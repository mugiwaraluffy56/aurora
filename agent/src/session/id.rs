pub fn generate_session_id(machine_id: &str) -> String {
    let pid = std::process::id();
    let ts = chrono::Utc::now().timestamp();
    format!("{machine_id}:{pid}:{ts}")
}
