use uuid::Uuid;

pub fn generate_nonce() -> String {
    Uuid::new_v4().to_string()
}
