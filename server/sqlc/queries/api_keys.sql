-- name: CreateAPIKey :one
INSERT INTO api_keys (id, workspace_id, created_by, name, key_hash, key_prefix)
VALUES ($1, $2, $3, $4, $5, $6)
RETURNING *;

-- name: GetAPIKeyByHash :one
SELECT * FROM api_keys WHERE key_hash = $1 AND revoked_at IS NULL;

-- name: ListWorkspaceAPIKeys :many
SELECT * FROM api_keys WHERE workspace_id = $1 ORDER BY created_at DESC;

-- name: RevokeAPIKey :one
UPDATE api_keys SET revoked_at = NOW()
WHERE id = $1 AND revoked_at IS NULL
RETURNING *;
