-- name: CreateSession :one
INSERT INTO sessions (id, machine_id, workspace_id, connection_id, agent_type)
VALUES ($1, $2, $3, $4, $5)
RETURNING *;

-- name: GetSession :one
SELECT * FROM sessions WHERE id = $1;

-- name: ListWorkspaceSessions :many
SELECT * FROM sessions WHERE workspace_id = $1 ORDER BY started_at DESC
LIMIT $2 OFFSET $3;

-- name: ListMachineSessions :many
SELECT * FROM sessions WHERE machine_id = $1 ORDER BY started_at DESC
LIMIT $2 OFFSET $3;

-- name: UpdateSessionStatus :one
UPDATE sessions SET status = $2, ended_at = CASE WHEN $2 != 'ACTIVE' THEN NOW() ELSE ended_at END
WHERE id = $1
RETURNING *;

-- name: UpdateSessionHeartbeat :exec
UPDATE sessions SET last_heartbeat = NOW(), seq = seq + 1
WHERE id = $1;

-- name: MarkZombieSessions :many
UPDATE sessions
SET status = 'ZOMBIE'
WHERE status = 'ACTIVE'
  AND last_heartbeat < NOW() - INTERVAL '90 seconds'
RETURNING *;

-- name: EndZombieSessions :exec
UPDATE sessions
SET status = 'ENDED', ended_at = NOW()
WHERE status = 'ZOMBIE'
  AND last_heartbeat < NOW() - INTERVAL '10 minutes';

-- name: IncrementSessionSeq :one
UPDATE sessions SET seq = seq + 1 WHERE id = $1 RETURNING seq;
