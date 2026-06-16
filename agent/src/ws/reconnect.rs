use std::time::Duration;

pub struct ReconnectBackoff {
    attempt: u32,
    max_delay_secs: u64,
}

impl ReconnectBackoff {
    pub fn new() -> Self {
        ReconnectBackoff {
            attempt: 0,
            max_delay_secs: 30,
        }
    }

    pub fn reset(&mut self) {
        self.attempt = 0;
    }

    pub fn next_delay(&mut self) -> Duration {
        let secs = if self.attempt == 0 {
            0
        } else {
            let shift = self.attempt.saturating_sub(1).min(63) as u32;
            let exp = 1u64.checked_shl(shift).unwrap_or(u64::MAX);
            exp.min(self.max_delay_secs)
        };
        self.attempt = self.attempt.saturating_add(1);
        Duration::from_secs(secs)
    }
}

impl Default for ReconnectBackoff {
    fn default() -> Self {
        Self::new()
    }
}
