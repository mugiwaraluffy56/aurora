use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(tag = "type", rename_all = "camelCase")]
pub enum AgentMessage {
    Auth {
        machine_token: String,
        fingerprint: String,
        session_meta: SessionMeta,
    },
    Reconnect {
        session_id: String,
        inflight_requests: Vec<String>,
    },
    Request {
        request_id: String,
        tool: String,
        input: serde_json::Value,
        input_hash: String,
        description: Option<String>,
        timeout_ms: u64,
        signature: String,
        seq: u32,
        nonce: String,
        ts: i64,
    },
    Cancel {
        request_id: String,
    },
    Ping {
        ts: i64,
    },
}

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(tag = "type", rename_all = "camelCase")]
pub enum ServerMessage {
    AuthOk {
        session_id: String,
        connection_id: String,
    },
    Decision {
        request_id: String,
        approved: bool,
    },
    AlreadyDecided {
        request_id: String,
        status: String,
    },
    Expired {
        request_id: String,
    },
    Revoked {
        reason: String,
    },
    Pong {
        ts: i64,
        server_ts: i64,
    },
    Error {
        code: String,
        message: String,
    },
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct SessionMeta {
    pub agent_type: String,
    pub pid: u32,
    pub tty: Option<String>,
    pub cwd: Option<String>,
    pub version: String,
}

impl SessionMeta {
    pub fn current(agent_type: &str) -> Self {
        let pid = std::process::id();
        let tty = std::env::var("SSH_TTY")
            .or_else(|_| std::env::var("TERM"))
            .ok();
        let cwd = std::env::current_dir()
            .ok()
            .map(|p| p.to_string_lossy().to_string());
        SessionMeta {
            agent_type: agent_type.to_string(),
            pid,
            tty,
            cwd,
            version: env!("CARGO_PKG_VERSION").to_string(),
        }
    }
}
