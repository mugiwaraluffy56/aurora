use std::fs;

use crate::config::paths::{config_dir, config_path, machine_token_path};
use crate::config::types::Config;
use crate::error::{Result, NodError};

pub fn load_config() -> Result<Config> {
    let path = config_path();
    if !path.exists() {
        return Err(NodError::NotInitialized);
    }
    let contents = fs::read_to_string(&path)?;
    let config: Config = toml::from_str(&contents)
        .map_err(|e| NodError::Config(e.to_string()))?;
    Ok(config)
}

pub fn save_config(config: &Config) -> Result<()> {
    let dir = config_dir();
    fs::create_dir_all(&dir)?;

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let perms = fs::Permissions::from_mode(0o700);
        fs::set_permissions(&dir, perms)?;
    }

    let path = config_path();
    let contents = toml::to_string_pretty(config)
        .map_err(|e| NodError::Config(e.to_string()))?;
    fs::write(&path, contents)?;

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let perms = fs::Permissions::from_mode(0o600);
        fs::set_permissions(&path, perms)?;
    }

    Ok(())
}

pub fn is_initialized() -> bool {
    config_path().exists() && machine_token_path().exists()
}
