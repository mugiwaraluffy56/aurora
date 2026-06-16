-- TapGuard initial schema

-- ENUM types
CREATE TYPE org_role AS ENUM ('OWNER', 'ADMIN', 'APPROVER', 'MEMBER', 'VIEWER');
CREATE TYPE machine_status AS ENUM ('ACTIVE', 'REVOKED');
CREATE TYPE session_status AS ENUM ('ACTIVE', 'DISCONNECTED', 'ENDED', 'ZOMBIE');
CREATE TYPE request_status AS ENUM ('PENDING', 'APPROVED', 'DENIED', 'EXPIRED', 'CANCELLED', 'AUTO_APPROVED', 'AUTO_DENIED');
CREATE TYPE rule_scope AS ENUM ('SESSION', 'USER', 'WORKSPACE', 'ORG');
CREATE TYPE rule_action AS ENUM ('AUTO_APPROVE', 'AUTO_DENY', 'REQUIRE_APPROVAL');
CREATE TYPE audit_actor_type AS ENUM ('USER', 'MACHINE', 'RULES_ENGINE', 'SYSTEM', 'ADMIN');
CREATE TYPE device_platform AS ENUM ('ios', 'android');

-- Users
CREATE TABLE users (
    id          TEXT PRIMARY KEY,
    email       TEXT UNIQUE NOT NULL,
    name        TEXT NOT NULL DEFAULT '',
    avatar_url  TEXT NOT NULL DEFAULT '',
    password_hash TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_email ON users (email);

-- OAuth accounts (GitHub, Google)
CREATE TABLE oauth_accounts (
    id          TEXT PRIMARY KEY,
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider    TEXT NOT NULL,
    provider_id TEXT NOT NULL,
    email       TEXT NOT NULL,
    name        TEXT NOT NULL DEFAULT '',
    avatar_url  TEXT NOT NULL DEFAULT '',
    access_token TEXT NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_id)
);
CREATE INDEX idx_oauth_accounts_user_id ON oauth_accounts (user_id);

-- Refresh tokens
CREATE TABLE refresh_tokens (
    id          TEXT PRIMARY KEY,
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT UNIQUE NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at  TIMESTAMPTZ
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens (token_hash);

-- Device tokens for push notifications
CREATE TABLE device_tokens (
    id          TEXT PRIMARY KEY,
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       TEXT NOT NULL,
    platform    device_platform NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, token)
);
CREATE INDEX idx_device_tokens_user_id ON device_tokens (user_id);

