use std::time::Duration;

use tokio::sync::mpsc;
use tokio::time::{sleep, timeout};
use tokio_tungstenite::tungstenite::Message;
use tracing::{debug, warn};

/// Sends pings every 15s via `ping_tx`. The WS read loop must send () on `pong_rx`
/// whenever it receives a Pong. If no pong arrives within 5s the connection is
/// considered dead and the returned future resolves (caller should reconnect).
pub async fn run_heartbeat(
    mut ping_tx: mpsc::Sender<Message>,
    mut pong_rx: mpsc::Receiver<()>,
) {
    loop {
        sleep(Duration::from_secs(15)).await;

        let ts = chrono::Utc::now().timestamp_millis();
        let msg = crate::ws::protocol::AgentMessage::Ping { ts };
        let text = match serde_json::to_string(&msg) {
            Ok(t) => t,
            Err(e) => {
                warn!("heartbeat: failed to serialize ping: {e}");
                continue;
            }
        };

        if ping_tx.send(Message::Text(text.into())).await.is_err() {
            debug!("heartbeat: ping channel closed");
            return;
        }

        match timeout(Duration::from_secs(5), pong_rx.recv()).await {
            Ok(Some(())) => {
                debug!("heartbeat: pong received");
            }
            Ok(None) => {
                warn!("heartbeat: pong channel closed");
                return;
            }
            Err(_) => {
                warn!("heartbeat: pong timeout — triggering reconnect");
                return;
            }
        }
    }
}
