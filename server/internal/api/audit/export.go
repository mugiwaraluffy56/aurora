package audit

import (
	"encoding/csv"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/nod/server/internal/respond"
	"github.com/nod/server/internal/auth"
)

// Export streams audit events as CSV, JSON, or NDJSON.
func (h *Handler) Export(w http.ResponseWriter, r *http.Request) {
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
	format := q.Get("format")
	if format == "" {
		format = "json"
	}
	from := q.Get("from")
	to := q.Get("to")

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

	query := "SELECT id, workspace_id, session_id, request_id, event_type, actor_type, actor_id, ip_address, payload, created_at FROM audit_events WHERE " +
		strings.Join(whereParts, " AND ") +
		" ORDER BY created_at DESC"

	rows, err := h.db.Query(r.Context(), query, args...)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}
	defer rows.Close()

	ts := time.Now().UTC().Format("20060102-150405")
	filename := fmt.Sprintf("audit-%s-%s.%s", orgID, ts, format)
	w.Header().Set("Content-Disposition", "attachment; filename="+filename)

	switch format {
	case "csv":
		w.Header().Set("Content-Type", "text/csv")
		cw := csv.NewWriter(w)
		// Write header row.
		_ = cw.Write([]string{"id", "workspace_id", "session_id", "request_id", "event_type", "actor_type", "actor_id", "ip_address", "payload", "created_at"})
		for rows.Next() {
			var evID, eventType, actorType string
			var workspaceID, sessionID, requestID, actorID, ipAddress *string
			var payload []byte
			var createdAt interface{}
			if err := rows.Scan(&evID, &workspaceID, &sessionID, &requestID, &eventType, &actorType, &actorID, &ipAddress, &payload, &createdAt); err != nil {
				continue
			}
			_ = cw.Write([]string{
				evID,
				ptrStr(workspaceID),
				ptrStr(sessionID),
				ptrStr(requestID),
				eventType,
				actorType,
				ptrStr(actorID),
				ptrStr(ipAddress),
				string(payload),
				fmt.Sprintf("%v", createdAt),
			})
		}
		cw.Flush()

	case "ndjson":
		w.Header().Set("Content-Type", "application/x-ndjson")
		for rows.Next() {
			var evID, eventType, actorType string
			var workspaceID, sessionID, requestID, actorID, ipAddress *string
			var payload []byte
			var createdAt interface{}
			if err := rows.Scan(&evID, &workspaceID, &sessionID, &requestID, &eventType, &actorType, &actorID, &ipAddress, &payload, &createdAt); err != nil {
				continue
			}
			var payloadObj any
			_ = json.Unmarshal(payload, &payloadObj)
			line, _ := json.Marshal(map[string]any{
				"id":          evID,
				"orgId":       orgID,
				"workspaceId": workspaceID,
				"sessionId":   sessionID,
				"requestId":   requestID,
				"eventType":   eventType,
				"actorType":   actorType,
				"actorId":     actorID,
				"ipAddress":   ipAddress,
				"payload":     payloadObj,
				"createdAt":   createdAt,
			})
			_, _ = w.Write(line)
			_, _ = w.Write([]byte("\n"))
		}

	default: // json
		w.Header().Set("Content-Type", "application/json")
		var events []map[string]any
		for rows.Next() {
			var evID, eventType, actorType string
			var workspaceID, sessionID, requestID, actorID, ipAddress *string
			var payload []byte
			var createdAt interface{}
			if err := rows.Scan(&evID, &workspaceID, &sessionID, &requestID, &eventType, &actorType, &actorID, &ipAddress, &payload, &createdAt); err != nil {
				continue
			}
			var payloadObj any
			_ = json.Unmarshal(payload, &payloadObj)
			events = append(events, map[string]any{
				"id":          evID,
				"orgId":       orgID,
				"workspaceId": workspaceID,
				"sessionId":   sessionID,
				"requestId":   requestID,
				"eventType":   eventType,
				"actorType":   actorType,
				"actorId":     actorID,
				"ipAddress":   ipAddress,
				"payload":     payloadObj,
				"createdAt":   createdAt,
			})
		}
		if events == nil {
			events = []map[string]any{}
		}
		_ = json.NewEncoder(w).Encode(map[string]any{"events": events})
	}
}

func ptrStr(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}
