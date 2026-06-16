package sessions

import (
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/rs/zerolog"

	"github.com/nod/server/internal/respond"
	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/auth"
	svcSessions "github.com/nod/server/internal/sessions"
)

// Handler handles session endpoints.
type Handler struct {
	db    *pgxpool.Pool
	svc   svcSessions.SessionService
	audit audit.AuditService
	log   zerolog.Logger
}

// NewHandler creates a Handler.
func NewHandler(db *pgxpool.Pool, svc svcSessions.SessionService, auditSvc audit.AuditService, log zerolog.Logger) *Handler {
	return &Handler{db: db, svc: svc, audit: auditSvc, log: log}
}

// List returns all sessions in a workspace.
func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	wsID := chi.URLParam(r, "wsId")

	list, err := h.svc.List(r.Context(), wsID)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	out := make([]map[string]any, 0, len(list))
	for _, s := range list {
		out = append(out, sessionToMap(s))
	}
	respond.JSON(w, http.StatusOK, map[string]any{"sessions": out})
}

// Get returns a single session.
func (h *Handler) Get(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	sessionID := chi.URLParam(r, "sessionId")

	s, err := h.svc.Get(r.Context(), sessionID)
	if err != nil {
		respond.NotFound(w)
		return
	}

	respond.JSON(w, http.StatusOK, sessionToMap(s))
}

// End force-ends a session (ADMIN only).
func (h *Handler) End(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	sessionID := chi.URLParam(r, "sessionId")

	if err := h.svc.End(r.Context(), sessionID); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		SessionID: sessionID,
		EventType: "session.force_ended",
		ActorType: "USER",
		ActorID:   claims.UserID,
	})

	w.WriteHeader(http.StatusNoContent)
}

func sessionToMap(s *svcSessions.Session) map[string]any {
	return map[string]any{
		"id":          s.ID,
		"machineId":   s.MachineID,
		"workspaceId": s.WorkspaceID,
		"status":      s.Status,
		"agentType":   s.AgentType,
		"startedAt":   s.StartedAt,
		"endedAt":     s.EndedAt,
	}
}
