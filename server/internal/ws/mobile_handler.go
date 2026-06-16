package ws

import (
	"context"
	"encoding/json"
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
)

// MobileHandler handles /ws/mobile WebSocket connections.
type MobileHandler struct {
	hub   *Hub
	db    *pgxpool.Pool
	rdb   *redis.Client
	audit audit.AuditService
	log   zerolog.Logger
}

// NewMobileHandler creates a MobileHandler.
func NewMobileHandler(hub *Hub, db *pgxpool.Pool, rdb *redis.Client, auditSvc audit.AuditService, log zerolog.Logger) *MobileHandler {
	return &MobileHandler{hub: hub, db: db, rdb: rdb, audit: auditSvc, log: log}
}

// ServeHTTP handles mobile WebSocket connections.
func (h *MobileHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	ws, err := websocket.Accept(w, r, &websocket.AcceptOptions{
		InsecureSkipVerify: true,
	})
	if err != nil {
		h.log.Error().Err(err).Msg("mobile ws upgrade failed")
		return
	}
	defer ws.Close(websocket.StatusInternalError, "handler exited")

	connID := uuid.NewString()
	conn := NewConn(connID, ws)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	go conn.WritePump(ctx)

	// 5s auth timeout.
	authCtx, authCancel := context.WithTimeout(ctx, authTimeout)
	defer authCancel()

	_, rawMsg, err := ws.Read(authCtx)
	if err != nil {
		h.log.Warn().Err(err).Msg("mobile: failed to read auth message")
		return
	}

	var baseMsg struct {
		Type string `json:"type"`
	}
	if err := json.Unmarshal(rawMsg, &baseMsg); err != nil || baseMsg.Type != "auth" {
		_ = conn.Send(ServerErrorMsg{Type: "error", Code: "invalid_auth", Message: "first message must be auth"})
		return
	}

	var authMsg MobileAuthMsg
	if err := json.Unmarshal(rawMsg, &authMsg); err != nil {
		_ = conn.Send(ServerErrorMsg{Type: "error", Code: "invalid_auth", Message: "malformed auth message"})
		return
	}

	claims, err := auth.VerifyAccessToken(authMsg.JWTToken)
	if err != nil {
		_ = conn.Send(ServerErrorMsg{Type: "error", Code: "invalid_token", Message: "invalid JWT"})
		return
	}

	h.hub.RegisterMobile(claims.UserID, conn)
	defer h.hub.UnregisterMobile(claims.UserID, connID)

	// Send auth OK.
	_ = conn.Send(ServerAuthOKMsg{Type: "auth_ok", ConnectionID: connID})

	// Flush current presence for all workspaces the user belongs to.
	// For simplicity, fetch all workspace IDs from DB and send presence snapshots.
	rows, err := h.db.Query(ctx,
		`SELECT DISTINCT workspace_id FROM org_members om
		 JOIN workspaces w ON w.org_id = om.org_id
		 WHERE om.user_id = $1`, claims.UserID)
	if err == nil {
		defer rows.Close()
		for rows.Next() {
			var wsID string
			if err := rows.Scan(&wsID); err != nil {
				continue
			}
			presenceMap, err := redisPkg.GetPresence(ctx, h.rdb, wsID)
			if err != nil {
				continue
			}
			for machineID, data := range presenceMap {
				_ = conn.Send(ServerPresenceMsg{
					Type:      "presence",
					MachineID: machineID,
					Event:     "connected",
					SessionID: data.SessionID,
				})
			}
		}
	}

	// Drain offline queue.
	pending, err := redisPkg.DrainOfflineQueue(ctx, h.rdb, claims.UserID)
	if err != nil {
		h.log.Warn().Err(err).Msg("drain offline queue error")
	}
	for _, raw := range pending {
		select {
		case conn.sendBuf <- raw:
		default:
			// Buffer full — skip remaining offline messages.
			break
		}
	}

	// Start heartbeat.
	go RunMobileHeartbeat(ctx, conn, cancel)

	// Read loop.
	for {
		_, raw, err := ws.Read(ctx)
		if err != nil {
			return
		}

		var msg struct {
			Type string `json:"type"`
		}
		if err := json.Unmarshal(raw, &msg); err != nil {
			continue
		}

		switch msg.Type {
		case "decide":
			var decide MobileDecideMsg
			if err := json.Unmarshal(raw, &decide); err != nil {
				continue
			}
			h.handleDecide(ctx, conn, claims, &decide, r)

		case "subscribe":
			// Workspace subscription acknowledged — future fanout will reach this conn.
			// No additional action needed; hub always delivers to all mobile conns.

		case "pong":
			// Heartbeat acknowledged.
		}
	}
}

