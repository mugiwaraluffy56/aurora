use crate::daemon::server::ApprovalRequest;

pub fn build_generic_request(description: &str, timeout_ms: u64) -> ApprovalRequest {
    ApprovalRequest {
        tool: "generic".to_string(),
        input: serde_json::json!({ "description": description }),
        description: Some(description.to_string()),
        timeout_ms,
    }
}
