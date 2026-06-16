-- name: CreateMachine :one
INSERT INTO machines (id, workspace_id, api_key_id, owned_by, name, fingerprint, public_key, machine_type, current_token_jti)
VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
RETURNING *;

-- name: GetMachineByID :one
SELECT * FROM machines WHERE id = $1;

-- name: GetMachineByFingerprint :one
SELECT * FROM machines WHERE fingerprint = $1;

-- name: ListWorkspaceMachines :many
SELECT * FROM machines WHERE workspace_id = $1 ORDER BY created_at DESC;

-- name: UpdateMachineName :one
UPDATE machines SET name = $2, updated_at = NOW()
WHERE id = $1
RETURNING *;

-- name: RevokeMachine :one
UPDATE machines
SET status = 'REVOKED', revoked_at = NOW(), updated_at = NOW()
WHERE id = $1
RETURNING *;

-- name: TransferMachine :one
UPDATE machines
SET owned_by = $2, workspace_id = COALESCE($3, workspace_id), updated_at = NOW()
WHERE id = $1
RETURNING *;

-- name: UpdateMachineToken :one
UPDATE machines SET current_token_jti = $2, updated_at = NOW()
WHERE id = $1
RETURNING *;

-- name: UpdateMachineLastSeen :exec
UPDATE machines SET last_seen_at = NOW(), updated_at = NOW()
WHERE id = $1;
