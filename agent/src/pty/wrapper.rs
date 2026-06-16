use std::io::{Read, Write};
use std::sync::{Arc, Mutex};

use portable_pty::{native_pty_system, CommandBuilder, PtySize};
use tracing::{debug, warn};

use crate::daemon::client::DaemonClient;
use crate::daemon::server::ApprovalRequest;
use crate::error::{Result, NodError};
use crate::pty::patterns::matches_prompt;

pub struct PtyWrapper {
    pub command: String,
    pub args: Vec<String>,
    pub patterns: Vec<String>,
    pub timeout_ms: u64,
}

impl PtyWrapper {
    pub async fn run(&self) -> Result<i32> {
        let pty_system = native_pty_system();

        let pair = pty_system
            .openpty(PtySize {
                rows: 24,
                cols: 80,
                pixel_width: 0,
                pixel_height: 0,
            })
            .map_err(|e| NodError::Io(std::io::Error::new(std::io::ErrorKind::Other, e.to_string())))?;

        let mut cmd = CommandBuilder::new(&self.command);
        for arg in &self.args {
            cmd.arg(arg);
        }

        let mut child = pair
            .slave
            .spawn_command(cmd)
            .map_err(|e| NodError::Io(std::io::Error::new(std::io::ErrorKind::Other, e.to_string())))?;

        // Drop the slave side so EOF propagates correctly
        drop(pair.slave);

        let master_write = pair.master.take_writer()
            .map_err(|e| NodError::Io(std::io::Error::new(std::io::ErrorKind::Other, e.to_string())))?;
        let master_write = Arc::new(Mutex::new(master_write));

        let mut master_read = pair.master.try_clone_reader()
            .map_err(|e| NodError::Io(std::io::Error::new(std::io::ErrorKind::Other, e.to_string())))?;

        let patterns = self.patterns.clone();
        let timeout_ms = self.timeout_ms;
        let master_write_clone = master_write.clone();

        // Run blocking I/O on a dedicated thread — stream output and detect prompts
        tokio::task::spawn_blocking(move || {
            let mut buf = [0u8; 1024];
            let mut output_buffer = String::new();

            loop {
                match master_read.read(&mut buf) {
                    Ok(0) => break,
                    Ok(n) => {
                        let chunk = String::from_utf8_lossy(&buf[..n]);
                        print!("{chunk}");
                        let _ = std::io::stdout().flush();
                        output_buffer.push_str(&chunk);

                        // Scan the buffer for prompt patterns
                        let lines: Vec<&str> = output_buffer.lines().collect();
                        if let Some(last) = lines.last() {
                            if matches_prompt(last, &patterns) {
                                debug!("pty: prompt detected: {last:?}");

                                // Request approval via daemon (blocking call inside thread)
                                let rt = tokio::runtime::Handle::try_current();
                                let req = ApprovalRequest {
                                    tool: "pty-prompt".to_string(),
                                    input: serde_json::json!({ "prompt": last }),
                                    description: Some(format!("PTY prompt: {last}")),
                                    timeout_ms,
                                };

                                let approved = if let Ok(handle) = rt {
                                    // We're inside a tokio runtime — use block_in_place
                                    tokio::task::block_in_place(|| {
                                        handle.block_on(async {
                                            let client = DaemonClient::new();
                                            match client.request_approval(req).await {
                                                Ok(resp) => resp.approved,
                                                Err(e) => {
                                                    warn!("pty: approval error: {e}");
                                                    false
                                                }
                                            }
                                        })
                                    })
                                } else {
                                    // Not inside a runtime, create one
                                    match tokio::runtime::Runtime::new() {
                                        Ok(rt) => rt.block_on(async {
                                            let client = DaemonClient::new();
                                            match client.request_approval(req).await {
                                                Ok(resp) => resp.approved,
                                                Err(e) => {
                                                    warn!("pty: approval error: {e}");
                                                    false
                                                }
                                            }
                                        }),
                                        Err(e) => {
                                            warn!("pty: failed to create runtime: {e}");
                                            false
                                        }
                                    }
                                };

                                let mut writer = master_write_clone.lock().unwrap();
                                if approved {
                                    let _ = writer.write_all(b"y\n");
                                } else {
                                    let _ = writer.write_all(b"n\n");
                                }

                                // Clear buffer after handling prompt
                                output_buffer.clear();
                            }
                        }
                    }
                    Err(e) => {
                        debug!("pty: read ended: {e}");
                        break;
                    }
                }
            }
        });

        // Wait for child to exit
        let exit_status = tokio::task::spawn_blocking(move || {
            child.wait().map(|s| {
                if s.success() {
                    0i32
                } else {
                    s.exit_code() as i32
                }
            })
        })
        .await
        .map_err(|e| NodError::Io(std::io::Error::new(std::io::ErrorKind::Other, e.to_string())))?
        .map_err(|e| NodError::Io(e))?;

        Ok(exit_status)
    }
}
