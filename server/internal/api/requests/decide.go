package requests

import (
	"encoding/json"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-playground/validator/v10"

	"github.com/nod/server/internal/respond"
	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/auth"
	redisPkg "github.com/nod/server/internal/redis"
)

var decideValidate = validator.New()

type decideRequest struct {
	Approved       bool   `json:"approved"`
	IdempotencyKey string `json:"idempotencyKey" validate:"required"`
}

// Decide approves or denies a permission request.
func (h *Handler) Decide(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	requestID := chi.URLParam(r, "requestId")

	var req decideRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := decideValidate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	// Check idempotency cache.
	iKey := redisPkg.IdempotencyKey(req.IdempotencyKey)
	if cached, err := h.rdb.Get(r.Context(), iKey).Result(); err == nil {
		// Return cached result.
		var result map[string]any
		if jsonErr := json.Unmarshal([]byte(cached), &result); jsonErr == nil {
			respond.JSON(w, http.StatusOK, result)
			return
		}
	}

	decided, err := h.svc.Decide(r.Context(), requestID, claims.UserID, "", req.Approved)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	result := map[string]any{
		"requestId": decided.ID,
		"status":    decided.Status,
		"approved":  req.Approved,
	}

	// Cache result under idempotency key for 24 hours.
	if raw, err := json.Marshal(result); err == nil {
		_ = h.rdb.Set(r.Context(), iKey, raw, 24*time.Hour).Err()
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		WorkspaceID: decided.WorkspaceID,
		SessionID:   decided.SessionID,
		RequestID:   decided.ID,
		EventType:   "request." + map[bool]string{true: "approved", false: "denied"}[req.Approved],
		ActorType:   "USER",
		ActorID:     claims.UserID,
		IPAddress:   r.RemoteAddr,
		UserAgent:   r.UserAgent(),
	})

	respond.JSON(w, http.StatusOK, result)
}
