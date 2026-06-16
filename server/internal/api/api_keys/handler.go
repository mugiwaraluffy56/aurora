package api_keys

import (
	"encoding/json"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-playground/validator/v10"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/rs/zerolog"

	"github.com/nod/server/internal/respond"
	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/auth"
	"github.com/nod/server/pkg/id"
)

var validate = validator.New()

// Handler handles API key endpoints.
type Handler struct {
	db    *pgxpool.Pool
	audit audit.AuditService
	log   zerolog.Logger
}

// NewHandler creates a Handler.
func NewHandler(db *pgxpool.Pool, auditSvc audit.AuditService, log zerolog.Logger) *Handler {
	return &Handler{db: db, audit: auditSvc, log: log}
}

type createKeyRequest struct {
	Name string `json:"name" validate:"required"`
}

// Create generates a new API key. The raw key is returned once and never stored.
func (h *Handler) Create(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	wsID := chi.URLParam(r, "wsId")

	var req createKeyRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := validate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	raw, hash, prefix, err := auth.GenerateAPIKey()
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	keyID := id.New()
	if _, err := h.db.Exec(r.Context(),
		`INSERT INTO api_keys (id, workspace_id, created_by, name, key_hash, key_prefix) VALUES ($1,$2,$3,$4,$5,$6)`,
		keyID, wsID, claims.UserID, req.Name, hash, prefix,
	); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		WorkspaceID: wsID,
		EventType:   "api_key.created",
		ActorType:   "USER",
		ActorID:     claims.UserID,
		Payload:     map[string]any{"key_id": keyID, "name": req.Name},
	})

	// Return raw key once — it will never be retrievable again.
	respond.JSON(w, http.StatusCreated, map[string]any{
		"id":     keyID,
		"name":   req.Name,
		"prefix": prefix,
		"key":    raw, // raw key, one-time display
	})
}

// List lists API keys for a workspace (prefix only, never hash).
func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	wsID := chi.URLParam(r, "wsId")

	rows, err := h.db.Query(r.Context(),
		`SELECT id, name, key_prefix, created_at, revoked_at FROM api_keys WHERE workspace_id=$1 ORDER BY created_at DESC`,
		wsID,
	)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}
	defer rows.Close()

	var keys []map[string]any
	for rows.Next() {
		var keyID, name, prefix string
		var createdAt interface{}
		var revokedAt interface{}
		if err := rows.Scan(&keyID, &name, &prefix, &createdAt, &revokedAt); err != nil {
			continue
		}
		keys = append(keys, map[string]any{
			"id": keyID, "name": name, "prefix": prefix,
			"createdAt": createdAt, "revokedAt": revokedAt,
		})
	}
	if keys == nil {
		keys = []map[string]any{}
	}
	respond.JSON(w, http.StatusOK, map[string]any{"apiKeys": keys})
}

// Revoke revokes an API key.
func (h *Handler) Revoke(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	keyID := chi.URLParam(r, "keyId")

	var wsID string
	if err := h.db.QueryRow(r.Context(),
		`UPDATE api_keys SET revoked_at=NOW() WHERE id=$1 AND revoked_at IS NULL RETURNING workspace_id`,
		keyID,
	).Scan(&wsID); err != nil {
		respond.NotFound(w)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		WorkspaceID: wsID,
		EventType:   "api_key.revoked",
		ActorType:   "USER",
		ActorID:     claims.UserID,
		Payload:     map[string]any{"key_id": keyID},
	})

	w.WriteHeader(http.StatusNoContent)
}
