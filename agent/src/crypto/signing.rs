use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use ed25519_dalek::{Signer, SigningKey};
use sha2::{Digest, Sha256};

pub fn sign_request(
    signing_key: &SigningKey,
    request_id: &str,
    machine_id: &str,
    tool: &str,
    input_hash: &str,
    timestamp: i64,
    nonce: &str,
) -> String {
    let payload = format!(
        "{request_id}:{machine_id}:{tool}:{input_hash}:{timestamp}:{nonce}"
    );
    let signature = signing_key.sign(payload.as_bytes());
    BASE64.encode(signature.to_bytes())
}

pub fn hash_input(input: &serde_json::Value) -> String {
    let json_str = input.to_string();
    let mut hasher = Sha256::new();
    hasher.update(json_str.as_bytes());
    hex::encode(hasher.finalize())
}
