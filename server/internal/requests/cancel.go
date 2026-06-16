package requests

import (
	"context"
	"fmt"

	"github.com/nod/server/internal/audit"
)

// Cancel cancels all PENDING requests for the given session.
// Called when a session ends or is marked zombie.
func (s *requestService) Cancel(ctx context.Context, sessionID string) error {
	rows, err := s.db.Query(ctx,
		`UPDATE permission_requests SET status='CANCELLED'
		 WHERE session_id=$1 AND status='PENDING'
		 RETURNING id, workspace_id, machine_id`,
		sessionID,
	)
	if err != nil {
		return fmt.Errorf("cancel requests for session %s: %w", sessionID, err)
	}
	defer rows.Close()

	for rows.Next() {
		var requestID, workspaceID, machineID string
		if err := rows.Scan(&requestID, &workspaceID, &machineID); err != nil {
			s.log.Warn().Err(err).Msg("scan cancelled request")
			continue
		}

		_ = s.audit.Emit(ctx, audit.AuditEvent{
			WorkspaceID: workspaceID,
			SessionID:   sessionID,
			RequestID:   requestID,
			EventType:   "request.cancelled",
			ActorType:   "SYSTEM",
			ActorID:     machineID,
		})
	}

	return rows.Err()
}
