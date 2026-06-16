use std::collections::HashMap;
use std::sync::Arc;

use clap::Args;
use tokio::sync::{mpsc, Mutex};
use tracing::info;

use crate::config::load_config;
use crate::config::paths::socket_path;
use crate::crypto::fingerprint::generate_fingerprint;
use crate::crypto::keypair::load_signing_key;
use crate::daemon::lifecycle::{is_process_alive, read_pid, remove_pid_file, write_pid_file};
use crate::daemon::server::{run_decision_router, DaemonServer};
use crate::error::{Result, NodError};
use crate::session::id::generate_session_id;
use crate::session::state::SessionState;
use crate::ws::client::{Decision, OutgoingRequest, WsClient};
use crate::ws::protocol::SessionMeta;

#[derive(Args, Debug)]
pub struct DaemonArgs {
    #[arg(long)]
    pub foreground: bool,
}

pub async fn run(args: DaemonArgs) -> Result<()> {
    // Check if already running
    if let Some(pid) = read_pid() {
        if is_process_alive(pid) {
            eprintln!("Nod daemon is already running (PID {pid})");
            return Ok(());
        }
    }

    if !args.foreground {
        daemonize()?;
        return Ok(());
    }

    run_foreground().await
}

fn daemonize() -> Result<()> {
    #[cfg(unix)]
    {
        use std::process::Command;
        let exe = std::env::current_exe()?;
        let child = Command::new(exe)
            .arg("daemon")
            .arg("--foreground")
            .stdin(std::process::Stdio::null())
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .spawn()?;
        println!("Nod daemon started (PID {})", child.id());
        Ok(())
    }

    #[cfg(not(unix))]
    {
        // On non-Unix, just run in foreground
        Err(NodError::Config(
            "daemonization not supported on this platform, use --foreground".to_string(),
        ))
    }
}

async fn run_foreground() -> Result<()> {
    write_pid_file()?;

    // Setup cleanup on exit
    let _cleanup = PidCleanup;

    let config = load_config()?;

    let signing_key = load_signing_key(
        std::path::Path::new(&config.agent.machine_key_path),
    )?;

    let machine_token = std::fs::read_to_string(&config.agent.machine_token_path)
        .map_err(|_| NodError::NotInitialized)?;
    let machine_token = machine_token.trim().to_string();

    let fingerprint = generate_fingerprint()?;

    let session_meta = SessionMeta::current(&config.agent.agent_type);
    let session_id = generate_session_id(&config.agent.machine_id);
    let session_state = Arc::new(SessionState::new(session_id));

    let (request_tx, request_rx) = mpsc::channel::<OutgoingRequest>(256);
    let (decision_tx, decision_rx) = mpsc::channel::<Decision>(256);

    let decision_map: Arc<Mutex<HashMap<String, tokio::sync::oneshot::Sender<bool>>>> =
        Arc::new(Mutex::new(HashMap::new()));

    let ws_client = WsClient::new(
        config.agent.server_url.clone(),
        machine_token,
        fingerprint,
        session_meta,
    );

    let socket = socket_path();

    let daemon_server = DaemonServer {
        socket_path: socket.clone(),
        ws_client_tx: request_tx,
        decision_map: decision_map.clone(),
        signing_key,
        machine_id: config.agent.machine_id.clone(),
        session_state: session_state.clone(),
    };

    // Signal handling
    #[cfg(unix)]
    let mut sigterm = tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
        .map_err(|e| NodError::Io(e))?;
    #[cfg(unix)]
    let mut sigint = tokio::signal::unix::signal(tokio::signal::unix::SignalKind::interrupt())
        .map_err(|e| NodError::Io(e))?;

    info!("Nod daemon starting (PID {})", std::process::id());

    let ws_handle = tokio::spawn(async move {
        if let Err(e) = ws_client.connect_and_run(request_rx, decision_tx).await {
            tracing::error!("ws client exited: {e}");
        }
    });

    let router_handle = tokio::spawn(run_decision_router(decision_rx, decision_map));

    let server_handle = tokio::spawn(async move {
        if let Err(e) = daemon_server.run().await {
            tracing::error!("daemon server exited: {e}");
        }
    });

    #[cfg(unix)]
    {
        tokio::select! {
            _ = sigterm.recv() => {
                info!("received SIGTERM, shutting down");
            }
            _ = sigint.recv() => {
                info!("received SIGINT, shutting down");
            }
            _ = ws_handle => {}
            _ = router_handle => {}
            _ = server_handle => {}
        }
    }

    #[cfg(not(unix))]
    {
        tokio::select! {
            _ = tokio::signal::ctrl_c() => {
                info!("received Ctrl+C, shutting down");
            }
            _ = ws_handle => {}
            _ = router_handle => {}
            _ = server_handle => {}
        }
    }

    // Cleanup socket
    if socket.exists() {
        let _ = std::fs::remove_file(&socket);
    }

    Ok(())
}

struct PidCleanup;

impl Drop for PidCleanup {
    fn drop(&mut self) {
        remove_pid_file();
    }
}
