use std::collections::HashMap;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::Mutex;

pub struct SessionState {
    pub session_id: String,
    pub seq: AtomicU32,
    pub inflight: Mutex<HashMap<String, ()>>,
}

impl SessionState {
    pub fn new(session_id: String) -> Self {
        SessionState {
            session_id,
            seq: AtomicU32::new(0),
            inflight: Mutex::new(HashMap::new()),
        }
    }

    pub fn next_seq(&self) -> u32 {
        self.seq.fetch_add(1, Ordering::SeqCst)
    }

    pub fn add_inflight(&self, request_id: &str) {
        let mut map = self.inflight.lock().unwrap();
        map.insert(request_id.to_string(), ());
    }

    pub fn remove_inflight(&self, request_id: &str) {
        let mut map = self.inflight.lock().unwrap();
        map.remove(request_id);
    }

    pub fn inflight_ids(&self) -> Vec<String> {
        let map = self.inflight.lock().unwrap();
        map.keys().cloned().collect()
    }
}
