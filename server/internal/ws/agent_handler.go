package ws

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
	"github.com/rs/zerolog"
	"nhooyr.io/websocket"

	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/auth"
	redisPkg "github.com/nod/server/internal/redis"
	"github.com/nod/server/internal/rules"
	"github.com/nod/server/pkg/id"
)

const authTimeout = 5 * time.Second

// AgentHandler handles /ws/agent WebSocket connections.
type AgentHandler struct {
	hub     *Hub
	db      *pgxpool.Pool
	rdb     *redis.Client
	engine  rules.RulesEngine
	audit   audit.AuditService
	log     zerolog.Logger
}

// NewAgentHandler creates an AgentHandler.
func NewAgentHandler(hub *Hub, db *pgxpool.Pool, rdb *redis.Client, engine rules.RulesEngine, auditSvc audit.AuditService, log zerolog.Logger) *AgentHandler {
	return &AgentHandler{hub: hub, db: db, rdb: rdb, engine: engine, audit: auditSvc, log: log}
}

// sendDirect marshals msg and writes it synchronously to ws before the handler exits.
// Use for early-exit error paths where conn.Send (async) would race with ws.Close.
func sendDirect(ctx context.Context, ws *websocket.Conn, msg any) {
	raw, err := json.Marshal(msg)
	if err != nil {
		return
	}
	writeCtx, cancel := context.WithTimeout(ctx, 3*time.Second)
	defer cancel()
	_ = ws.Write(writeCtx, websocket.MessageText, raw)
}

