-- Drop everything in reverse dependency order

DROP TRIGGER IF EXISTS trg_prevent_audit_delete ON audit_events;
DROP TRIGGER IF EXISTS trg_prevent_audit_update ON audit_events;
DROP FUNCTION IF EXISTS prevent_audit_mutation();

DROP TABLE IF EXISTS audit_events_default;
DROP TABLE IF EXISTS audit_events;

DROP TABLE IF EXISTS delegations;
DROP TABLE IF EXISTS rules;
DROP TABLE IF EXISTS approval_policies;
DROP TABLE IF EXISTS request_approvals;
DROP TABLE IF EXISTS permission_requests;
DROP TABLE IF EXISTS sessions;
DROP TABLE IF EXISTS machines;
DROP TABLE IF EXISTS api_keys;
DROP TABLE IF EXISTS workspaces;
DROP TABLE IF EXISTS org_members;
DROP TABLE IF EXISTS organizations;
DROP TABLE IF EXISTS device_tokens;
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS oauth_accounts;
DROP TABLE IF EXISTS users;

DROP TYPE IF EXISTS device_platform;
DROP TYPE IF EXISTS audit_actor_type;
DROP TYPE IF EXISTS rule_action;
DROP TYPE IF EXISTS rule_scope;
DROP TYPE IF EXISTS request_status;
DROP TYPE IF EXISTS session_status;
DROP TYPE IF EXISTS machine_status;
DROP TYPE IF EXISTS org_role;
