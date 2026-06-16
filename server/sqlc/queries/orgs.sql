-- name: CreateOrg :one
INSERT INTO organizations (id, name, slug)
VALUES ($1, $2, $3)
RETURNING *;

-- name: GetOrg :one
SELECT * FROM organizations WHERE id = $1;

-- name: GetOrgBySlug :one
SELECT * FROM organizations WHERE slug = $1;

-- name: ListUserOrgs :many
SELECT o.* FROM organizations o
JOIN org_members m ON m.org_id = o.id
WHERE m.user_id = $1
ORDER BY o.name;

-- name: UpdateOrg :one
UPDATE organizations
SET name = $2, slug = $3, avatar_url = $4, updated_at = NOW()
WHERE id = $1
RETURNING *;

-- name: DeleteOrg :exec
DELETE FROM organizations WHERE id = $1;

-- name: CreateOrgMember :one
INSERT INTO org_members (id, org_id, user_id, role, invited_by)
VALUES ($1, $2, $3, $4, $5)
ON CONFLICT (org_id, user_id) DO NOTHING
RETURNING *;

-- name: GetOrgMember :one
SELECT * FROM org_members WHERE org_id = $1 AND user_id = $2;

-- name: ListOrgMembers :many
SELECT m.*, u.email, u.name, u.avatar_url FROM org_members m
JOIN users u ON u.id = m.user_id
WHERE m.org_id = $1
ORDER BY m.created_at;

-- name: UpdateOrgMemberRole :one
UPDATE org_members
SET role = $3, updated_at = NOW()
WHERE org_id = $1 AND user_id = $2
RETURNING *;

-- name: DeleteOrgMember :exec
DELETE FROM org_members WHERE org_id = $1 AND user_id = $2;
