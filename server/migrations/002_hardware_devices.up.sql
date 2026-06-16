-- Hardware devices (physical ESP32 approval devices)
CREATE TABLE hardware_devices (
    id           TEXT PRIMARY KEY,
    user_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name         TEXT NOT NULL DEFAULT 'TapGuard Device',
    key_hash     TEXT UNIQUE NOT NULL,
    key_prefix   TEXT NOT NULL,
    last_seen_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at   TIMESTAMPTZ
);

CREATE INDEX idx_hardware_devices_user_id ON hardware_devices (user_id);
CREATE INDEX idx_hardware_devices_workspace_id ON hardware_devices (workspace_id);
CREATE INDEX idx_hardware_devices_key_hash ON hardware_devices (key_hash);
