use std::path::PathBuf;
use std::time::Duration;

use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::UnixStream;

use crate::config::paths::{pid_file_path, socket_path};
use crate::daemon::lifecycle::{is_process_alive, read_pid};
use crate::daemon::server::{ApprovalRequest, ApprovalResponse};
use crate::error::{Result, NodError};

pub struct DaemonClient {
    pub socket_path: PathBuf,
}

impl DaemonClient {
    pub fn new() -> Self {
        DaemonClient {
            socket_path: socket_path(),
        }
    }

    pub async fn request_approval(&self, req: ApprovalRequest) -> Result<ApprovalResponse> {
        if !self.socket_path.exists() {
            return Err(NodError::DaemonNotRunning);
        }

        let stream = tokio::time::timeout(
            Duration::from_secs(5),
            UnixStream::connect(&self.socket_path),
        )
        .await
        .map_err(|_| NodError::Timeout(5))?
        .map_err(|_| NodError::DaemonNotRunning)?;

        let (read_half, mut write_half) = stream.into_split();

        let req_json = serde_json::to_string(&req)?;
        write_half.write_all(req_json.as_bytes()).await?;
        write_half.write_all(b"\n").await?;

        let mut reader = BufReader::new(read_half);
        let mut line = String::new();

        let timeout_ms = req.timeout_ms + 5000; // extra headroom
        tokio::time::timeout(
            Duration::from_millis(timeout_ms),
            reader.read_line(&mut line),
        )
        .await
        .map_err(|_| NodError::Timeout(timeout_ms / 1000))??;

        let resp: ApprovalResponse = serde_json::from_str(line.trim())?;
        Ok(resp)
    }
}

impl Default for DaemonClient {
    fn default() -> Self {
        Self::new()
    }
}

pub fn is_daemon_running() -> bool {
    let pid_path = pid_file_path();
    if !pid_path.exists() {
        return false;
    }
    if let Some(pid) = read_pid() {
        is_process_alive(pid)
    } else {
        false
    }
}
