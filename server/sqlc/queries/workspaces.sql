-- name: CreateWorkspace :one
INSERT INTO workspaces (id, org_id, name, slug)
VALUES ($1, $2, $3, $4)
RETURNING *;

-- name: GetWorkspace :one
SELECT * FROM workspaces WHERE id = $1;

-- name: ListOrgWorkspaces :many
SELECT * FROM workspaces WHERE org_id = $1 ORDER BY name;

-- name: UpdateWorkspace :one
UPDATE workspaces
SET name = $2, slug = $3, updated_at = NOW()
WHERE id = $1
RETURNING *;

-- name: DeleteWorkspace :exec
DELETE FROM workspaces WHERE id = $1;
