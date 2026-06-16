use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use futures_util::{SinkExt, StreamExt};
use tokio::sync::mpsc;
use tokio::time::timeout;
use tokio_tungstenite::{
    connect_async,
    tungstenite::Message,
    MaybeTlsStream, WebSocketStream,
};
use tracing::{error, info, warn};

use crate::error::{Result, NodError};
use crate::ws::protocol::{AgentMessage, ServerMessage, SessionMeta};
use crate::ws::reconnect::ReconnectBackoff;

#[derive(Debug, Clone)]
pub struct OutgoingRequest {
    pub message: AgentMessage,
    pub request_id: String,
}

#[derive(Debug, Clone)]
pub struct Decision {
    pub request_id: String,
    pub approved: bool,
}

pub struct WsClient {
    pub url: String,
    pub machine_token: String,
    pub fingerprint: String,
    pub session_meta: SessionMeta,
    pub session_id: Arc<Mutex<Option<String>>>,
}

type WsSink = futures_util::stream::SplitSink<
    WebSocketStream<MaybeTlsStream<tokio::net::TcpStream>>,
    Message,
>;
type WsStream =
    futures_util::stream::SplitStream<WebSocketStream<MaybeTlsStream<tokio::net::TcpStream>>>;

impl WsClient {
    pub fn new(
        url: String,
        machine_token: String,
        fingerprint: String,
        session_meta: SessionMeta,
    ) -> Self {
        WsClient {
            url,
            machine_token,
            fingerprint,
            session_meta,
            session_id: Arc::new(Mutex::new(None)),
        }
    }

    pub async fn connect_and_run(
        &self,
        mut request_rx: mpsc::Receiver<OutgoingRequest>,
        decision_tx: mpsc::Sender<Decision>,
    ) -> Result<()> {
        let mut backoff = ReconnectBackoff::new();
        let inflight: Arc<Mutex<HashMap<String, ()>>> = Arc::new(Mutex::new(HashMap::new()));

        'reconnect: loop {
            let delay = backoff.next_delay();
            if delay.as_secs() > 0 {
                info!("ws: reconnecting in {}s…", delay.as_secs());
                tokio::time::sleep(delay).await;
            }

            let url = self.url.as_str();

            let ws_stream = match connect_async(url).await {
                Ok((ws, _)) => ws,
                Err(e) => {
                    warn!("ws: connect failed: {e}");
                    continue 'reconnect;
                }
            };

            info!("ws: connected");
            let (mut sink, mut stream) = ws_stream.split();

            // --- Auth ---
            let current_session = {
                let lock = self.session_id.lock().unwrap();
                lock.clone()
            };
            let auth_msg = match current_session {
                Some(sid) => {
                    let ids = {
                        let lock = inflight.lock().unwrap();
                        lock.keys().cloned().collect()
                    };
                    AgentMessage::Reconnect {
                        session_id: sid,
                        inflight_requests: ids,
                    }
                }
                None => AgentMessage::Auth {
                    machine_token: self.machine_token.clone(),
                    fingerprint: self.fingerprint.clone(),
                    session_meta: self.session_meta.clone(),
                },
            };

            let auth_text = match serde_json::to_string(&auth_msg) {
                Ok(t) => t,
                Err(e) => {
                    warn!("ws: failed to serialize auth: {e}");
                    continue 'reconnect;
                }
            };
            if let Err(e) = sink.send(Message::Text(auth_text.into())).await {
                warn!("ws: failed to send auth: {e}");
                continue 'reconnect;
            }

            // --- Wait for AuthOk ---
            let auth_deadline = tokio::time::Instant::now() + Duration::from_secs(10);
            let authed = 'auth: loop {
                let remaining = auth_deadline.saturating_duration_since(tokio::time::Instant::now());
                if remaining.is_zero() {
                    warn!("ws: auth timeout");
                    continue 'reconnect;
                }
                match timeout(remaining, stream.next()).await {
                    Ok(Some(Ok(Message::Text(txt)))) => {
                        match serde_json::from_str::<ServerMessage>(&txt) {
                            Ok(ServerMessage::AuthOk { session_id, .. }) => {
                                info!("ws: auth ok, session={session_id}");
                                let mut lock = self.session_id.lock().unwrap();
                                *lock = Some(session_id);
                                backoff.reset();
                                break 'auth true;
                            }
                            Ok(ServerMessage::Error { code, message }) => {
                                error!("ws: auth error {code}: {message}");
                                // session_expired means server lost our session — clear it so
                                // next iteration sends Auth (not Reconnect).
                                if code == "session_expired" {
                                    let mut lock = self.session_id.lock().unwrap();
                                    *lock = None;
                                }
                                break 'auth false;
                            }
                            Ok(other) => {
                                warn!("ws: unexpected auth response: {other:?}");
                                break 'auth false;
                            }
                            Err(e) => {
                                warn!("ws: failed to parse auth response: {e}");
                                break 'auth false;
                            }
                        }
                    }
                    Ok(Some(Ok(Message::Ping(data)))) => {
                        let _ = sink.send(Message::Pong(data)).await;
                        continue 'auth;
                    }
                    Ok(Some(Ok(Message::Binary(_)))) => {
                        continue 'auth;
                    }
                    Ok(Some(Err(e))) => {
                        warn!("ws: error during auth: {e}");
                        break 'auth false;
                    }
                    Ok(None) => {
                        warn!("ws: stream ended during auth");
                        break 'auth false;
                    }
                    Err(_) => {
                        warn!("ws: auth timeout");
                        break 'auth false;
                    }
                    Ok(Some(Ok(_))) => {
                        continue 'auth;
                    }
                }
            };
            if !authed {
                continue 'reconnect;
            }

