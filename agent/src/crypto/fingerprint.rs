use sha2::{Digest, Sha256};

use crate::error::{Result, NodError};

pub fn generate_fingerprint() -> Result<String> {
    let raw = collect_fingerprint_data()?;
    let mut hasher = Sha256::new();
    hasher.update(raw.as_bytes());
    Ok(hex::encode(hasher.finalize()))
}

fn collect_fingerprint_data() -> Result<String> {
    // Check for Docker container first
    #[cfg(target_os = "linux")]
    if std::path::Path::new("/.dockerenv").exists() {
        return collect_docker_fingerprint();
    }

    // Try AWS metadata service (1s timeout)
    if let Some(instance_id) = try_aws_instance_id() {
        return Ok(format!("aws:{instance_id}"));
    }

    #[cfg(target_os = "macos")]
    return collect_macos_fingerprint();

    #[cfg(target_os = "linux")]
    return collect_linux_fingerprint();

    #[cfg(not(any(target_os = "macos", target_os = "linux")))]
    {
        let hostname = hostname_string()?;
        Ok(format!("generic:{hostname}"))
    }
}

fn hostname_string() -> Result<String> {
    let output = std::process::Command::new("hostname")
        .output()
        .map_err(|e| NodError::Io(e))?;
    Ok(String::from_utf8_lossy(&output.stdout).trim().to_string())
}

fn try_aws_instance_id() -> Option<String> {
    // Use blocking reqwest with timeout — this is called from sync context
    let client = reqwest::blocking::Client::builder()
        .timeout(std::time::Duration::from_secs(1))
        .build()
        .ok()?;
    let resp = client
        .get("http://169.254.169.254/latest/meta-data/instance-id")
        .send()
        .ok()?;
    if resp.status().is_success() {
        resp.text().ok()
    } else {
        None
    }
}

#[cfg(target_os = "macos")]
fn collect_macos_fingerprint() -> Result<String> {
    let hostname = hostname_string().unwrap_or_default();

    let serial = {
        let out = std::process::Command::new("system_profiler")
            .args(["SPHardwareDataType"])
            .output()
            .ok();
        out.and_then(|o| {
            String::from_utf8(o.stdout).ok().and_then(|s| {
                s.lines()
                    .find(|l| l.contains("Serial Number"))
                    .and_then(|l| l.split(':').nth(1))
                    .map(|s| s.trim().to_string())
            })
        })
        .unwrap_or_default()
    };

    let mac = first_mac_address().unwrap_or_default();
    Ok(format!("{hostname}:{serial}:{mac}"))
}

#[cfg(target_os = "linux")]
fn collect_linux_fingerprint() -> Result<String> {
    let hostname = hostname_string().unwrap_or_default();

    let machine_id = std::fs::read_to_string("/etc/machine-id")
        .or_else(|_| std::fs::read_to_string("/var/lib/dbus/machine-id"))
        .unwrap_or_default();
    let machine_id = machine_id.trim().to_string();

    let mac = first_mac_address().unwrap_or_default();
    Ok(format!("{hostname}:{machine_id}:{mac}"))
}

#[cfg(target_os = "linux")]
fn collect_docker_fingerprint() -> Result<String> {
    let hostname = hostname_string().unwrap_or_default();

    let container_id = std::fs::read_to_string("/proc/self/cgroup")
        .ok()
        .and_then(|s| {
            s.lines()
                .find(|l| l.contains("docker") || l.contains("containerd"))
                .and_then(|l| l.split('/').last())
                .map(|s| s.trim().to_string())
        })
        .unwrap_or_default();

    Ok(format!("docker:{container_id}:{hostname}"))
}

fn first_mac_address() -> Option<String> {
    #[cfg(target_os = "macos")]
    {
        let out = std::process::Command::new("ifconfig")
            .output()
            .ok()?;
        let text = String::from_utf8(out.stdout).ok()?;
        for line in text.lines() {
            let trimmed = line.trim();
            if trimmed.starts_with("ether ") {
                let mac = trimmed.trim_start_matches("ether ").trim();
                if !mac.starts_with("00:00:00") {
                    return Some(mac.to_string());
                }
            }
        }
        None
    }

    #[cfg(target_os = "linux")]
    {
        let dir = std::path::Path::new("/sys/class/net");
        if let Ok(entries) = std::fs::read_dir(dir) {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                if name == "lo" {
                    continue;
                }
                let addr_path = dir.join(&name).join("address");
                if let Ok(mac) = std::fs::read_to_string(&addr_path) {
                    let mac = mac.trim().to_string();
                    if mac != "00:00:00:00:00:00" {
                        return Some(mac);
                    }
                }
            }
        }
        None
    }

    #[cfg(not(any(target_os = "macos", target_os = "linux")))]
    {
        None
    }
}
