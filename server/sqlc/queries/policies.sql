-- name: CreatePolicy :one
INSERT INTO approval_policies (id, workspace_id, name, description, require_count, timeout_ms)
VALUES ($1, $2, $3, $4, $5, $6)
RETURNING *;

-- name: GetPolicy :one
SELECT * FROM approval_policies WHERE id = $1;

-- name: ListWorkspacePolicies :many
SELECT * FROM approval_policies WHERE workspace_id = $1 ORDER BY name;

-- name: UpdatePolicy :one
UPDATE approval_policies
SET name = $2, description = $3, require_count = $4, timeout_ms = $5, updated_at = NOW()
WHERE id = $1
RETURNING *;

-- name: DeletePolicy :exec
DELETE FROM approval_policies WHERE id = $1;
