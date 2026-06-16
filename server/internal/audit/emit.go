package audit

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/nod/server/pkg/id"
)

// Emit inserts an audit event into the database with a hash chain link.
func (s *service) Emit(ctx context.Context, event AuditEvent) error {
	if event.ID == "" {
		event.ID = id.New()
	}
	if event.ActorType == "" {
		event.ActorType = "SYSTEM"
	}

	prevHash, err := GetPreviousHash(ctx, s.db, event.OrgID)
	if err != nil {
		s.log.Warn().Err(err).Str("org_id", event.OrgID).Msg("could not fetch previous audit hash")
		prevHash = ""
	}

	checksum := ComputeChecksum(&event)

	payloadJSON, err := json.Marshal(event.Payload)
	if err != nil {
		return fmt.Errorf("marshal audit payload: %w", err)
	}

	wsID := ptrOrNil(event.WorkspaceID)
	sessID := ptrOrNil(event.SessionID)
	reqID := ptrOrNil(event.RequestID)
	actorID := ptrOrNil(event.ActorID)
	deviceID := ptrOrNil(event.DeviceID)
	ipAddr := ptrOrNil(event.IPAddress)
	ua := ptrOrNil(event.UserAgent)

	_, err = s.db.Exec(ctx, `
		INSERT INTO audit_events (
			id, org_id, workspace_id, session_id, request_id,
			event_type, actor_type, actor_id, device_id, ip_address, user_agent,
			payload, previous_hash, checksum
		) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)`,
		event.ID, event.OrgID, wsID, sessID, reqID,
		event.EventType, event.ActorType, actorID, deviceID, ipAddr, ua,
		payloadJSON, prevHash, checksum,
	)
	if err != nil {
		return fmt.Errorf("insert audit event: %w", err)
	}
	return nil
}

func ptrOrNil(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}
