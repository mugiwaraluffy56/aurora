use std::io::{self, Read};

use clap::Args;

use crate::adapters::claude_code::{build_description, parse_hook_input};
use crate::config::load_config;
use crate::daemon::client::{is_daemon_running, DaemonClient};
use crate::daemon::server::ApprovalRequest;
use crate::error::{Result, NodError};

#[derive(Args, Debug)]
pub struct HookArgs {}

pub async fn run(_args: HookArgs) -> Result<i32> {
    // Read stdin — Claude Code sends hook input as JSON
    let mut stdin_buf = String::new();
    io::stdin()
        .read_to_string(&mut stdin_buf)
        .map_err(|e| NodError::Io(e))?;

    if stdin_buf.trim().is_empty() {
        // Nothing to approve — allow by default
        return Ok(0);
    }

    let hook_input = parse_hook_input(&stdin_buf)?;

    if !is_daemon_running() {
        // Fail open: if daemon isn't running, block with an error message
        let block_msg = serde_json::json!({
            "decision": "block",
            "reason": "Nod daemon is not running. Start it with: tap-guard daemon"
        });
        println!("{block_msg}");
        return Ok(2);
    }

    let config = load_config()?;
    let timeout_ms = config.adapters.claude_code.default_timeout_ms;

    let description = build_description(&hook_input);

    let req = ApprovalRequest {
        tool: hook_input.tool_name.clone(),
        input: hook_input.tool_input.clone(),
        description: Some(description),
        timeout_ms,
    };

    let client = DaemonClient::new();
    let resp = client.request_approval(req).await?;

    if resp.approved {
        Ok(0)
    } else {
        let reason = match resp.status.as_str() {
            "timeout" => "Timed out waiting for approval via Nod",
            "expired" => "Request expired without a decision via Nod",
            _ => "Denied via Nod",
        };
        let block_msg = serde_json::json!({
            "decision": "block",
            "reason": reason
        });
        println!("{block_msg}");
        Ok(2)
    }
}
