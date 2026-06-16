use serde::Deserialize;

use crate::error::{Result, NodError};

#[derive(Deserialize, Debug, Clone)]
pub struct ClaudeCodeHookInput {
    pub tool_name: String,
    pub tool_input: serde_json::Value,
    pub session_id: Option<String>,
    pub transcript_path: Option<String>,
}

pub fn parse_hook_input(stdin: &str) -> Result<ClaudeCodeHookInput> {
    serde_json::from_str(stdin.trim())
        .map_err(|e| NodError::Json(e))
}

pub fn build_description(input: &ClaudeCodeHookInput) -> String {
    match input.tool_name.as_str() {
        "Bash" => {
            let cmd = input.tool_input
                .get("command")
                .and_then(|v| v.as_str())
                .unwrap_or("<unknown command>");
            format!("Bash: {cmd}")
        }
        "Write" => {
            let path = input.tool_input
                .get("file_path")
                .and_then(|v| v.as_str())
                .unwrap_or("<unknown path>");
            let content = input.tool_input
                .get("content")
                .and_then(|v| v.as_str())
                .unwrap_or("");
            let bytes = content.len();
            format!("Write: {path} ({bytes} bytes)")
        }
        "Edit" => {
            let path = input.tool_input
                .get("file_path")
                .and_then(|v| v.as_str())
                .unwrap_or("<unknown path>");
            format!("Edit: {path}")
        }
        "Read" => {
            let path = input.tool_input
                .get("file_path")
                .and_then(|v| v.as_str())
                .unwrap_or("<unknown path>");
            format!("Read: {path}")
        }
        other => {
            format!("{other}: {}", input.tool_input)
        }
    }
}