// ServeHTTP upgrades the connection and handles the agent session.
func (h *AgentHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	ws, err := websocket.Accept(w, r, &websocket.AcceptOptions{
		InsecureSkipVerify: true,
	})
	if err != nil {
		h.log.Error().Err(err).Msg("agent ws upgrade failed")
		return
	}

	connID := uuid.NewString()
	conn := NewConn(connID, ws)

	// Detach from HTTP request context: r.Context() may carry a short deadline
	// from middleware. WebSocket connections are long-lived and must not inherit it.
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Auth timeout.
	authCtx, authCancel := context.WithTimeout(ctx, authTimeout)

	// Read first message — must be AgentAuthMsg.
	_, rawMsg, err := ws.Read(authCtx)
	authCancel()
	if err != nil {
		h.log.Warn().Err(err).Msg("agent: failed to read auth message")
		_ = ws.Close(websocket.StatusPolicyViolation, "auth timeout")
		return
	}

	var baseMsg struct {
		Type string `json:"type"`
	}
	if err := json.Unmarshal(rawMsg, &baseMsg); err != nil {
		sendDirect(ctx, ws, ServerErrorMsg{Type: "error", Code: "invalid_auth", Message: "first message must be auth"})
		_ = ws.Close(websocket.StatusPolicyViolation, "invalid auth")
		return
	}

	// Handle reconnect: client has a session_id from a previous connection.
	// We don't store raw tokens so we can't re-issue auth — tell agent to re-auth.
	if baseMsg.Type == "reconnect" {
		h.log.Debug().Msg("agent: reconnect received, sending session_expired")
		sendDirect(ctx, ws, ServerErrorMsg{Type: "error", Code: "session_expired", Message: "session expired, re-authenticate"})
		_ = ws.Close(websocket.StatusNormalClosure, "session expired")
		return
	}

	if baseMsg.Type != "auth" {
		sendDirect(ctx, ws, ServerErrorMsg{Type: "error", Code: "invalid_auth", Message: "first message must be auth"})
		_ = ws.Close(websocket.StatusPolicyViolation, "invalid auth")
		return
	}

	var authMsg AgentAuthMsg
	if err := json.Unmarshal(rawMsg, &authMsg); err != nil {
		sendDirect(ctx, ws, ServerErrorMsg{Type: "error", Code: "invalid_auth", Message: "malformed auth message"})
		_ = ws.Close(websocket.StatusPolicyViolation, "malformed auth")
		return
	}

	claims, err := auth.VerifyMachineToken(authMsg.MachineToken)
	if err != nil {
		h.log.Warn().Err(err).Msg("agent: invalid machine token")
		sendDirect(ctx, ws, ServerErrorMsg{Type: "error", Code: "invalid_token", Message: "invalid machine token"})
		_ = ws.Close(websocket.StatusPolicyViolation, "invalid token")
		return
	}

	// Check blocklist.
	if claims.ID != "" {
		blocked, err := redisPkg.IsBlocked(ctx, h.rdb, claims.ID)
		if err == nil && blocked {
			sendDirect(ctx, ws, ServerRevokedMsg{Type: "revoked", Reason: "token revoked"})
			_ = ws.Close(websocket.StatusPolicyViolation, "revoked")
			return
		}
	}

	// Verify fingerprint matches DB.
	var dbFingerprint, workspaceID, machineName, machineType string
	err = h.db.QueryRow(ctx,
		`SELECT fingerprint, workspace_id, name, machine_type FROM machines WHERE id = $1 AND status = 'ACTIVE'`,
		claims.MachineID,
	).Scan(&dbFingerprint, &workspaceID, &machineName, &machineType)
	if err != nil {
		h.log.Warn().Err(err).Str("machine_id", claims.MachineID).Msg("agent: machine not found")
		sendDirect(ctx, ws, ServerErrorMsg{Type: "error", Code: "machine_not_found", Message: "machine not found or revoked"})
		_ = ws.Close(websocket.StatusPolicyViolation, "machine not found")
		return
	}

	if dbFingerprint != authMsg.Fingerprint || claims.Fingerprint != authMsg.Fingerprint {
		h.log.Warn().Str("db_fp", dbFingerprint).Str("msg_fp", authMsg.Fingerprint).Str("claims_fp", claims.Fingerprint).Msg("agent: fingerprint mismatch")
		sendDirect(ctx, ws, ServerErrorMsg{Type: "error", Code: "fingerprint_mismatch", Message: "fingerprint mismatch"})
		_ = ws.Close(websocket.StatusPolicyViolation, "fingerprint mismatch")
		return
	}

	// Create session.
	sessionID := id.New()
	_, err = h.db.Exec(ctx,
		`INSERT INTO sessions (id, machine_id, workspace_id, connection_id, agent_type) VALUES ($1, $2, $3, $4, $5)`,
		sessionID, claims.MachineID, workspaceID, connID, authMsg.SessionMeta.AgentType,
	)
	if err != nil {
		h.log.Error().Err(err).Msg("create session failed")
		sendDirect(ctx, ws, ServerErrorMsg{Type: "error", Code: "server_error", Message: "could not create session"})
		_ = ws.Close(websocket.StatusInternalError, "server error")
		return
	}

	h.hub.RegisterAgent(sessionID, conn)
	go conn.WritePump(ctx)
	defer ws.Close(websocket.StatusNormalClosure, "session ended")
	defer func() {
		h.hub.UnregisterAgent(sessionID)
		_, _ = h.db.Exec(context.Background(),
			`UPDATE sessions SET status='ENDED', ended_at=NOW() WHERE id=$1`, sessionID)
		_ = redisPkg.RemovePresence(context.Background(), h.rdb, workspaceID, claims.MachineID)
		h.hub.BroadcastToWorkspace(workspaceID, ServerPresenceMsg{
			Type:      "presence",
			MachineID: claims.MachineID,
			Event:     "disconnected",
			SessionID: sessionID,
		}, "")
	}()

	// Emit presence.
	_ = redisPkg.SetPresence(ctx, h.rdb, workspaceID, claims.MachineID, redisPkg.PresenceData{
		MachineID:   claims.MachineID,
		MachineName: machineName,
		SessionID:   sessionID,
		ConnectedAt: time.Now(),
		LastSeen:    time.Now(),
	})

	// Broadcast presence connected to all mobile clients.
	h.hub.BroadcastToWorkspace(workspaceID, ServerPresenceMsg{
		Type:      "presence",
		MachineID: claims.MachineID,
		Event:     "connected",
		SessionID: sessionID,
	}, "")

	// Send auth OK.
	if err := conn.Send(ServerAuthOKMsg{Type: "authOk", SessionID: sessionID, ConnectionID: connID}); err != nil {
		return
	}

	// Update machine last seen.
	_, _ = h.db.Exec(ctx, `UPDATE machines SET last_seen_at = NOW() WHERE id = $1`, claims.MachineID)

	// Emit audit event.
	_ = h.audit.Emit(ctx, audit.AuditEvent{
		OrgID:       "",
		WorkspaceID: workspaceID,
		SessionID:   sessionID,
		EventType:   "session.started",
		ActorType:   "MACHINE",
		ActorID:     claims.MachineID,
	})

	// Read loop.
	for {
		_, raw, err := ws.Read(ctx)
		if err != nil {
			h.log.Warn().Err(err).Str("session_id", sessionID).Msg("agent: read loop exited")
			return
		}

		// Update heartbeat.
		_, _ = h.db.Exec(ctx, `UPDATE sessions SET last_heartbeat=NOW() WHERE id=$1`, sessionID)

		var msg struct {
			Type string `json:"type"`
		}
		if err := json.Unmarshal(raw, &msg); err != nil {
			continue
		}

		switch msg.Type {
		case "ping":
			var ping AgentPingMsg
			if err := json.Unmarshal(raw, &ping); err == nil {
				_ = conn.Send(ServerPongMsg{Type: "pong", Ts: ping.Ts, ServerTs: time.Now().UnixMilli()})
			}

		case "request":
			var req AgentRequestMsg
			if err := json.Unmarshal(raw, &req); err != nil {
				_ = conn.Send(ServerErrorMsg{Type: "error", Code: "invalid_request", Message: "malformed request"})
				continue
			}
			h.handleRequest(ctx, conn, sessionID, workspaceID, claims.MachineID, machineName, machineType, &req)

		case "cancel":
			var cancel AgentCancelMsg
			if err := json.Unmarshal(raw, &cancel); err == nil {
				_, _ = h.db.Exec(ctx,
					`UPDATE permission_requests SET status='CANCELLED' WHERE id=$1 AND session_id=$2 AND status='PENDING'`,
					cancel.RequestID, sessionID)
			}

		case "reconnect":
			// Acknowledge reconnect — inflight requests will be re-fanned by scheduler on next tick.
		}
	}
}

