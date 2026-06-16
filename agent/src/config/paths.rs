use std::path::PathBuf;

pub fn config_dir() -> PathBuf {
    dirs::home_dir()
        .unwrap_or_else(|| PathBuf::from("/tmp"))
        .join(".tap-guard")
}

pub fn config_path() -> PathBuf {
    config_dir().join("config.toml")
}

pub fn machine_token_path() -> PathBuf {
    config_dir().join("machine.token")
}

pub fn machine_key_path() -> PathBuf {
    config_dir().join("machine.key")
}

pub fn machine_pubkey_path() -> PathBuf {
    config_dir().join("machine.pub")
}

pub fn pid_file_path() -> PathBuf {
    config_dir().join("daemon.pid")
}

pub fn socket_path() -> PathBuf {
    config_dir().join("daemon.sock")
}
