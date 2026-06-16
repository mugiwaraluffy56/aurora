package requests

import (
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
	"github.com/rs/zerolog"

	"github.com/nod/server/internal/respond"
	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/auth"
	svcRequests "github.com/nod/server/internal/requests"
)

// Handler handles permission request endpoints.
type Handler struct {
	db    *pgxpool.Pool
	rdb   *redis.Client
	svc   svcRequests.RequestService
	audit audit.AuditService
	log   zerolog.Logger
}

// NewHandler creates a Handler.
func NewHandler(db *pgxpool.Pool, rdb *redis.Client, svc svcRequests.RequestService, auditSvc audit.AuditService, log zerolog.Logger) *Handler {
	return &Handler{db: db, rdb: rdb, svc: svc, audit: auditSvc, log: log}
}

// List returns permission requests in a workspace with cursor-based pagination.
func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	wsID := chi.URLParam(r, "wsId")

	q := r.URL.Query()
	filter := svcRequests.ListFilter{
		WorkspaceID: wsID,
		Status:      q.Get("status"),
		Cursor:      q.Get("cursor"),
		Limit:       100,
	}

	list, nextCursor, err := h.svc.List(r.Context(), filter)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	out := make([]map[string]any, 0, len(list))
	for _, req := range list {
		out = append(out, requestToMap(req))
	}
	respond.JSON(w, http.StatusOK, map[string]any{
		"requests":   out,
		"nextCursor": nextCursor,
	})
}

// Get returns a single permission request.
func (h *Handler) Get(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	requestID := chi.URLParam(r, "requestId")

	req, err := h.svc.Get(r.Context(), requestID)
	if err != nil {
		respond.NotFound(w)
		return
	}

	respond.JSON(w, http.StatusOK, requestToMap(req))
}

func requestToMap(req *svcRequests.PermissionRequest) map[string]any {
	return map[string]any{
		"id":                req.ID,
		"sessionId":         req.SessionID,
		"workspaceId":       req.WorkspaceID,
		"machineId":         req.MachineID,
		"tool":              req.Tool,
		"input":             req.Input,
		"description":       req.Description,
		"status":            req.Status,
		"autoDecided":       req.AutoDecided,
		"agentSignature":    req.AgentSignature,
		"signatureVerified": req.SignatureVerified,
		"expiresAt":         req.ExpiresAt,
		"decidedAt":         req.DecidedAt,
		"createdAt":         req.CreatedAt,
	}
}
