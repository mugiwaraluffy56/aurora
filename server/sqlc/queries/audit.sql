-- name: CreateAuditEvent :one
INSERT INTO audit_events (
    id, org_id, workspace_id, session_id, request_id,
    event_type, actor_type, actor_id, device_id, ip_address, user_agent,
    payload, previous_hash, checksum
)
VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)
RETURNING *;

-- name: ListAuditEvents :many
SELECT * FROM audit_events
WHERE org_id = $1
  AND ($2::text IS NULL OR workspace_id = $2)
  AND ($3::text[] IS NULL OR event_type = ANY($3::text[]))
  AND ($4::timestamptz IS NULL OR created_at >= $4)
  AND ($5::timestamptz IS NULL OR created_at <= $5)
  AND ($6::text IS NULL OR id < $6)
ORDER BY id DESC
LIMIT $7;

-- name: GetLastAuditEventHash :one
SELECT checksum FROM audit_events
WHERE org_id = $1
ORDER BY created_at DESC, id DESC
LIMIT 1;

-- name: ExportAuditEvents :many
SELECT * FROM audit_events
WHERE org_id = $1
  AND ($2::text IS NULL OR workspace_id = $2)
  AND ($3::timestamptz IS NULL OR created_at >= $3)
  AND ($4::timestamptz IS NULL OR created_at <= $4)
ORDER BY created_at ASC, id ASC;
