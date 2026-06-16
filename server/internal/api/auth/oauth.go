package auth

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"net/http"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/rs/zerolog"

	"github.com/nod/server/internal/respond"
	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/auth/oauth"
	"github.com/nod/server/pkg/id"
)

// OAuthHandler handles OAuth flows.
type OAuthHandler struct {
	db     *pgxpool.Pool
	github *oauth.GitHubProvider
	google *oauth.GoogleProvider
	audit  audit.AuditService
	log    zerolog.Logger
}

// NewOAuthHandler creates an OAuthHandler.
func NewOAuthHandler(
	db *pgxpool.Pool,
	github *oauth.GitHubProvider,
	google *oauth.GoogleProvider,
	auditSvc audit.AuditService,
	log zerolog.Logger,
) *OAuthHandler {
	return &OAuthHandler{db: db, github: github, google: google, audit: auditSvc, log: log}
}

// GitHubRedirect redirects the user to GitHub for OAuth authorization.
func (h *OAuthHandler) GitHubRedirect(w http.ResponseWriter, r *http.Request) {
	state := randomState()
	http.SetCookie(w, &http.Cookie{Name: "oauth_state", Value: state, Path: "/", HttpOnly: true, SameSite: http.SameSiteLaxMode})
	http.Redirect(w, r, h.github.AuthURL(state), http.StatusFound)
}

// GitHubCallback handles the GitHub OAuth callback.
func (h *OAuthHandler) GitHubCallback(w http.ResponseWriter, r *http.Request) {
	if !verifyState(r) {
		respond.BadRequest(w, "invalid oauth state")
		return
	}
	code := r.URL.Query().Get("code")
	if code == "" {
		respond.BadRequest(w, "missing code")
		return
	}
	user, _, err := h.github.Exchange(r.Context(), code)
	if err != nil {
		h.log.Error().Err(err).Msg("github oauth exchange failed")
		respond.InternalError(w, err, h.log)
		return
	}

	userID, email, err := h.upsertOAuthUser(r, user.Email, user.Name, user.AvatarURL, "github", fmt.Sprintf("%d", user.ID))
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	authH := &Handler{db: h.db, audit: h.audit, log: h.log}
	resp, err := authH.issueTokens(r.Context(), r, userID, email)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}
	respond.JSON(w, http.StatusOK, resp)
}

// GoogleRedirect redirects the user to Google for OAuth authorization.
func (h *OAuthHandler) GoogleRedirect(w http.ResponseWriter, r *http.Request) {
	state := randomState()
	http.SetCookie(w, &http.Cookie{Name: "oauth_state", Value: state, Path: "/", HttpOnly: true, SameSite: http.SameSiteLaxMode})
	http.Redirect(w, r, h.google.AuthURL(state), http.StatusFound)
}

// GoogleCallback handles the Google OAuth callback.
func (h *OAuthHandler) GoogleCallback(w http.ResponseWriter, r *http.Request) {
	if !verifyState(r) {
		respond.BadRequest(w, "invalid oauth state")
		return
	}
	code := r.URL.Query().Get("code")
	if code == "" {
		respond.BadRequest(w, "missing code")
		return
	}
	user, _, err := h.google.Exchange(r.Context(), code)
	if err != nil {
		h.log.Error().Err(err).Msg("google oauth exchange failed")
		respond.InternalError(w, err, h.log)
		return
	}

	userID, email, err := h.upsertOAuthUser(r, user.Email, user.Name, user.Picture, "google", user.Sub)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	authH := &Handler{db: h.db, audit: h.audit, log: h.log}
	resp, err := authH.issueTokens(r.Context(), r, userID, email)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}
	respond.JSON(w, http.StatusOK, resp)
}

func (h *OAuthHandler) upsertOAuthUser(r *http.Request, email, name, avatarURL, provider, providerID string) (string, string, error) {
	var userID string
	err := h.db.QueryRow(r.Context(),
		`SELECT user_id FROM oauth_accounts WHERE provider=$1 AND provider_id=$2`, provider, providerID,
	).Scan(&userID)
	if err == nil {
		_, _ = h.db.Exec(r.Context(),
			`UPDATE oauth_accounts SET email=$1, name=$2, avatar_url=$3, updated_at=NOW() WHERE provider=$4 AND provider_id=$5`,
			email, name, avatarURL, provider, providerID)
		return userID, email, nil
	}

	// Check if user with this email exists.
	_ = h.db.QueryRow(r.Context(), `SELECT id FROM users WHERE email=$1`, email).Scan(&userID)
	if userID == "" {
		userID = id.New()
		if _, err = h.db.Exec(r.Context(),
			`INSERT INTO users (id, email, name, avatar_url) VALUES ($1,$2,$3,$4)`,
			userID, email, name, avatarURL,
		); err != nil {
			return "", "", fmt.Errorf("create oauth user: %w", err)
		}
	}

	if _, err = h.db.Exec(r.Context(),
		`INSERT INTO oauth_accounts (id, user_id, provider, provider_id, email, name, avatar_url)
		 VALUES ($1,$2,$3,$4,$5,$6,$7)
		 ON CONFLICT (provider, provider_id) DO NOTHING`,
		id.New(), userID, provider, providerID, email, name, avatarURL,
	); err != nil {
		return "", "", fmt.Errorf("link oauth account: %w", err)
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		EventType: "user.oauth_login",
		ActorType: "USER",
		ActorID:   userID,
		IPAddress: r.RemoteAddr,
		Payload:   map[string]any{"provider": provider},
	})

	return userID, email, nil
}

func randomState() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}

func verifyState(r *http.Request) bool {
	cookie, err := r.Cookie("oauth_state")
	if err != nil {
		return false
	}
	return r.URL.Query().Get("state") == cookie.Value
}
