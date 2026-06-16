package requests

import (
	"context"
	"fmt"

	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/ws"
)

// ExpirePending sets status=EXPIRED for all PENDING requests past their expires_at.
// Notifies the relevant agent WebSocket connections and emits audit events.
func (s *requestService) ExpirePending(ctx context.Context) error {
	rows, err := s.db.Query(ctx,
		`UPDATE permission_requests SET status='EXPIRED'
		 WHERE status='PENDING' AND expires_at < NOW()
		 RETURNING id, session_id, workspace_id, machine_id`,
	)
	if err != nil {
		return fmt.Errorf("expire pending requests: %w", err)
	}
	defer rows.Close()

	for rows.Next() {
		var requestID, sessionID, workspaceID, machineID string
		if err := rows.Scan(&requestID, &sessionID, &workspaceID, &machineID); err != nil {
			s.log.Warn().Err(err).Msg("scan expired request")
			continue
		}

		// Notify agent.
		_ = s.hub.SendToAgent(sessionID, ws.ServerExpiredMsg{
			Type:      "expired",
			RequestID: requestID,
		})

		// Emit audit event.
		_ = s.audit.Emit(ctx, audit.AuditEvent{
			WorkspaceID: workspaceID,
			SessionID:   sessionID,
			RequestID:   requestID,
			EventType:   "request.expired",
			ActorType:   "SYSTEM",
			ActorID:     machineID,
		})
	}

	return rows.Err()
}
