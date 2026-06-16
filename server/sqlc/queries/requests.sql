-- name: CreateRequest :one
INSERT INTO permission_requests (
    id, session_id, workspace_id, machine_id, tool, input, input_hash,
    description, agent_signature, signature_verified, timeout_ms, seq, session_seq, expires_at
)
VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)
RETURNING *;

-- name: GetRequest :one
SELECT * FROM permission_requests WHERE id = $1;

-- name: ListWorkspaceRequests :many
SELECT * FROM permission_requests
WHERE workspace_id = $1
  AND ($2::text IS NULL OR status = $2::request_status)
  AND ($3::text IS NULL OR id < $3)
ORDER BY id DESC
LIMIT $4;

-- name: ListSessionRequests :many
SELECT * FROM permission_requests
WHERE session_id = $1
ORDER BY created_at DESC
LIMIT $2 OFFSET $3;

-- name: DecideRequest :one
UPDATE permission_requests
SET status = $2, decided_at = NOW()
WHERE id = $1 AND status = 'PENDING'
RETURNING *;

-- name: ExpirePendingRequests :many
UPDATE permission_requests
SET status = 'EXPIRED'
WHERE status = 'PENDING' AND expires_at < NOW()
RETURNING *;

-- name: CancelSessionRequests :exec
UPDATE permission_requests
SET status = 'CANCELLED'
WHERE session_id = $1 AND status = 'PENDING';

-- name: CountPendingByWorkspace :one
SELECT COUNT(*) FROM permission_requests
WHERE workspace_id = $1 AND status = 'PENDING';
