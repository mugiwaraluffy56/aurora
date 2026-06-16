use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Config {
    pub agent: AgentConfig,
    pub adapters: AdaptersConfig,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AgentConfig {
    pub agent_type: String,
    pub workspace_id: String,
    pub machine_token_path: String,
    pub machine_key_path: String,
    pub server_url: String,
    pub server_http_url: String,
    pub machine_id: String,
    pub machine_name: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AdaptersConfig {
    pub claude_code: ClaudeCodeAdapter,
    pub codex: PTYAdapter,
    pub aider: PTYAdapter,
    pub gemini: PTYAdapter,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ClaudeCodeAdapter {
    pub default_timeout_ms: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PTYAdapter {
    pub prompt_patterns: Vec<String>,
    pub default_timeout_ms: u64,
}

impl Default for Config {
    fn default() -> Self {
        Config {
            agent: AgentConfig {
                agent_type: "claude-code".to_string(),
                workspace_id: String::new(),
                machine_token_path: crate::config::paths::machine_token_path()
                    .to_string_lossy()
                    .to_string(),
                machine_key_path: crate::config::paths::machine_key_path()
                    .to_string_lossy()
                    .to_string(),
                server_url: "wss://api.nod.dev/ws".to_string(),
                server_http_url: "https://api.nod.dev".to_string(),
                machine_id: String::new(),
                machine_name: String::new(),
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
        }
    }
}
