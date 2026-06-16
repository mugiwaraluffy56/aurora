pub fn matches_prompt(line: &str, patterns: &[String]) -> bool {
    let lower = line.to_lowercase();
    patterns.iter().any(|p| lower.contains(&p.to_lowercase()))
}

pub fn extract_context(buffer: &str, match_pos: usize, context_lines: usize) -> String {
    let before = &buffer[..match_pos];
    let lines: Vec<&str> = before.lines().collect();
    let start = lines.len().saturating_sub(context_lines);
    lines[start..].join("\n")
}