// handleDecide processes a MobileDecideMsg.
func (h *MobileHandler) handleDecide(
	ctx context.Context,
	conn *Conn,
	claims *auth.UserClaims,
	msg *MobileDecideMsg,
	r *http.Request,
) {
	// Check idempotency key.
	if msg.IdempotencyKey != "" {
		iKey := redisPkg.IdempotencyKey(msg.IdempotencyKey)
		existing, err := h.rdb.Get(ctx, iKey).Result()
		if err == nil {
			// Already decided — return cached status.
			_ = conn.Send(ServerDecidedMsg{
				Type:      "decided",
				RequestID: msg.RequestID,
				Status:    existing,
			})
			return
		}
	}

	status := "APPROVED"
	if !msg.Approved {
		status = "DENIED"
	}

	// Optimistic lock update.
	var requestID string
	err := h.db.QueryRow(ctx,
		`UPDATE permission_requests SET status=$2, decided_at=NOW() WHERE id=$1 AND status='PENDING' RETURNING id`,
		msg.RequestID, status,
	).Scan(&requestID)
	if err != nil {
		// Request already decided or not found — fetch current status.
		var currentStatus string
		_ = h.db.QueryRow(ctx,
			`SELECT status FROM permission_requests WHERE id=$1`, msg.RequestID,
		).Scan(&currentStatus)
		_ = conn.Send(ServerDecidedMsg{Type: "decided", RequestID: msg.RequestID, Status: currentStatus})
		return
	}

	// Store approval record.
	var sessionID, workspaceID string
	_ = h.db.QueryRow(ctx,
		`SELECT session_id, workspace_id FROM permission_requests WHERE id=$1`, msg.RequestID,
	).Scan(&sessionID, &workspaceID)

	_, _ = h.db.Exec(ctx,
		`INSERT INTO request_approvals (id, request_id, decided_by, approved, idempotency_key, decided_at)
		 VALUES ($1, $2, $3, $4, $5, NOW())`,
		uuid.NewString(), msg.RequestID, claims.UserID, msg.Approved, nullStr(msg.IdempotencyKey),
	)

	// Cache idempotency result.
	if msg.IdempotencyKey != "" {
		_ = h.rdb.Set(ctx, redisPkg.IdempotencyKey(msg.IdempotencyKey), status, 24*time.Hour).Err()
	}

	// Notify agent.
	_ = h.hub.SendToAgent(sessionID, ServerDecisionMsg{
		Type:      "decision",
		RequestID: msg.RequestID,
		Approved:  msg.Approved,
	})

	// Notify other mobile clients.
	h.hub.BroadcastToWorkspace(workspaceID, ServerDecidedMsg{
		Type:            "decided",
		RequestID:       msg.RequestID,
		Status:          status,
		DecidedByDevice: conn.ID(),
	}, conn.ID())

	// Emit audit event.
	_ = h.audit.Emit(ctx, audit.AuditEvent{
		WorkspaceID: workspaceID,
		SessionID:   sessionID,
		RequestID:   msg.RequestID,
		EventType:   "request." + map[bool]string{true: "approved", false: "denied"}[msg.Approved],
		ActorType:   "USER",
		ActorID:     claims.UserID,
		IPAddress:   r.RemoteAddr,
		UserAgent:   r.UserAgent(),
	})
}

func nullStr(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}
