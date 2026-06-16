use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;

use serde::{Deserialize, Serialize};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::UnixListener;
use tokio::sync::{mpsc, oneshot, Mutex};
use tokio::time::timeout;
use tracing::{info, warn};

use crate::crypto::{nonce::generate_nonce, signing};
use crate::error::{Result, NodError};
use crate::session::state::SessionState;
use crate::ws::client::{Decision, OutgoingRequest};
use crate::ws::protocol::AgentMessage;

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct ApprovalRequest {
    pub tool: String,
    pub input: serde_json::Value,
    pub description: Option<String>,
    pub timeout_ms: u64,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct ApprovalResponse {
    pub approved: bool,
    pub status: String,
}

pub struct DaemonServer {
    pub socket_path: PathBuf,
    pub ws_client_tx: mpsc::Sender<OutgoingRequest>,
    pub decision_map: Arc<Mutex<HashMap<String, oneshot::Sender<bool>>>>,
    pub signing_key: ed25519_dalek::SigningKey,
    pub machine_id: String,
    pub session_state: Arc<SessionState>,
}

impl DaemonServer {
    pub async fn run(self) -> Result<()> {
        // Remove stale socket if it exists
        if self.socket_path.exists() {
            std::fs::remove_file(&self.socket_path)?;
        }

        let listener = UnixListener::bind(&self.socket_path)?;

        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            std::fs::set_permissions(
                &self.socket_path,
                std::fs::Permissions::from_mode(0o600),
            )?;
        }

        info!("daemon: listening on {:?}", self.socket_path);

        let ws_tx = self.ws_client_tx.clone();
        let decision_map = self.decision_map.clone();
        let signing_key = Arc::new(self.signing_key);
        let machine_id = Arc::new(self.machine_id.clone());
        let session_state = self.session_state.clone();

        loop {
            let (stream, _) = listener.accept().await?;

            let ws_tx = ws_tx.clone();
            let decision_map = decision_map.clone();
            let signing_key = signing_key.clone();
            let machine_id = machine_id.clone();
            let session_state = session_state.clone();

            tokio::spawn(async move {
                if let Err(e) = handle_connection(
                    stream,
                    ws_tx,
                    decision_map,
                    signing_key,
                    machine_id,
                    session_state,
                )
                .await
                {
                    warn!("daemon: connection error: {e}");
                }
            });
        }
    }
}

async fn handle_connection(
    stream: tokio::net::UnixStream,
    ws_tx: mpsc::Sender<OutgoingRequest>,
    decision_map: Arc<Mutex<HashMap<String, oneshot::Sender<bool>>>>,
    signing_key: Arc<ed25519_dalek::SigningKey>,
    machine_id: Arc<String>,
    session_state: Arc<SessionState>,
) -> Result<()> {
    let (read_half, mut write_half) = stream.into_split();
    let mut reader = BufReader::new(read_half);
    let mut line = String::new();
    reader.read_line(&mut line).await?;

    let req: ApprovalRequest = serde_json::from_str(line.trim())
        .map_err(|e| NodError::Json(e))?;

    let request_id = uuid::Uuid::new_v4().to_string();
    let nonce = generate_nonce();
    let ts = chrono::Utc::now().timestamp_millis();
    let input_hash = signing::hash_input(&req.input);
    let seq = session_state.next_seq();

    let signature = signing::sign_request(
        &signing_key,
        &request_id,
        &machine_id,
        &req.tool,
        &input_hash,
        ts,
        &nonce,
    );

    let msg = AgentMessage::Request {
        request_id: request_id.clone(),
        tool: req.tool.clone(),
        input: req.input.clone(),
        input_hash,
        description: req.description.clone(),
        timeout_ms: req.timeout_ms,
        signature,
        seq,
        nonce,
        ts,
    };

    let (tx, rx) = oneshot::channel::<bool>();
    {
        let mut map = decision_map.lock().await;
        map.insert(request_id.clone(), tx);
    }

    session_state.add_inflight(&request_id);

    ws_tx
        .send(OutgoingRequest {
            message: msg,
            request_id: request_id.clone(),
        })
        .await
        .map_err(|_| NodError::WebSocket("ws channel closed".to_string()))?;

    let timeout_dur = Duration::from_millis(req.timeout_ms);
    let result = timeout(timeout_dur, rx).await;

    session_state.remove_inflight(&request_id);
    {
        let mut map = decision_map.lock().await;
        map.remove(&request_id);
    }

    let response = match result {
        Ok(Ok(true)) => ApprovalResponse {
            approved: true,
            status: "approved".to_string(),
        },
        Ok(Ok(false)) => ApprovalResponse {
            approved: false,
            status: "denied".to_string(),
        },
        Ok(Err(_)) => ApprovalResponse {
            approved: false,
            status: "expired".to_string(),
        },
        Err(_) => ApprovalResponse {
            approved: false,
            status: "timeout".to_string(),
        },
    };

    let resp_json = serde_json::to_string(&response)?;
    write_half.write_all(resp_json.as_bytes()).await?;
    write_half.write_all(b"\n").await?;

    Ok(())
}

/// Route decisions from WebSocket to waiting Unix socket handlers.
pub async fn run_decision_router(
    mut decision_rx: mpsc::Receiver<Decision>,
    decision_map: Arc<Mutex<HashMap<String, oneshot::Sender<bool>>>>,
) {
    while let Some(d) = decision_rx.recv().await {
        let mut map = decision_map.lock().await;
        if let Some(tx) = map.remove(&d.request_id) {
            let _ = tx.send(d.approved);
        }
    }
}
