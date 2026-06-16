-- name: GetUserByEmail :one
SELECT * FROM users WHERE email = $1;

-- name: GetUserByID :one
SELECT * FROM users WHERE id = $1;

-- name: CreateUser :one
INSERT INTO users (id, email, name, avatar_url, password_hash)
VALUES ($1, $2, $3, $4, $5)
RETURNING *;

-- name: UpdateUser :one
UPDATE users
SET name = $2, avatar_url = $3, updated_at = NOW()
WHERE id = $1
RETURNING *;

-- name: CreateOAuthAccount :one
INSERT INTO oauth_accounts (id, user_id, provider, provider_id, email, name, avatar_url, access_token)
VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
ON CONFLICT (provider, provider_id)
DO UPDATE SET
    email = EXCLUDED.email,
    name = EXCLUDED.name,
    avatar_url = EXCLUDED.avatar_url,
    access_token = EXCLUDED.access_token,
    updated_at = NOW()
RETURNING *;

-- name: GetOAuthAccount :one
SELECT * FROM oauth_accounts WHERE provider = $1 AND provider_id = $2;

-- name: CreateRefreshToken :one
INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at)
VALUES ($1, $2, $3, $4)
RETURNING *;

-- name: GetRefreshTokenByHash :one
SELECT * FROM refresh_tokens
WHERE token_hash = $1 AND revoked_at IS NULL AND expires_at > NOW();

-- name: DeleteRefreshToken :exec
UPDATE refresh_tokens SET revoked_at = NOW() WHERE id = $1;

-- name: DeleteExpiredRefreshTokens :exec
DELETE FROM refresh_tokens WHERE expires_at < NOW() OR revoked_at IS NOT NULL;

-- name: DeleteUserRefreshTokens :exec
UPDATE refresh_tokens SET revoked_at = NOW() WHERE user_id = $1 AND revoked_at IS NULL;
