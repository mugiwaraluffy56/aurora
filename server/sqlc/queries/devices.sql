-- name: CreateDeviceToken :one
INSERT INTO device_tokens (id, user_id, token, platform)
VALUES ($1, $2, $3, $4)
ON CONFLICT (user_id, token) DO UPDATE SET platform = EXCLUDED.platform, updated_at = NOW()
RETURNING *;

-- name: GetDeviceToken :one
SELECT * FROM device_tokens WHERE id = $1;

-- name: ListUserDeviceTokens :many
SELECT * FROM device_tokens WHERE user_id = $1 ORDER BY created_at DESC;

-- name: DeleteDeviceToken :exec
DELETE FROM device_tokens WHERE id = $1 AND user_id = $2;

-- name: ListWorkspaceApproverDeviceTokens :many
SELECT dt.* FROM device_tokens dt
JOIN org_members om ON om.user_id = dt.user_id
JOIN workspaces w ON w.org_id = om.org_id
WHERE w.id = $1
  AND om.role IN ('OWNER', 'ADMIN', 'MEMBER');

-- name: DeleteDeviceTokenByToken :exec
DELETE FROM device_tokens WHERE token = $1;