            // --- Heartbeat channels ---
            let (ping_tx, mut ping_rx) = mpsc::channel::<Message>(8);
            let (pong_tx, pong_rx) = mpsc::channel::<()>(8);

            let hb_handle =
                tokio::spawn(crate::ws::heartbeat::run_heartbeat(ping_tx, pong_rx));

            let decision_tx_clone = decision_tx.clone();
            let inflight_clone = inflight.clone();

            let disconnect_reason = run_connection_loop(
                &mut sink,
                &mut stream,
                &mut request_rx,
                &mut ping_rx,
                &pong_tx,
                &decision_tx_clone,
                &inflight_clone,
            )
            .await;

            hb_handle.abort();

            match disconnect_reason {
                Err(ref reason) if reason == "revoked" => {
                    return Err(NodError::MachineRevoked);
                }
                Err(e) => {
                    warn!("ws: connection lost: {e}");
                }
                Ok(()) => {}
            }
        }
    }
}

async fn run_connection_loop(
    sink: &mut WsSink,
    stream: &mut WsStream,
    request_rx: &mut mpsc::Receiver<OutgoingRequest>,
    ping_rx: &mut mpsc::Receiver<Message>,
    pong_tx: &mpsc::Sender<()>,
    decision_tx: &mpsc::Sender<Decision>,
    inflight: &Arc<Mutex<HashMap<String, ()>>>,
) -> std::result::Result<(), String> {
    loop {
        tokio::select! {
            msg = stream.next() => {
                match msg {
                    None => return Err("stream closed".to_string()),
                    Some(Err(e)) => return Err(format!("ws error: {e}")),
                    Some(Ok(Message::Text(txt))) => {
                        match serde_json::from_str::<ServerMessage>(&txt) {
                            Ok(ServerMessage::Decision { request_id, approved }) => {
                                inflight.lock().unwrap().remove(&request_id);
                                let _ = decision_tx.send(Decision { request_id, approved }).await;
                            }
                            Ok(ServerMessage::AlreadyDecided { request_id, status }) => {
                                inflight.lock().unwrap().remove(&request_id);
                                let approved = status == "approved";
                                let _ = decision_tx.send(Decision { request_id, approved }).await;
                            }
                            Ok(ServerMessage::Expired { request_id }) => {
                                inflight.lock().unwrap().remove(&request_id);
                                let _ = decision_tx
                                    .send(Decision { request_id, approved: false })
                                    .await;
                            }
                            Ok(ServerMessage::Revoked { reason }) => {
                                error!("ws: machine revoked: {reason}");
                                return Err("revoked".to_string());
                            }
                            Ok(ServerMessage::Pong { .. }) => {
                                let _ = pong_tx.send(()).await;
                            }
                            Ok(ServerMessage::Error { code, message }) => {
                                warn!("ws: server error {code}: {message}");
                            }
                            Ok(other) => {
                                warn!("ws: unexpected message: {other:?}");
                            }
                            Err(e) => {
                                warn!("ws: failed to parse message: {e}");
                            }
                        }
                    }
                    Some(Ok(Message::Ping(data))) => {
                        if let Err(e) = sink.send(Message::Pong(data)).await {
                            return Err(format!("failed to send pong: {e}"));
                        }
                    }
                    Some(Ok(Message::Close(_))) => {
                        return Err("server closed connection".to_string());
                    }
                    Some(Ok(_)) => {}
                }
            }

            req = request_rx.recv() => {
                match req {
                    None => return Err("request channel closed".to_string()),
                    Some(r) => {
                        inflight.lock().unwrap().insert(r.request_id.clone(), ());
                        match serde_json::to_string(&r.message) {
                            Ok(text) => {
                                if let Err(e) = sink.send(Message::Text(text.into())).await {
                                    return Err(format!("send error: {e}"));
                                }
                            }
                            Err(e) => {
                                warn!("ws: failed to serialize request: {e}");
                            }
                        }
                    }
                }
            }

            ping = ping_rx.recv() => {
                match ping {
                    None => return Err("heartbeat ping channel closed".to_string()),
                    Some(msg) => {
                        if let Err(e) = sink.send(msg).await {
                            return Err(format!("ping send error: {e}"));
                        }
                    }
                }
            }
        }
    }
}
