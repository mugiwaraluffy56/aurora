-- name: CreateDelegation :one
INSERT INTO delegations (id, delegator_id, delegate_id, workspace_id, reason, expires_at)
VALUES ($1, $2, $3, $4, $5, $6)
ON CONFLICT (delegator_id, delegate_id, workspace_id) DO UPDATE
SET reason = EXCLUDED.reason, expires_at = EXCLUDED.expires_at
RETURNING *;

-- name: ListActiveDelegationsForUser :many
SELECT * FROM delegations
WHERE delegate_id = $1
  AND workspace_id = $2
  AND expires_at > NOW();

-- name: ListDelegationsByDelegator :many
SELECT * FROM delegations WHERE delegator_id = $1 ORDER BY created_at DESC;

-- name: DeleteDelegation :exec
DELETE FROM delegations WHERE id = $1;

-- name: ExpireDelegations :exec
DELETE FROM delegations WHERE expires_at < NOW();
