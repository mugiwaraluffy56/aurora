package auth

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"net/http"

	"github.com/go-playground/validator/v10"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/rs/zerolog"

	"github.com/nod/server/internal/respond"
	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/auth"
	"github.com/nod/server/pkg/id"
)

var validate = validator.New()

// Handler handles auth endpoints.
type Handler struct {
	db    *pgxpool.Pool
	audit audit.AuditService
	log   zerolog.Logger
}

// NewHandler creates an auth Handler.
func NewHandler(db *pgxpool.Pool, auditSvc audit.AuditService, log zerolog.Logger) *Handler {
	return &Handler{db: db, audit: auditSvc, log: log}
}

type registerRequest struct {
	Email    string `json:"email" validate:"required,email"`
	Password string `json:"password" validate:"required,min=8"`
	Name     string `json:"name" validate:"required"`
}

type authResponse struct {
	AccessToken  string `json:"accessToken"`
	RefreshToken string `json:"refreshToken"`
	UserID       string `json:"userId"`
	Email        string `json:"email"`
	Name         string `json:"name"`
}

// Register creates a new user account.
func (h *Handler) Register(w http.ResponseWriter, r *http.Request) {
	var req registerRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := validate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	var existing string
	err := h.db.QueryRow(r.Context(), `SELECT id FROM users WHERE email = $1`, req.Email).Scan(&existing)
	if err == nil {
		respond.Error(w, http.StatusConflict, "email_taken", "email already registered")
		return
	}
	if !errors.Is(err, pgx.ErrNoRows) {
		respond.InternalError(w, err, h.log)
		return
	}

	hash, err := auth.HashPassword(req.Password)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	userID := id.New()
	if _, err = h.db.Exec(r.Context(),
		`INSERT INTO users (id, email, name, password_hash) VALUES ($1, $2, $3, $4)`,
		userID, req.Email, req.Name, hash,
	); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	resp, err := h.issueTokens(r.Context(), r, userID, req.Email)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}
	resp.Name = req.Name

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		EventType: "user.registered",
		ActorType: "USER",
		ActorID:   userID,
		IPAddress: r.RemoteAddr,
		UserAgent: r.UserAgent(),
		Payload:   map[string]any{"email": req.Email},
	})

	respond.JSON(w, http.StatusCreated, resp)
}

type loginRequest struct {
	Email    string `json:"email" validate:"required,email"`
	Password string `json:"password" validate:"required"`
}

// Login authenticates a user and issues tokens.
func (h *Handler) Login(w http.ResponseWriter, r *http.Request) {
	var req loginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := validate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	var userID, passwordHash, name string
	err := h.db.QueryRow(r.Context(),
		`SELECT id, password_hash, name FROM users WHERE email = $1`, req.Email,
	).Scan(&userID, &passwordHash, &name)
	if errors.Is(err, pgx.ErrNoRows) || err != nil {
		respond.Error(w, http.StatusUnauthorized, "invalid_credentials", "invalid email or password")
		return
	}

	if err := auth.VerifyPassword(req.Password, passwordHash); err != nil {
		respond.Error(w, http.StatusUnauthorized, "invalid_credentials", "invalid email or password")
		return
	}

	resp, err := h.issueTokens(r.Context(), r, userID, req.Email)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}
	resp.Name = name

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		EventType: "user.login",
		ActorType: "USER",
		ActorID:   userID,
		IPAddress: r.RemoteAddr,
		UserAgent: r.UserAgent(),
	})

	respond.JSON(w, http.StatusOK, resp)
}

type refreshRequest struct {
	RefreshToken string `json:"refreshToken" validate:"required"`
}

// Refresh verifies a refresh token and issues a new access token.
func (h *Handler) Refresh(w http.ResponseWriter, r *http.Request) {
	var req refreshRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := validate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	tokenHash := hashToken(req.RefreshToken)
	var userID, email string
	err := h.db.QueryRow(r.Context(),
		`SELECT rt.user_id, u.email FROM refresh_tokens rt
		 JOIN users u ON u.id = rt.user_id
		 WHERE rt.token_hash = $1 AND rt.revoked_at IS NULL AND rt.expires_at > NOW()`,
		tokenHash,
	).Scan(&userID, &email)
	if err != nil {
		respond.Error(w, http.StatusUnauthorized, "invalid_token", "invalid or expired refresh token")
		return
	}

	accessToken, err := auth.SignAccessToken(userID, email, "", "")
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	respond.JSON(w, http.StatusOK, map[string]string{"accessToken": accessToken})
}

// Logout revokes the current refresh token.
func (h *Handler) Logout(w http.ResponseWriter, r *http.Request) {
	var req refreshRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	tokenHash := hashToken(req.RefreshToken)
	_, _ = h.db.Exec(r.Context(),
		`UPDATE refresh_tokens SET revoked_at=NOW() WHERE token_hash=$1`, tokenHash)
	w.WriteHeader(http.StatusNoContent)
}

// Me returns the current authenticated user.
func (h *Handler) Me(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	var userID, email, name, avatarURL string
	err := h.db.QueryRow(r.Context(),
		`SELECT id, email, name, avatar_url FROM users WHERE id = $1`, claims.UserID,
	).Scan(&userID, &email, &name, &avatarURL)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}
	respond.JSON(w, http.StatusOK, map[string]any{
		"id": userID, "email": email, "name": name, "avatarUrl": avatarURL,
	})
}

func (h *Handler) issueTokens(ctx context.Context, r *http.Request, userID, email string) (*authResponse, error) {
	raw, expiresAt, err := auth.IssueRefreshToken(userID)
	if err != nil {
		return nil, err
	}
	tokenHash := hashToken(raw)
	if _, err = h.db.Exec(ctx,
		`INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at) VALUES ($1,$2,$3,$4)`,
		id.New(), userID, tokenHash, expiresAt,
	); err != nil {
		return nil, err
	}
	accessToken, err := auth.SignAccessToken(userID, email, "", "")
	if err != nil {
		return nil, err
	}
	return &authResponse{
		AccessToken:  accessToken,
		RefreshToken: raw,
		UserID:       userID,
		Email:        email,
	}, nil
}

func hashToken(raw string) string {
	sum := sha256.Sum256([]byte(raw))
	return hex.EncodeToString(sum[:])
}
