package auth

import (
	"net/http"
	"strings"

	"github.com/redis/go-redis/v9"
	redisPkg "github.com/nod/server/internal/redis"
)

// RequireAuth validates a Bearer JWT in the Authorization header and injects
// the claims into the request context.
func RequireAuth(rdb *redis.Client) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			authHeader := r.Header.Get("Authorization")
			if !strings.HasPrefix(authHeader, "Bearer ") {
				http.Error(w, `{"code":"unauthorized","message":"missing bearer token"}`, http.StatusUnauthorized)
				return
			}
			tokenStr := strings.TrimPrefix(authHeader, "Bearer ")

			claims, err := VerifyAccessToken(tokenStr)
			if err != nil {
				http.Error(w, `{"code":"unauthorized","message":"invalid token"}`, http.StatusUnauthorized)
				return
			}

			// Check blocklist using the JWT ID (jti claim).
			if claims.ID != "" {
				blocked, err := redisPkg.IsBlocked(r.Context(), rdb, claims.ID)
				if err == nil && blocked {
					http.Error(w, `{"code":"unauthorized","message":"token revoked"}`, http.StatusUnauthorized)
					return
				}
			}

			ctx := WithUser(r.Context(), claims)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

// RequireRole checks that the authenticated user has the given org role.
// Must be used after RequireAuth.
func RequireRole(role string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			claims := UserFromContext(r.Context())
			if claims == nil {
				http.Error(w, `{"code":"unauthorized","message":"not authenticated"}`, http.StatusUnauthorized)
				return
			}
			if claims.Role != role && claims.Role != "OWNER" {
				http.Error(w, `{"code":"forbidden","message":"insufficient role"}`, http.StatusForbidden)
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

// RequireAPIKey validates an API key passed in the X-API-Key header for
// machine registration endpoints. The machine service will perform the actual
// DB lookup; this middleware only validates the format.
func RequireAPIKey(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		key := r.Header.Get("X-API-Key")
		if !strings.HasPrefix(key, "apk_") {
			http.Error(w, `{"code":"unauthorized","message":"missing or invalid api key"}`, http.StatusUnauthorized)
			return
		}
		// Store raw key in header for downstream handlers.
		r.Header.Set("X-API-Key-Raw", key)
		next.ServeHTTP(w, r)
	})
}
