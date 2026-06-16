use clap::Args;

use crate::adapters::generic::build_generic_request;
use crate::daemon::client::{is_daemon_running, DaemonClient};
use crate::error::{Result, NodError};

#[derive(Args, Debug)]
pub struct AskArgs {
    pub description: String,

    #[arg(long, default_value = "30")]
    pub timeout: u64,

    #[arg(long, default_value = "generic")]
    pub tool: String,
}

pub async fn run(args: AskArgs) -> Result<i32> {
    if !is_daemon_running() {
        return Err(NodError::DaemonNotRunning);
    }

    let timeout_ms = args.timeout * 1000;
    let req = build_generic_request(&args.description, timeout_ms);

    eprintln!("Waiting for approval on your phone…");

    let client = DaemonClient::new();
    let resp = client.request_approval(req).await?;

    if resp.approved {
        eprintln!("✓ Approved");
        Ok(0)
    } else {
        eprintln!("✗ Denied ({})", resp.status);
        Ok(1)
    }
}
