-- name: CreateRule :one
INSERT INTO rules (id, scope, scope_id, workspace_id, name, description, priority, action, conditions, enabled, expires_at, created_by)
VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
RETURNING *;

-- name: GetRule :one
SELECT * FROM rules WHERE id = $1;

-- name: ListWorkspaceRules :many
SELECT * FROM rules WHERE workspace_id = $1 ORDER BY priority ASC, created_at ASC;

-- name: ListOrgRules :many
SELECT * FROM rules WHERE scope = 'ORG' AND scope_id = $1 AND enabled = TRUE ORDER BY priority ASC;

-- name: ListRulesForSession :many
SELECT * FROM rules
WHERE workspace_id = $1
  AND enabled = TRUE
  AND (expires_at IS NULL OR expires_at > NOW())
  AND (
      (scope = 'ORG' AND scope_id = $2)
      OR (scope = 'WORKSPACE' AND scope_id = $1)
      OR (scope = 'USER' AND scope_id = $3)
      OR (scope = 'SESSION' AND scope_id = $4)
  )
ORDER BY
    CASE scope
        WHEN 'SESSION' THEN 1
        WHEN 'USER' THEN 2
        WHEN 'WORKSPACE' THEN 3
        WHEN 'ORG' THEN 4
    END ASC,
    priority ASC;

-- name: UpdateRule :one
UPDATE rules
SET name = $2, description = $3, priority = $4, action = $5, conditions = $6,
    enabled = $7, expires_at = $8, updated_at = NOW()
WHERE id = $1
RETURNING *;

-- name: DeleteRule :exec
DELETE FROM rules WHERE id = $1;

-- name: DisableExpiredRules :exec
UPDATE rules SET enabled = FALSE, updated_at = NOW()
WHERE enabled = TRUE AND expires_at IS NOT NULL AND expires_at < NOW();

-- name: IncrementRuleHitCount :exec
UPDATE rules SET hit_count = hit_count + 1 WHERE id = $1;
