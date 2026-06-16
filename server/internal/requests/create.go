package requests

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/notifications"
	redisPkg "github.com/nod/server/internal/redis"
	"github.com/nod/server/internal/ws"
	"github.com/nod/server/pkg/id"
)

// Create stores a new permission request, fans out to mobile WS connections,
// and sends push notifications to the workspace's device tokens.
func (s *requestService) Create(ctx context.Context, req CreateRequest) (*PermissionRequest, error) {
	// Check nonce replay.
	if req.InputHash != "" {
		fresh, err := redisPkg.CheckAndSetNonce(ctx, s.rdb, req.InputHash, 2*time.Minute)
		if err != nil || !fresh {
			return nil, fmt.Errorf("nonce already seen or error checking nonce: %w", err)
		}
	}

	requestID := req.ID
	if requestID == "" {
		requestID = id.New()
	}

	inputJSON, err := json.Marshal(req.Input)
	if err != nil {
		return nil, fmt.Errorf("marshal input: %w", err)
	}

	if _, err := s.db.Exec(ctx, `
		INSERT INTO permission_requests (
			id, session_id, workspace_id, machine_id, tool, input, input_hash,
			description, agent_signature, timeout_ms, seq, session_seq, expires_at
		) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)`,
		requestID, req.SessionID, req.WorkspaceID, req.MachineID,
		req.Tool, inputJSON, req.InputHash,
		req.Description, req.Signature,
		req.TimeoutMs, req.Seq, req.SessionSeq, req.ExpiresAt,
	); err != nil {
		return nil, fmt.Errorf("insert permission request: %w", err)
	}

	pr, err := s.Get(ctx, requestID)
	if err != nil {
		return nil, err
	}

	// Fan out to mobile WS clients.
	s.hub.FanoutRequest(req.WorkspaceID, &ws.PermissionRequest{
		RequestID:       requestID,
		SessionID:       req.SessionID,
		MachineID:       req.MachineID,
		MachineName:     req.MachineName,
		WorkspaceID:     req.WorkspaceID,
		Tool:            req.Tool,
		Input:           req.Input,
		Description:     req.Description,
		ExpiresAt:       req.ExpiresAt.Format(time.RFC3339),
		AgentSignature:  req.Signature,
	}, nil)

	// Send push notifications to workspace device tokens.
	go func() {
		bgCtx := context.Background()
		rows, err := s.db.Query(bgCtx,
			`SELECT dt.token FROM device_tokens dt
			 JOIN org_members om ON om.user_id=dt.user_id
			 JOIN workspaces w ON w.org_id=om.org_id
			 WHERE w.id=$1`, req.WorkspaceID,
		)
		if err != nil {
			s.log.Warn().Err(err).Msg("failed to fetch device tokens for push notification")
			return
		}
		defer rows.Close()

		var tokens []string
		for rows.Next() {
			var token string
			if err := rows.Scan(&token); err == nil {
				tokens = append(tokens, token)
			}
		}

		if len(tokens) == 0 {
			return
		}

		notif := &notifications.ApprovalRequestNotif{
			RequestID:   requestID,
			SessionID:   req.SessionID,
			MachineID:   req.MachineID,
			MachineName: req.MachineName,
			Tool:        req.Tool,
			Description: req.Description,
			ExpiresAt:   req.ExpiresAt.Format(time.RFC3339),
		}
		if err := s.notifSvc.SendApprovalRequest(bgCtx, notif, tokens); err != nil {
			s.log.Warn().Err(err).Msg("push notification failed")
		}
	}()

	_ = s.audit.Emit(ctx, audit.AuditEvent{
		WorkspaceID: req.WorkspaceID,
		SessionID:   req.SessionID,
		RequestID:   requestID,
		EventType:   "request.created",
		ActorType:   "MACHINE",
		ActorID:     req.MachineID,
	})

	return pr, nil
}
