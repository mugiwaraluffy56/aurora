use clap::Args;

use crate::config::load_config;
use crate::config::paths::machine_token_path;
use crate::error::{Result, NodError};

#[derive(Args, Debug)]
pub struct RotateArgs {}

pub async fn run(_args: RotateArgs) -> Result<()> {
    let config = load_config()?;

    let token_path = machine_token_path();
    let current_token = std::fs::read_to_string(&token_path)
        .map_err(|_| NodError::NotInitialized)?;
    let current_token = current_token.trim();

    let http_url = config.agent.server_http_url.trim_end_matches('/');
    let machine_id = &config.agent.machine_id;
    let url = format!("{http_url}/machines/{machine_id}/rotate-token");

    println!("Rotating machine token…");

    let client = reqwest::Client::new();
    let resp = client
        .post(&url)
        .header("Authorization", format!("Bearer {current_token}"))
        .send()
        .await
        .map_err(|e| NodError::Http(e.to_string()))?;

    if !resp.status().is_success() {
        let status = resp.status();
        let text = resp.text().await.unwrap_or_default();
        return Err(NodError::Http(format!("server returned {status}: {text}")));
    }

    let resp_json: serde_json::Value = resp
        .json()
        .await
        .map_err(|e| NodError::Http(e.to_string()))?;

    let new_token = resp_json["machineToken"]
        .as_str()
        .ok_or_else(|| NodError::Http("server response missing machineToken".to_string()))?;

    std::fs::write(&token_path, new_token)?;

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        std::fs::set_permissions(&token_path, std::fs::Permissions::from_mode(0o600))?;
    }

    println!("✓ Machine token rotated successfully.");
    println!("  Restart the daemon to use the new token: tap-guard daemon");

    Ok(())
}