// handleRequest processes an incoming AgentRequestMsg.
func (h *AgentHandler) handleRequest(
	ctx context.Context,
	conn *Conn,
	sessionID, workspaceID, machineID, machineName, machineType string,
	req *AgentRequestMsg,
) {
	// Validate timestamp.
	const maxAge = 30 * time.Second
	if time.Duration(time.Now().Unix()-req.Ts)*time.Second > maxAge {
		_ = conn.Send(ServerErrorMsg{Type: "error", Code: "timestamp_expired", Message: "request timestamp too old"})
		return
	}

	// Check nonce replay.
	fresh, err := redisPkg.CheckAndSetNonce(ctx, h.rdb, req.Nonce, 2*time.Minute)
	if err != nil || !fresh {
		_ = conn.Send(ServerErrorMsg{Type: "error", Code: "replay", Message: "nonce already seen"})
		return
	}

	// Run rules engine.
	evalReq := &rules.EvalRequest{
		Tool:        req.Tool,
		SessionID:   sessionID,
		MachineID:   machineID,
		MachineName: machineName,
		MachineType: machineType,
		WorkspaceID: workspaceID,
	}
	if inputMap, ok := req.Input.(map[string]any); ok {
		evalReq.Input = inputMap
	}

	result, err := h.engine.Evaluate(ctx, evalReq)
	if err != nil {
		h.log.Error().Err(err).Msg("rules engine error")
		// Fall through to manual review.
	}

	inputJSON, _ := json.Marshal(req.Input)
	expiresAt := time.Now().Add(time.Duration(req.TimeoutMs) * time.Millisecond)

	// Get session seq.
	var sessionSeq int
	_ = h.db.QueryRow(ctx, `UPDATE sessions SET seq=seq+1 WHERE id=$1 RETURNING seq`, sessionID).Scan(&sessionSeq)

	// Store request.
	_, dbErr := h.db.Exec(ctx, `
		INSERT INTO permission_requests (
			id, session_id, workspace_id, machine_id, tool, input, input_hash,
			description, agent_signature, signature_verified, timeout_ms, seq, session_seq, expires_at, status, auto_decided
		) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16)`,
		req.RequestID, sessionID, workspaceID, machineID, req.Tool, inputJSON, req.InputHash,
		req.Description, req.Signature, false, req.TimeoutMs, req.Seq, sessionSeq,
		expiresAt,
		statusForResult(result),
		result != nil && result.Matched && result.Action != "",
	)
	if dbErr != nil {
		h.log.Error().Err(dbErr).Msg("store permission request failed")
		_ = conn.Send(ServerErrorMsg{Type: "error", Code: "server_error", Message: "could not store request"})
		return
	}

	if result != nil && result.Matched && result.Action != "" {
		approved := result.Action == "AUTO_APPROVE"
		_ = conn.Send(ServerDecisionMsg{Type: "decision", RequestID: req.RequestID, Approved: approved})
		_ = h.audit.Emit(ctx, audit.AuditEvent{
			WorkspaceID: workspaceID,
			SessionID:   sessionID,
			RequestID:   req.RequestID,
			EventType:   fmt.Sprintf("request.auto_%s", map[bool]string{true: "approved", false: "denied"}[approved]),
			ActorType:   "SYSTEM",
			Payload:     map[string]any{"rule_id": result.RuleID, "rule_name": result.RuleName},
		})
		return
	}

	// Manual review: fan out to mobile clients.
	h.hub.FanoutRequest(workspaceID, &PermissionRequest{
		RequestID:        req.RequestID,
		SessionID:        sessionID,
		MachineID:        machineID,
		MachineName:      machineName,
		WorkspaceID:      workspaceID,
		Tool:             req.Tool,
		Input:            req.Input,
		Description:      req.Description,
		ExpiresAt:        expiresAt.Format(time.RFC3339),
		AgentSignature:   req.Signature,
		SignatureVerified: false,
		Seq:              req.Seq,
		SessionSeq:       sessionSeq,
	}, nil /* userIDs: hub broadcasts to all workspace mobile conns */)
}

func statusForResult(r *rules.EvalResult) string {
	if r != nil && r.Matched && r.Action != "" {
		if r.Action == "AUTO_APPROVE" {
			return "AUTO_APPROVED"
		}
		if r.Action == "AUTO_DENY" {
			return "AUTO_DENIED"
		}
	}
	return "PENDING"
}
