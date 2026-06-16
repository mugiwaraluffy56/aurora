use std::fs;

use crate::config::paths::pid_file_path;
use crate::error::Result;

pub fn write_pid_file() -> Result<()> {
    let pid = std::process::id();
    let path = pid_file_path();

    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }

    fs::write(&path, pid.to_string())?;

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(&path, fs::Permissions::from_mode(0o600))?;
    }

    Ok(())
}

pub fn remove_pid_file() {
    let path = pid_file_path();
    if path.exists() {
        let _ = fs::remove_file(&path);
    }
}

pub fn read_pid() -> Option<u32> {
    let path = pid_file_path();
    let content = fs::read_to_string(&path).ok()?;
    content.trim().parse::<u32>().ok()
}

pub fn is_process_alive(pid: u32) -> bool {
    #[cfg(unix)]
    {
        // kill(pid, 0) checks existence without sending a signal
        let result = unsafe { libc_kill(pid as i32, 0) };
        result == 0
    }

    #[cfg(not(unix))]
    {
        // On non-Unix, check via process list
        use std::process::Command;
        Command::new("tasklist")
            .output()
            .ok()
            .and_then(|o| String::from_utf8(o.stdout).ok())
            .map(|s| s.contains(&pid.to_string()))
            .unwrap_or(false)
    }
}

#[cfg(unix)]
unsafe fn libc_kill(pid: i32, sig: i32) -> i32 {
    extern "C" {
        fn kill(pid: i32, sig: i32) -> i32;
    }
    kill(pid, sig)
}
