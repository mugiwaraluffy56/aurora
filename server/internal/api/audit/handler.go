package audit

import (
	"fmt"
	"net/http"
	"strings"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/rs/zerolog"

	"github.com/nod/server/internal/respond"
	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/auth"
)

// Handler handles audit log endpoints.
type Handler struct {
	db    *pgxpool.Pool
	audit audit.AuditService
	log   zerolog.Logger
}

// NewHandler creates a Handler.
func NewHandler(db *pgxpool.Pool, auditSvc audit.AuditService, log zerolog.Logger) *Handler {
	return &Handler{db: db, audit: auditSvc, log: log}
}

// List returns audit events for an org with cursor-based pagination.
func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	orgID := chi.URLParam(r, "orgId")

	// Verify org membership.
	var role string
	if err := h.db.QueryRow(r.Context(),
		`SELECT role FROM org_members WHERE org_id=$1 AND user_id=$2`, orgID, claims.UserID,
	).Scan(&role); err != nil {
		respond.Forbidden(w)
		return
	}

	q := r.URL.Query()
	cursor := q.Get("cursor")
	from := q.Get("from")
	to := q.Get("to")
	eventTypesStr := q.Get("eventTypes")
	wsID := q.Get("workspaceId")

	// Build query with optional filters.
	args := []any{orgID}
	whereParts := []string{"org_id=$1"}
	argIdx := 2

	if from != "" {
		whereParts = append(whereParts, fmt.Sprintf("created_at >= $%d", argIdx))
		args = append(args, from)
		argIdx++
	}
	if to != "" {
		whereParts = append(whereParts, fmt.Sprintf("created_at <= $%d", argIdx))
		args = append(args, to)
		argIdx++
	}
	if wsID != "" {
		whereParts = append(whereParts, fmt.Sprintf("workspace_id=$%d", argIdx))
		args = append(args, wsID)
		argIdx++
	}
	if eventTypesStr != "" {
		eventTypes := strings.Split(eventTypesStr, ",")
		whereParts = append(whereParts, fmt.Sprintf("event_type=ANY($%d)", argIdx))
		args = append(args, eventTypes)
		argIdx++
	}
	if cursor != "" {
		whereParts = append(whereParts, fmt.Sprintf("created_at < $%d", argIdx))
		args = append(args, cursor)
		argIdx++
	}

	query := "SELECT id, workspace_id, session_id, request_id, event_type, actor_type, actor_id, device_id, ip_address, payload, created_at FROM audit_events WHERE " +
		strings.Join(whereParts, " AND ") +
		" ORDER BY created_at DESC LIMIT 101"

	rows, err := h.db.Query(r.Context(), query, args...)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}
	defer rows.Close()

	var events []map[string]any
	for rows.Next() {
		var evID string
		var workspaceID, sessionID, requestID, eventType, actorType, actorID, deviceID, ipAddress *string
		var payload []byte
		var createdAt interface{}
		if err := rows.Scan(&evID, &workspaceID, &sessionID, &requestID, &eventType, &actorType, &actorID, &deviceID, &ipAddress, &payload, &createdAt); err != nil {
			continue
		}
		events = append(events, map[string]any{
			"id":          evID,
			"orgId":       orgID,
			"workspaceId": workspaceID,
			"sessionId":   sessionID,
			"requestId":   requestID,
			"eventType":   eventType,
			"actorType":   actorType,
			"actorId":     actorID,
			"deviceId":    deviceID,
			"ipAddress":   ipAddress,
			"payload":     payload,
			"createdAt":   createdAt,
		})
	}

	var nextCursor string
	if len(events) > 100 {
		events = events[:100]
		if last, ok := events[99]["createdAt"]; ok {
			nextCursor = fmt.Sprintf("%v", last)
		}
	}

	if events == nil {
		events = []map[string]any{}
	}

	respond.JSON(w, http.StatusOK, map[string]any{
		"events":     events,
		"nextCursor": nextCursor,
	})
}
