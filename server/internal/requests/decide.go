package requests

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"

	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/ws"
	"github.com/nod/server/pkg/id"
)

// Decide records the approval decision for a PENDING request.
// Uses an optimistic lock (UPDATE WHERE status='PENDING') to prevent double-decisions.
func (s *requestService) Decide(ctx context.Context, requestID, userID, deviceID string, approved bool) (*PermissionRequest, error) {
	status := "APPROVED"
	if !approved {
		status = "DENIED"
	}

	var updatedID string
	err := s.db.QueryRow(ctx,
		`UPDATE permission_requests SET status=$2, decided_at=NOW()
		 WHERE id=$1 AND status='PENDING' RETURNING id`,
		requestID, status,
	).Scan(&updatedID)
	if err != nil {
		// Already decided — return current state.
		pr, getErr := s.Get(ctx, requestID)
		if getErr != nil {
			return nil, fmt.Errorf("request not found: %w", getErr)
		}
		return pr, nil
	}

	// Record approval.
	approvalID := id.New()
	var deviceIDPtr *string
	if deviceID != "" {
		deviceIDPtr = &deviceID
	}
	_, _ = s.db.Exec(ctx,
		`INSERT INTO request_approvals (id, request_id, decided_by, device_id, approved, decided_at)
		 VALUES ($1,$2,$3,$4,$5,NOW())`,
		approvalID, requestID, userID, deviceIDPtr, approved,
	)

	pr, err := s.Get(ctx, requestID)
	if err != nil {
		return nil, err
	}

	// Notify agent via hub.
	_ = s.hub.SendToAgent(pr.SessionID, ws.ServerDecisionMsg{
		Type:      "decision",
		RequestID: requestID,
		Approved:  approved,
	})

	// Broadcast to mobile clients.
	s.hub.BroadcastToWorkspace(pr.WorkspaceID, ws.ServerDecidedMsg{
		Type:      "decided",
		RequestID: requestID,
		Status:    status,
	}, "")

	// Emit audit event.
	_ = s.audit.Emit(ctx, audit.AuditEvent{
		WorkspaceID: pr.WorkspaceID,
		SessionID:   pr.SessionID,
		RequestID:   requestID,
		EventType:   "request." + map[bool]string{true: "approved", false: "denied"}[approved],
		ActorType:   "USER",
		ActorID:     userID,
		DeviceID:    deviceID,
	})

	return pr, nil
}

// Get returns a single permission request by ID.
func (s *requestService) Get(ctx context.Context, requestID string) (*PermissionRequest, error) {
	pr := &PermissionRequest{}
	var inputJSON []byte
	err := s.db.QueryRow(ctx,
		`SELECT id, session_id, workspace_id, machine_id, tool, input, description, status,
		        auto_decided, agent_signature, signature_verified, timeout_ms, seq, session_seq,
		        expires_at, decided_at, created_at
		 FROM permission_requests WHERE id=$1`, requestID,
	).Scan(
		&pr.ID, &pr.SessionID, &pr.WorkspaceID, &pr.MachineID, &pr.Tool, &inputJSON,
		&pr.Description, &pr.Status, &pr.AutoDecided, &pr.AgentSignature, &pr.SignatureVerified,
		&pr.TimeoutMs, &pr.Seq, &pr.SessionSeq, &pr.ExpiresAt, &pr.DecidedAt, &pr.CreatedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("get request %s: %w", requestID, err)
	}
	var input any
	_ = json.Unmarshal(inputJSON, &input)
	pr.Input = input
	return pr, nil
}

// List returns permission requests matching the filter with cursor-based pagination.
func (s *requestService) List(ctx context.Context, filter ListFilter) ([]*PermissionRequest, string, error) {
	limit := filter.Limit
	if limit <= 0 {
		limit = 100
	}

	args := []any{filter.WorkspaceID}
	whereParts := []string{"workspace_id=$1"}
	argIdx := 2

	if filter.Status != "" {
		whereParts = append(whereParts, fmt.Sprintf("status=$%d", argIdx))
		args = append(args, filter.Status)
		argIdx++
	}
	if filter.Cursor != "" {
		whereParts = append(whereParts, fmt.Sprintf("created_at < $%d", argIdx))
		args = append(args, filter.Cursor)
		argIdx++
	}

	query := fmt.Sprintf(
		`SELECT id, session_id, workspace_id, machine_id, tool, input, description, status,
		        auto_decided, agent_signature, signature_verified, timeout_ms, seq, session_seq,
		        expires_at, decided_at, created_at
		 FROM permission_requests WHERE %s ORDER BY created_at DESC LIMIT %d`,
		strings.Join(whereParts, " AND "), limit+1,
	)

	rows, err := s.db.Query(ctx, query, args...)
	if err != nil {
		return nil, "", fmt.Errorf("list requests: %w", err)
	}
	defer rows.Close()

	var prs []*PermissionRequest
	for rows.Next() {
		pr := &PermissionRequest{}
		var inputJSON []byte
		if err := rows.Scan(
			&pr.ID, &pr.SessionID, &pr.WorkspaceID, &pr.MachineID, &pr.Tool, &inputJSON,
			&pr.Description, &pr.Status, &pr.AutoDecided, &pr.AgentSignature, &pr.SignatureVerified,
			&pr.TimeoutMs, &pr.Seq, &pr.SessionSeq, &pr.ExpiresAt, &pr.DecidedAt, &pr.CreatedAt,
		); err != nil {
			return nil, "", fmt.Errorf("scan request: %w", err)
		}
		var input any
		_ = json.Unmarshal(inputJSON, &input)
		pr.Input = input
		prs = append(prs, pr)
	}

	var nextCursor string
	if len(prs) > limit {
		nextCursor = prs[limit-1].CreatedAt.Format("2006-01-02T15:04:05.999999999Z07:00")
		prs = prs[:limit]
	}

	return prs, nextCursor, nil
}
