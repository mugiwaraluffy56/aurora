package ws

import (
	"context"
	"encoding/json"
	"net/http"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
	"github.com/rs/zerolog"
	"nhooyr.io/websocket"

	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/auth"
	"github.com/nod/server/pkg/id"
)

// DeviceHandler handles /ws/device WebSocket connections from ESP32 hardware.
type DeviceHandler struct {
	hub   *Hub
	db    *pgxpool.Pool
	rdb   *redis.Client
	audit audit.AuditService
	log   zerolog.Logger
}

// NewDeviceHandler creates a DeviceHandler.
func NewDeviceHandler(hub *Hub, db *pgxpool.Pool, rdb *redis.Client, auditSvc audit.AuditService, log zerolog.Logger) *DeviceHandler {
	return &DeviceHandler{hub: hub, db: db, rdb: rdb, audit: auditSvc, log: log}
}

type deviceClaims struct {
	DeviceID    string
	UserID      string
	WorkspaceID string
}

// ServeHTTP handles hardware device WebSocket connections.
func (h *DeviceHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	ws, err := websocket.Accept(w, r, &websocket.AcceptOptions{
		InsecureSkipVerify: true,
	})
	if err != nil {
		h.log.Error().Err(err).Msg("device ws upgrade failed")
		return
	}
	defer ws.Close(websocket.StatusInternalError, "handler exited")

	connID := uuid.NewString()
	conn := NewConn(connID, ws)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	go conn.WritePump(ctx)

	authCtx, authCancel := context.WithTimeout(ctx, authTimeout)
	defer authCancel()

	_, rawMsg, err := ws.Read(authCtx)
	if err != nil {
		h.log.Warn().Err(err).Msg("device: failed to read auth message")
		return
	}

	var baseMsg struct {
		Type string `json:"type"`
	}
	if err := json.Unmarshal(rawMsg, &baseMsg); err != nil || baseMsg.Type != "auth" {
		_ = conn.Send(ServerErrorMsg{Type: "error", Code: "invalid_auth", Message: "first message must be auth"})
		return
	}

	var authMsg HardwareAuthMsg
	if err := json.Unmarshal(rawMsg, &authMsg); err != nil || authMsg.DeviceKey == "" {
		_ = conn.Send(ServerErrorMsg{Type: "error", Code: "invalid_auth", Message: "malformed auth message"})
		return
	}

	claims, err := h.validateDeviceKey(ctx, authMsg.DeviceKey)
	if err != nil {
		_ = conn.Send(ServerErrorMsg{Type: "error", Code: "invalid_key", Message: "invalid device key"})
		return
	}

	// Update last_seen_at.
	_, _ = h.db.Exec(ctx,
		`UPDATE hardware_devices SET last_seen_at=NOW() WHERE id=$1`, claims.DeviceID,
	)

	h.hub.RegisterHardware(claims.UserID, conn)
	defer h.hub.UnregisterHardware(claims.UserID, connID)

	_ = conn.Send(ServerAuthOKMsg{Type: "auth_ok", ConnectionID: connID})

	go RunHardwareHeartbeat(ctx, conn, cancel)

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
			var decide HardwareDecideMsg
			if err := json.Unmarshal(raw, &decide); err != nil {
				continue
			}
			h.handleDecide(ctx, conn, claims, &decide, r)

		case "pong":
			// Heartbeat acknowledged.
		}
	}
}

func (h *DeviceHandler) validateDeviceKey(ctx context.Context, rawKey string) (*deviceClaims, error) {
	keyHash := auth.HashAPIKey(rawKey)

	var claims deviceClaims
	err := h.db.QueryRow(ctx,
		`SELECT id, user_id, workspace_id FROM hardware_devices
		 WHERE key_hash=$1 AND revoked_at IS NULL`,
		keyHash,
	).Scan(&claims.DeviceID, &claims.UserID, &claims.WorkspaceID)
	if err != nil {
		return nil, err
	}
	return &claims, nil
}

