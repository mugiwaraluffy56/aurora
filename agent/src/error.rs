use thiserror::Error;

#[derive(Error, Debug)]
pub enum NodError {
    #[error("daemon not running — start with: tap-guard daemon")]
    DaemonNotRunning,
    #[error("not initialized — run: tap-guard init --api-key <key>")]
    NotInitialized,
    #[error("machine revoked")]
    MachineRevoked,
    #[error("request timed out after {0}s")]
    Timeout(u64),
    #[error("request denied")]
    Denied,
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
    #[error("WebSocket error: {0}")]
    WebSocket(String),
    #[error("HTTP error: {0}")]
    Http(String),
    #[error("JSON error: {0}")]
    Json(#[from] serde_json::Error),
    #[error("config error: {0}")]
    Config(String),
    #[error("crypto error: {0}")]
    Crypto(String),
}

pub type Result<T> = std::result::Result<T, NodError>;
