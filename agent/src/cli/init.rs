use clap::Args;
use tracing::info;

use crate::config::paths::{config_dir, machine_key_path, machine_pubkey_path, machine_token_path};
use crate::config::types::{AdaptersConfig, AgentConfig, ClaudeCodeAdapter, Config, PTYAdapter};
use crate::config::{is_initialized, save_config};
use crate::crypto::{
    fingerprint::generate_fingerprint,
    keypair::{generate_keypair, load_public_key_base64, save_keypair},
};
use crate::error::{Result, NodError};

#[derive(Args, Debug)]
pub struct InitArgs {
    #[arg(long, env = "TAP_GUARD_API_KEY")]
    pub api_key: String,

    #[arg(long, default_value = "")]
    pub name: String,

    #[arg(long, default_value = "https://api.nod.dev")]
    pub server: String,

    #[arg(long, default_value = "")]
    pub workspace_id: String,
}

pub async fn run(args: InitArgs) -> Result<()> {
    if is_initialized() {
        eprintln!("Nod is already initialized. Use `tap-guard rotate` to rotate your token.");
        return Ok(());
    }

    let dir = config_dir();
    std::fs::create_dir_all(&dir)?;

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        std::fs::set_permissions(&dir, std::fs::Permissions::from_mode(0o700))?;
    }

    println!("Generating Ed25519 keypair…");
    let (signing_key, _verifying_key) = generate_keypair();
    let key_path = machine_key_path();
    let pub_path = machine_pubkey_path();
    save_keypair(&signing_key, &key_path, &pub_path)?;

    println!("Generating machine fingerprint…");
    let fingerprint = generate_fingerprint()?;

    let public_key_b64 = load_public_key_base64(&pub_path)?;

    let machine_name = if args.name.is_empty() {
        hostname()
    } else {
        args.name.clone()
    };

    let machine_type = detect_machine_type();

    println!("Registering machine with Nod server…");
    let http_url = args.server.trim_end_matches('/');
    let register_url = format!("{http_url}/machines");

    let body = serde_json::json!({
        "apiKey": args.api_key,
        "name": machine_name,
        "type": machine_type,
        "fingerprint": fingerprint,
        "publicKey": public_key_b64,
        "workspaceId": if args.workspace_id.is_empty() { None } else { Some(&args.workspace_id) },
    });

    let client = reqwest::Client::new();
    let resp = client
        .post(&register_url)
        .header("X-API-Key", &args.api_key)
        .json(&body)
        .send()
        .await
        .map_err(|e| NodError::Http(e.to_string()))?;

    if !resp.status().is_success() {
        let status = resp.status();
        let text = resp.text().await.unwrap_or_default();
        return Err(NodError::Http(format!(
            "server returned {status}: {text}"
        )));
    }

    let resp_json: serde_json::Value = resp
        .json()
        .await
        .map_err(|e| NodError::Http(e.to_string()))?;

    // Server returns { id, token } (shown once)
    let machine_token = resp_json["token"].as_str().ok_or_else(|| {
        NodError::Http(format!("server response missing token: {resp_json}"))
    })?;

    let machine_id = resp_json["id"]
        .as_str()
        .ok_or_else(|| NodError::Http(format!("server response missing id: {resp_json}")))?;

    let workspace_id = args.workspace_id.clone();

    // Save machine token
    let token_path = machine_token_path();
    std::fs::write(&token_path, machine_token)?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        std::fs::set_permissions(&token_path, std::fs::Permissions::from_mode(0o600))?;
    }

    let ws_url = args
        .server
        .replace("https://", "wss://")
        .replace("http://", "ws://");
    let ws_url = format!("{ws_url}/ws");

    let config = Config {
        agent: AgentConfig {
            agent_type: "claude-code".to_string(),
            workspace_id,
            machine_token_path: token_path.to_string_lossy().to_string(),
            machine_key_path: key_path.to_string_lossy().to_string(),
            server_url: ws_url,
            server_http_url: args.server.clone(),
            machine_id: machine_id.to_string(),
            machine_name: machine_name.clone(),
        },
        adapters: AdaptersConfig {
            claude_code: ClaudeCodeAdapter {
                default_timeout_ms: 30000,
            },
            codex: PTYAdapter {
                prompt_patterns: vec![
                    "Allow this action?".to_string(),
                    "Proceed with".to_string(),
                    "Do you want to".to_string(),
                    "Continue?".to_string(),
                ],
                default_timeout_ms: 30000,
            },
            aider: PTYAdapter {
                prompt_patterns: vec![
                    "Allow bash".to_string(),
                    "Run this command?".to_string(),
                    "Apply these changes?".to_string(),
                ],
                default_timeout_ms: 30000,
            },
            gemini: PTYAdapter {
                prompt_patterns: vec![
                    "Allow this".to_string(),
                    "Proceed?".to_string(),
                    "Execute?".to_string(),
                ],
                default_timeout_ms: 30000,
            },
        },
    };

    save_config(&config)?;

    println!();
    println!("✓ Nod initialized successfully!");
    println!();
    println!("  Machine:   {machine_name} ({machine_id})");
    println!("  Server:    {}", args.server);
    println!();
    println!("Next steps:");
    println!("  1. Start the daemon:    tap-guard daemon");
    println!("  2. Add hook to Claude:  tap-guard hook  (configure in .claude/settings.json)");
    println!();
    println!("Example Claude Code settings.json:");
    println!(
        r#"  {{"hooks": {{"PreToolUse": [{{"matcher": ".*", "hooks": [{{"type": "command", "command": "tap-guard hook"}}]}}]}}}}"#
    );

    Ok(())
}

fn hostname() -> String {
    std::process::Command::new("hostname")
        .output()
        .ok()
        .and_then(|o| String::from_utf8(o.stdout).ok())
        .map(|s| s.trim().to_string())
        .unwrap_or_else(|| "unknown".to_string())
}

fn detect_machine_type() -> &'static str {
    #[cfg(target_os = "linux")]
    if std::path::Path::new("/.dockerenv").exists() {
        return "DOCKER";
    }

    if std::env::var("TMUX").is_ok() {
        return "TMUX";
    }

    // Check for AWS
    let client = reqwest::blocking::Client::builder()
        .timeout(std::time::Duration::from_secs(1))
        .build();
    if let Ok(c) = client {
        if c.get("http://169.254.169.254/latest/meta-data/instance-id")
            .send()
            .map(|r| r.status().is_success())
            .unwrap_or(false)
        {
            return "AWS_EC2";
        }
    }

    "LOCAL"
}
