use std::fs;
use std::path::Path;

use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use ed25519_dalek::{SigningKey, VerifyingKey};
use rand::rngs::OsRng;

use crate::error::{Result, NodError};

pub fn generate_keypair() -> (SigningKey, VerifyingKey) {
    let signing_key = SigningKey::generate(&mut OsRng);
    let verifying_key = signing_key.verifying_key();
    (signing_key, verifying_key)
}

pub fn save_keypair(signing_key: &SigningKey, key_path: &Path, pub_path: &Path) -> Result<()> {
    if let Some(parent) = key_path.parent() {
        fs::create_dir_all(parent)?;
    }

    let key_bytes = signing_key.to_bytes();
    fs::write(key_path, hex::encode(key_bytes))?;

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(key_path, fs::Permissions::from_mode(0o600))?;
    }

    let pub_bytes = signing_key.verifying_key().to_bytes();
    let pub_b64 = BASE64.encode(pub_bytes);
    fs::write(pub_path, &pub_b64)?;

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(pub_path, fs::Permissions::from_mode(0o600))?;
    }

    Ok(())
}

pub fn load_signing_key(key_path: &Path) -> Result<SigningKey> {
    let hex_str = fs::read_to_string(key_path)
        .map_err(|_| NodError::NotInitialized)?;
    let bytes = hex::decode(hex_str.trim())
        .map_err(|e| NodError::Crypto(format!("invalid key hex: {e}")))?;
    let arr: [u8; 32] = bytes
        .try_into()
        .map_err(|_| NodError::Crypto("key must be 32 bytes".to_string()))?;
    Ok(SigningKey::from_bytes(&arr))
}

pub fn load_public_key_base64(pub_path: &Path) -> Result<String> {
    let b64 = fs::read_to_string(pub_path)
        .map_err(|_| NodError::NotInitialized)?;
    Ok(b64.trim().to_string())
}