-- Organizations
CREATE TABLE organizations (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    slug        TEXT UNIQUE NOT NULL,
    avatar_url  TEXT NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Org members
CREATE TABLE org_members (
    id          TEXT PRIMARY KEY,
    org_id      TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        org_role NOT NULL DEFAULT 'MEMBER',
    invited_by  TEXT REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (org_id, user_id)
);
CREATE INDEX idx_org_members_org_id ON org_members (org_id);
CREATE INDEX idx_org_members_user_id ON org_members (user_id);

-- Workspaces
CREATE TABLE workspaces (
    id          TEXT PRIMARY KEY,
    org_id      TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    slug        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (org_id, slug)
);
CREATE INDEX idx_workspaces_org_id ON workspaces (org_id);

-- API keys
CREATE TABLE api_keys (
    id          TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    created_by  TEXT NOT NULL REFERENCES users(id),
    name        TEXT NOT NULL,
    key_hash    TEXT UNIQUE NOT NULL,
    key_prefix  TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at  TIMESTAMPTZ
);
CREATE INDEX idx_api_keys_workspace_id ON api_keys (workspace_id);
CREATE INDEX idx_api_keys_hash ON api_keys (key_hash);

-- Machines
CREATE TABLE machines (
    id              TEXT PRIMARY KEY,
    workspace_id    TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    api_key_id      TEXT REFERENCES api_keys(id),
    owned_by        TEXT NOT NULL REFERENCES users(id),
    name            TEXT NOT NULL,
    fingerprint     TEXT UNIQUE NOT NULL,
    public_key      TEXT NOT NULL DEFAULT '',
    machine_type    TEXT NOT NULL DEFAULT 'generic',
    status          machine_status NOT NULL DEFAULT 'ACTIVE',
    current_token_jti TEXT,
    last_seen_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMPTZ
);
CREATE INDEX idx_machines_workspace_id ON machines (workspace_id);
CREATE INDEX idx_machines_fingerprint ON machines (fingerprint);
CREATE INDEX idx_machines_owned_by ON machines (owned_by);

-- Sessions
CREATE TABLE sessions (
    id              TEXT PRIMARY KEY,
    machine_id      TEXT NOT NULL REFERENCES machines(id) ON DELETE CASCADE,
    workspace_id    TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    connection_id   TEXT NOT NULL,
    agent_type      TEXT NOT NULL DEFAULT '',
    status          session_status NOT NULL DEFAULT 'ACTIVE',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at        TIMESTAMPTZ,
    last_heartbeat  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    seq             INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_sessions_machine_id ON sessions (machine_id);
CREATE INDEX idx_sessions_workspace_id ON sessions (workspace_id);
CREATE INDEX idx_sessions_status ON sessions (status);
CREATE INDEX idx_sessions_last_heartbeat ON sessions (last_heartbeat) WHERE status = 'ACTIVE';

-- Permission requests
CREATE TABLE permission_requests (
    id              TEXT PRIMARY KEY,
    session_id      TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    workspace_id    TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    machine_id      TEXT NOT NULL REFERENCES machines(id),
    tool            TEXT NOT NULL,
    input           JSONB NOT NULL DEFAULT '{}',
    input_hash      TEXT NOT NULL,
    description     TEXT NOT NULL DEFAULT '',
    status          request_status NOT NULL DEFAULT 'PENDING',
    auto_decided    BOOLEAN NOT NULL DEFAULT FALSE,
    rule_id         TEXT,
    agent_signature TEXT NOT NULL DEFAULT '',
    signature_verified BOOLEAN NOT NULL DEFAULT FALSE,
    timeout_ms      INT NOT NULL DEFAULT 30000,
    seq             INT NOT NULL DEFAULT 0,
    session_seq     INT NOT NULL DEFAULT 0,
    expires_at      TIMESTAMPTZ NOT NULL,
    decided_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_permission_requests_session_id ON permission_requests (session_id);
CREATE INDEX idx_permission_requests_workspace_id ON permission_requests (workspace_id);
CREATE INDEX idx_permission_requests_status ON permission_requests (status);
CREATE INDEX idx_permission_requests_expires_at ON permission_requests (expires_at) WHERE status = 'PENDING';

-- Request approvals (who decided)
CREATE TABLE request_approvals (
    id          TEXT PRIMARY KEY,
    request_id  TEXT NOT NULL REFERENCES permission_requests(id) ON DELETE CASCADE,
    decided_by  TEXT REFERENCES users(id),
    device_id   TEXT REFERENCES device_tokens(id),
    approved    BOOLEAN NOT NULL,
    idempotency_key TEXT UNIQUE,
    decided_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_request_approvals_request_id ON request_approvals (request_id);

-- Approval policies
CREATE TABLE approval_policies (
    id              TEXT PRIMARY KEY,
    workspace_id    TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    description     TEXT NOT NULL DEFAULT '',
    require_count   INT NOT NULL DEFAULT 1,
    timeout_ms      INT NOT NULL DEFAULT 30000,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_approval_policies_workspace_id ON approval_policies (workspace_id);

-- Rules
CREATE TABLE rules (
    id              TEXT PRIMARY KEY,
    scope           rule_scope NOT NULL,
    scope_id        TEXT NOT NULL,
    workspace_id    TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    description     TEXT NOT NULL DEFAULT '',
    priority        INT NOT NULL DEFAULT 100,
    action          rule_action NOT NULL,
    conditions      JSONB NOT NULL DEFAULT '{}',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at      TIMESTAMPTZ,
    hit_count       BIGINT NOT NULL DEFAULT 0,
    created_by      TEXT NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rules_workspace_id ON rules (workspace_id);
CREATE INDEX idx_rules_scope ON rules (scope, scope_id);
CREATE INDEX idx_rules_enabled ON rules (enabled) WHERE enabled = TRUE;

-- Delegations
CREATE TABLE delegations (
    id              TEXT PRIMARY KEY,
    delegator_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    delegate_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workspace_id    TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    reason          TEXT NOT NULL DEFAULT '',
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (delegator_id, delegate_id, workspace_id)
);
CREATE INDEX idx_delegations_delegate_id ON delegations (delegate_id);
CREATE INDEX idx_delegations_workspace_id ON delegations (workspace_id);
CREATE INDEX idx_delegations_expires_at ON delegations (expires_at);

-- Audit events (append-only, tamper-evident hash chain)
CREATE TABLE audit_events (
    id              TEXT NOT NULL,
    org_id          TEXT NOT NULL,
    workspace_id    TEXT,
    session_id      TEXT,
    request_id      TEXT,
    event_type      TEXT NOT NULL,
    actor_type      audit_actor_type NOT NULL DEFAULT 'SYSTEM',
    actor_id        TEXT,
    device_id       TEXT,
    ip_address      TEXT,
    user_agent      TEXT,
    payload         JSONB NOT NULL DEFAULT '{}',
    previous_hash   TEXT NOT NULL DEFAULT '',
    checksum        TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (created_at);

CREATE TABLE audit_events_default PARTITION OF audit_events DEFAULT;

CREATE INDEX idx_audit_events_org_id ON audit_events (org_id, created_at DESC);
CREATE INDEX idx_audit_events_event_type ON audit_events (event_type, created_at DESC);
CREATE INDEX idx_audit_events_workspace_id ON audit_events (workspace_id, created_at DESC) WHERE workspace_id IS NOT NULL;
CREATE INDEX idx_audit_events_request_id ON audit_events (request_id) WHERE request_id IS NOT NULL;

-- Prevent mutation of audit events
CREATE OR REPLACE FUNCTION prevent_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_audit_update
    BEFORE UPDATE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();

CREATE TRIGGER trg_prevent_audit_delete
    BEFORE DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();
