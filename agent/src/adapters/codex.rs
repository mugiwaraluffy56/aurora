pub const PROMPT_PATTERNS: &[&str] = &[
    "Allow this action?",
    "Proceed with",
    "Do you want to",
    "Continue?",
];

pub fn extract_command_from_context(context: &str) -> Option<String> {
    // Look for a line that appears to be a command (e.g., starts with $ or >)
    for line in context.lines().rev() {
        let trimmed = line.trim();
        if trimmed.starts_with("$ ") || trimmed.starts_with("> ") {
            return Some(trimmed[2..].to_string());
        }
        // Also detect shell-like lines
        if !trimmed.is_empty()
            && !trimmed.starts_with("Allow")
            && !trimmed.starts_with("Proceed")
            && !trimmed.starts_with("Do you")
            && !trimmed.starts_with("Continue")
        {
            return Some(trimmed.to_string());
        }
    }
    None
}
