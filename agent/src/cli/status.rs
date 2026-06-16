use clap::Args;

use crate::config::load_config;
use crate::config::paths::{machine_token_path, socket_path};
use crate::daemon::client::is_daemon_running;
use crate::daemon::lifecycle::read_pid;
use crate::error::{Result, NodError};

#[derive(Args, Debug)]
pub struct StatusArgs {}

pub async fn run(_args: StatusArgs) -> Result<()> {
    let config = match load_config() {
        Ok(c) => c,
        Err(NodError::NotInitialized) => {
            println!("Nod Status");
            println!("  Not initialized. Run: tap-guard init --api-key <key>");
            return Ok(());
        }
        Err(e) => return Err(e),
    };

    let daemon_running = is_daemon_running();
    let pid = read_pid();

    let daemon_status = if daemon_running {
        match pid {
            Some(p) => format!("● Running (PID {p})"),
            None => "● Running".to_string(),
        }
    } else {
        "○ Not running".to_string()
    };

    // Check if socket exists to infer connectivity
    let sock = socket_path();
    let conn_status = if sock.exists() && daemon_running {
        format!("● Connected to {}", config.agent.server_url)
    } else {
        "○ Disconnected".to_string()
    };

    // Read token file and check expiry if it contains expiry metadata
    let token_status = check_token_status();

    println!("Nod Status");
    println!("  Machine:    {} ({})", config.agent.machine_name, config.agent.machine_id);
    println!("  Workspace:  {}", config.agent.workspace_id);
    println!("  Daemon:     {daemon_status}");
    println!("  Connection: {conn_status}");
    println!("  Token:      {token_status}");

    Ok(())
}

fn check_token_status() -> String {
    let path = machine_token_path();
    if !path.exists() {
        return "Not found".to_string();
    }
    // Token is an opaque string; we can't know expiry without querying the server
    "Present (use `tap-guard rotate` to refresh)".to_string()
}
