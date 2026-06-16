package ws

// SessionMeta is metadata sent by an agent when authenticating.
type SessionMeta struct {
	AgentType   string `json:"agentType"`
	AgentVersion string `json:"agentVersion,omitempty"`
	OSName      string `json:"osName,omitempty"`
}

// --- Agent → Server ---

// AgentAuthMsg is the first message an agent must send.
type AgentAuthMsg struct {
	Type         string      `json:"type"` // "auth"
	MachineToken string      `json:"machine_token"`
	Fingerprint  string      `json:"fingerprint"`
	SessionMeta  SessionMeta `json:"session_meta"`
}

// AgentReconnectMsg is sent by an agent reconnecting to reclaim inflight requests.
type AgentReconnectMsg struct {
	Type             string   `json:"type"` // "reconnect"
	SessionID        string   `json:"session_id"`
	InflightRequests []string `json:"inflight_requests"`
}

// AgentRequestMsg is sent when the agent needs approval for a tool use.
type AgentRequestMsg struct {
	Type        string `json:"type"` // "request"
	RequestID   string `json:"request_id"`
	Tool        string `json:"tool"`
	Input       any    `json:"input"`
	InputHash   string `json:"input_hash"`
	Description string `json:"description,omitempty"`
	TimeoutMs   int    `json:"timeout_ms"`
	Signature   string `json:"signature"`
	Seq         int    `json:"seq"`
	Nonce       string `json:"nonce"`
	Ts          int64  `json:"ts"`
}

// AgentCancelMsg cancels a pending request.
type AgentCancelMsg struct {
	Type      string `json:"type"` // "cancel"
	RequestID string `json:"request_id"`
}

// AgentPingMsg is a heartbeat from the agent.
type AgentPingMsg struct {
	Type string `json:"type"` // "ping"
	Ts   int64  `json:"ts"`
}

// --- Server → Agent ---

// ServerAuthOKMsg is sent to the agent after successful auth.
// Rust enum variant "AuthOk" → camelCase tag "authOk"; fields are snake_case.
type ServerAuthOKMsg struct {
	Type         string `json:"type"` // "authOk"
	SessionID    string `json:"session_id"`
	ConnectionID string `json:"connection_id"`
}

// ServerDecisionMsg delivers a decision to the agent.
type ServerDecisionMsg struct {
	Type      string `json:"type"` // "decision"
	RequestID string `json:"request_id"`
	Approved  bool   `json:"approved"`
}

// ServerAlreadyDecidedMsg tells the agent this request was already decided.
type ServerAlreadyDecidedMsg struct {
	Type      string `json:"type"` // "alreadyDecided"
	RequestID string `json:"request_id"`
	Status    string `json:"status"`
}

// ServerExpiredMsg tells the agent a request timed out.
type ServerExpiredMsg struct {
	Type      string `json:"type"` // "expired"
	RequestID string `json:"request_id"`
}

// ServerRevokedMsg tells the agent its machine has been revoked.
type ServerRevokedMsg struct {
	Type   string `json:"type"` // "revoked"
	Reason string `json:"reason"`
}

// ServerPongMsg is the server's heartbeat reply.
type ServerPongMsg struct {
	Type     string `json:"type"` // "pong"
	Ts       int64  `json:"ts"`
	ServerTs int64  `json:"server_ts"`
}

// ServerErrorMsg reports a protocol-level error to the agent.
type ServerErrorMsg struct {
	Type    string `json:"type"` // "error"
	Code    string `json:"code"`
	Message string `json:"message"`
}

// --- Mobile → Server ---

// MobileAuthMsg is the first message a mobile client must send.
type MobileAuthMsg struct {
	Type     string `json:"type"` // "auth"
	JWTToken string `json:"jwtToken"`
}

// MobileDecideMsg is sent by the mobile client to approve or deny a request.
type MobileDecideMsg struct {
	Type           string `json:"type"` // "decide"
	RequestID      string `json:"requestId"`
	Approved       bool   `json:"approved"`
	IdempotencyKey string `json:"idempotencyKey"`
}

// MobileSubscribeMsg subscribes the mobile client to specific workspace events.
type MobileSubscribeMsg struct {
	Type         string   `json:"type"` // "subscribe"
	WorkspaceIDs []string `json:"workspaceIds"`
}

// MobilePingMsg is a heartbeat from the mobile client.
type MobilePingMsg struct {
	Type string `json:"type"` // "ping"
}

// --- Server → Mobile ---

// ServerRequestMsg pushes a pending permission request to the mobile client.
type ServerRequestMsg struct {
	Type              string `json:"type"` // "request"
	RequestID         string `json:"requestId"`
	SessionID         string `json:"sessionId"`
	MachineID         string `json:"machineId"`
	MachineName       string `json:"machineName"`
	Tool              string `json:"tool"`
	Input             any    `json:"input"`
	Description       string `json:"description,omitempty"`
	ExpiresAt         string `json:"expiresAt"`
	AgentSignature    string `json:"agentSignature"`
	SignatureVerified  bool   `json:"signatureVerified"`
	Seq               int    `json:"seq"`
	SessionSeq        int    `json:"sessionSeq"`
}

// ServerDecidedMsg notifies the mobile client that a request was decided.
type ServerDecidedMsg struct {
	Type            string `json:"type"` // "decided"
	RequestID       string `json:"requestId"`
	Status          string `json:"status"`
	DecidedByDevice string `json:"decidedByDevice,omitempty"`
}

// ServerPresenceMsg notifies the mobile client about agent presence changes.
type ServerPresenceMsg struct {
	Type      string `json:"type"` // "presence"
	MachineID string `json:"machineId"`
	Event     string `json:"event"` // "connected" | "disconnected"
	SessionID string `json:"sessionId"`
}

// ServerMobilePingMsg is sent by the server to check mobile client liveness.
type ServerMobilePingMsg struct {
	Type string `json:"type"` // "ping"
}

// --- Hardware → Server ---

// HardwareAuthMsg is the first message an ESP32 device must send.
type HardwareAuthMsg struct {
	Type      string `json:"type"`       // "auth"
	DeviceKey string `json:"device_key"` // "tgd_..."
}

// HardwareDecideMsg is sent when a button is pressed on the device.
type HardwareDecideMsg struct {
	Type      string `json:"type"`       // "decide"
	RequestID string `json:"request_id"`
	Action    string `json:"action"`     // "yes" | "yes_always" | "no"
}

// HardwarePongMsg is the device heartbeat reply.
type HardwarePongMsg struct {
	Type string `json:"type"` // "pong"
}

// --- Server → Hardware ---

// ServerHardwareRequestMsg pushes a pending permission request to the device.
type ServerHardwareRequestMsg struct {
	Type        string `json:"type"`        // "request"
	RequestID   string `json:"request_id"`
	Tool        string `json:"tool"`
	MachineName string `json:"machine_name"`
	Description string `json:"description,omitempty"`
	ExpiresAt   string `json:"expires_at"`
}

// ServerHardwareClearMsg tells the device to clear its current request display.
type ServerHardwareClearMsg struct {
	Type      string `json:"type"`       // "clear"
	RequestID string `json:"request_id"`
}

// ServerHardwareDecidedMsg notifies the device that another approver resolved the request.
type ServerHardwareDecidedMsg struct {
	Type      string `json:"type"`       // "decided"
	RequestID string `json:"request_id"`
	Status    string `json:"status"`
}

// ServerHardwarePingMsg is sent by the server to check device liveness.
type ServerHardwarePingMsg struct {
	Type string `json:"type"` // "ping"
}