func (h *DeviceHandler) handleDecide(
	ctx context.Context,
	conn *Conn,
	claims *deviceClaims,
	msg *HardwareDecideMsg,
	r *http.Request,
) {
	if msg.RequestID == "" || msg.Action == "" {
		return
	}

	approved := msg.Action == "yes" || msg.Action == "yes_always"
	status := "APPROVED"
	if !approved {
		status = "DENIED"
	}

	var requestID, sessionID string
	var tool string
	err := h.db.QueryRow(ctx,
		`UPDATE permission_requests SET status=$2, decided_at=NOW()
		 WHERE id=$1 AND status='PENDING'
		 RETURNING id, session_id, tool`,
		msg.RequestID, status,
	).Scan(&requestID, &sessionID, &tool)
	if err != nil {
		var currentStatus string
		_ = h.db.QueryRow(ctx,
			`SELECT status FROM permission_requests WHERE id=$1`, msg.RequestID,
		).Scan(&currentStatus)
		_ = conn.Send(ServerHardwareDecidedMsg{Type: "decided", RequestID: msg.RequestID, Status: currentStatus})
		return
	}

	_, _ = h.db.Exec(ctx,
		`INSERT INTO request_approvals (id, request_id, approved, decided_at)
		 VALUES ($1, $2, $3, NOW())`,
		id.New(), msg.RequestID, approved,
	)

	_ = h.hub.SendToAgent(sessionID, ServerDecisionMsg{
		Type:      "decision",
		RequestID: msg.RequestID,
		Approved:  approved,
	})

	h.hub.BroadcastToWorkspace(claims.WorkspaceID, ServerDecidedMsg{
		Type:            "decided",
		RequestID:       msg.RequestID,
		Status:          status,
		DecidedByDevice: conn.ID(),
	}, conn.ID())

	// "yes_always" → create session-scoped auto-approve rule for this tool.
	if msg.Action == "yes_always" {
		h.createAutoApproveRule(ctx, claims, sessionID, tool)
	}

	_ = h.audit.Emit(ctx, audit.AuditEvent{
		WorkspaceID: claims.WorkspaceID,
		SessionID:   sessionID,
		RequestID:   msg.RequestID,
		EventType:   "request." + map[bool]string{true: "approved", false: "denied"}[approved],
		ActorType:   "USER",
		ActorID:     claims.UserID,
		IPAddress:   r.RemoteAddr,
		UserAgent:   "Nod-Hardware",
	})

	_ = conn.Send(ServerHardwareDecidedMsg{Type: "decided", RequestID: msg.RequestID, Status: status})
}

func (h *DeviceHandler) createAutoApproveRule(ctx context.Context, claims *deviceClaims, sessionID, tool string) {
	_, _ = h.db.Exec(ctx,
		`INSERT INTO rules (id, scope, scope_id, workspace_id, name, action, conditions, enabled, created_by, expires_at)
		 VALUES ($1, 'SESSION', $2, $3, $4, 'AUTO_APPROVE', $5, TRUE, $6, NOW() + INTERVAL '24 hours')`,
		id.New(),
		sessionID,
		claims.WorkspaceID,
		"Hardware always: "+tool,
		map[string]any{"tool": tool},
		claims.UserID,
	)
}

// ProvisionDeviceKey generates a new hardware device key for a user.
// Called from the REST handler at POST /devices/hardware.
func ProvisionDeviceKey(ctx context.Context, db *pgxpool.Pool, userID, workspaceID, name string) (rawKey, deviceID string, err error) {
	rawKey, keyHash, keyPrefix, err := auth.GenerateAPIKey()
	if err != nil {
		return "", "", err
	}
	// Replace "apk_" prefix with "nod_" to distinguish hardware keys.
	rawKey = "nod_" + rawKey[4:]
	keyHash = auth.HashAPIKey(rawKey)
	keyPrefix = "nod_" + keyPrefix[4:]

	deviceID = id.New()
	_, err = db.Exec(ctx,
		`INSERT INTO hardware_devices (id, user_id, workspace_id, name, key_hash, key_prefix)
		 VALUES ($1,$2,$3,$4,$5,$6)`,
		deviceID, userID, workspaceID, name, keyHash, keyPrefix,
	)
	if err != nil {
		return "", "", err
	}
	return rawKey, deviceID, nil
}

