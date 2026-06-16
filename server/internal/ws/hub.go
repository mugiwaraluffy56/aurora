package ws

import (
	"encoding/json"
	"fmt"
	"sync"

	"github.com/redis/go-redis/v9"
	"github.com/rs/zerolog"
)

// PermissionRequest is the data fanned out to mobile clients.
type PermissionRequest struct {
	RequestID         string
	SessionID         string
	MachineID         string
	MachineName       string
	WorkspaceID       string
	Tool              string
	Input             any
	Description       string
	ExpiresAt         string
	AgentSignature    string
	SignatureVerified  bool
	Seq               int
	SessionSeq        int
}

// Hub manages all active WebSocket connections.
type Hub struct {
	agentConns    map[string]*Conn   // sessionID → conn
	mobileConns   map[string][]*Conn // userID → []conn
	hardwareConns map[string][]*Conn // userID → []conn
	mu            sync.RWMutex
	redis         *redis.Client
	log           zerolog.Logger
}

// NewHub creates a Hub.
func NewHub(rdb *redis.Client, log zerolog.Logger) *Hub {
	return &Hub{
		agentConns:    make(map[string]*Conn),
		mobileConns:   make(map[string][]*Conn),
		hardwareConns: make(map[string][]*Conn),
		redis:         rdb,
		log:           log,
	}
}

// RegisterAgent adds an agent connection keyed by sessionID.
func (h *Hub) RegisterAgent(sessionID string, conn *Conn) {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.agentConns[sessionID] = conn
}

// UnregisterAgent removes an agent connection.
func (h *Hub) UnregisterAgent(sessionID string) {
	h.mu.Lock()
	defer h.mu.Unlock()
	delete(h.agentConns, sessionID)
}

// RegisterMobile adds a mobile connection for the given userID.
func (h *Hub) RegisterMobile(userID string, conn *Conn) {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.mobileConns[userID] = append(h.mobileConns[userID], conn)
}

// UnregisterMobile removes a specific mobile connection by connID.
func (h *Hub) UnregisterMobile(userID, connID string) {
	h.mu.Lock()
	defer h.mu.Unlock()
	conns := h.mobileConns[userID]
	filtered := conns[:0]
	for _, c := range conns {
		if c.ID() != connID {
			filtered = append(filtered, c)
		}
	}
	if len(filtered) == 0 {
		delete(h.mobileConns, userID)
	} else {
		h.mobileConns[userID] = filtered
	}
}

// RegisterHardware adds a hardware device connection for the given userID.
func (h *Hub) RegisterHardware(userID string, conn *Conn) {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.hardwareConns[userID] = append(h.hardwareConns[userID], conn)
}

// UnregisterHardware removes a specific hardware connection by connID.
func (h *Hub) UnregisterHardware(userID, connID string) {
	h.mu.Lock()
	defer h.mu.Unlock()
	conns := h.hardwareConns[userID]
	filtered := conns[:0]
	for _, c := range conns {
		if c.ID() != connID {
			filtered = append(filtered, c)
		}
	}
	if len(filtered) == 0 {
		delete(h.hardwareConns, userID)
	} else {
		h.hardwareConns[userID] = filtered
	}
}

// SendToAgent sends a message to the agent managing the given session.
func (h *Hub) SendToAgent(sessionID string, msg any) error {
	h.mu.RLock()
	conn, ok := h.agentConns[sessionID]
	h.mu.RUnlock()
	if !ok {
		return fmt.Errorf("no agent connection for session %s", sessionID)
	}
	return conn.Send(msg)
}

// BroadcastToWorkspace sends a message to all mobile connections for a set of users,
// optionally excluding one connection (the sender).
func (h *Hub) BroadcastToWorkspace(workspaceID string, msg any, excludeConnID string) {
	raw, err := json.Marshal(msg)
	if err != nil {
		h.log.Error().Err(err).Msg("broadcast marshal error")
		return
	}

	h.mu.RLock()
	defer h.mu.RUnlock()

	for _, conns := range h.mobileConns {
		for _, conn := range conns {
			if conn.ID() == excludeConnID {
				continue
			}
			if err := sendRaw(conn, raw); err != nil {
				h.log.Warn().Err(err).Str("conn_id", conn.ID()).Msg("broadcast send error")
			}
		}
	}
}

// FanoutRequest sends a permission request to specific userIDs' mobile and hardware connections.
func (h *Hub) FanoutRequest(workspaceID string, req *PermissionRequest, userIDs []string) {
	mobileMsg := ServerRequestMsg{
		Type:              "request",
		RequestID:         req.RequestID,
		SessionID:         req.SessionID,
		MachineID:         req.MachineID,
		MachineName:       req.MachineName,
		Tool:              req.Tool,
		Input:             req.Input,
		Description:       req.Description,
		ExpiresAt:         req.ExpiresAt,
		AgentSignature:    req.AgentSignature,
		SignatureVerified:  req.SignatureVerified,
		Seq:               req.Seq,
		SessionSeq:        req.SessionSeq,
	}
	hwMsg := ServerHardwareRequestMsg{
		Type:        "request",
		RequestID:   req.RequestID,
		Tool:        req.Tool,
		MachineName: req.MachineName,
		Description: req.Description,
		ExpiresAt:   req.ExpiresAt,
	}

	mobileRaw, err := json.Marshal(mobileMsg)
	if err != nil {
		h.log.Error().Err(err).Msg("fanout mobile marshal error")
		return
	}
	hwRaw, err := json.Marshal(hwMsg)
	if err != nil {
		h.log.Error().Err(err).Msg("fanout hardware marshal error")
		return
	}

	h.mu.RLock()
	defer h.mu.RUnlock()

	if len(userIDs) == 0 {
		for _, conns := range h.mobileConns {
			for _, conn := range conns {
				if err := sendRaw(conn, mobileRaw); err != nil {
					h.log.Warn().Err(err).Str("conn_id", conn.ID()).Msg("fanout mobile send error")
				}
			}
		}
		for _, conns := range h.hardwareConns {
			for _, conn := range conns {
				if err := sendRaw(conn, hwRaw); err != nil {
					h.log.Warn().Err(err).Str("conn_id", conn.ID()).Msg("fanout hardware send error")
				}
			}
		}
		return
	}

	for _, userID := range userIDs {
		for _, conn := range h.mobileConns[userID] {
			if err := sendRaw(conn, mobileRaw); err != nil {
				h.log.Warn().Err(err).Str("user_id", userID).Msg("fanout mobile send error")
			}
		}
		for _, conn := range h.hardwareConns[userID] {
			if err := sendRaw(conn, hwRaw); err != nil {
				h.log.Warn().Err(err).Str("user_id", userID).Msg("fanout hardware send error")
			}
		}
	}
}

func sendRaw(conn *Conn, raw []byte) error {
	select {
	case conn.sendBuf <- raw:
		return nil
	default:
		conn.Close()
		return fmt.Errorf("buffer full, closed conn %s", conn.ID())
	}
}
