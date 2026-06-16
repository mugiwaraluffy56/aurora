# Nod — Production Architecture

## What This Does

Claude Code pauses on stdin waiting for `y/n` when it wants to run a tool. Nod intercepts that pause via a PreToolUse hook, relays the request to your phone, and sends back the decision. You tap Yes or No on your phone. Claude Code continues or stops. You never need to be at the terminal.

```
Claude Code wants to run Bash: "rm -rf /tmp/build"
  ↓
PreToolUse hook fires (tap-guard-hook script)
  ↓
Hook BLOCKS — waiting for decision
  ↓
Hook sends request to Nod server via WebSocket
  ↓
Server pushes to your phone (push notification + live feed)
  ↓
You tap [Approve] on phone
  ↓
Server sends decision back to hook
  ↓
Hook exits 0 (approve) or 2 (deny)
  ↓
Claude Code continues (or stops)
```

For Codex and other agents without hook APIs: PTY wrapper intercepts interactive prompts and injects `y\n` or `n\n` into process stdin directly.

---

## System Diagram

```
                          ┌──────────────────────────────────────────┐
                          │           TAPGUARD PLATFORM              │
┌─────────────┐           │                                          │
│  Claude     │  stdin/   │  ┌──────────────┐   ┌────────────────┐  │
│  Code       ├──stdout──►│  │  Hono HTTP   │   │  WS Gateway    │  │
│  (PreHook)  │           │  │  API Server  │   │  (sticky LB)   │  │
└─────────────┘           │  └──────┬───────┘   └───────┬────────┘  │
                          │         │                    │           │
┌─────────────┐  PTY      │  ┌──────▼──────────────────▼────────┐  │
│  Codex      ├──wrap────►│  │         Business Logic            │  │
│  (wrapper)  │           │  │   Auth · Rules · Audit · Push     │  │
└─────────────┘           │  └──────┬──────────────────┬─────────┘  │
                          │         │                  │            │
┌─────────────┐  socket   │  ┌──────▼──────┐  ┌───────▼─────────┐  │
│  tap-guard  ├──────────►│  │ PostgreSQL  │  │    Redis        │  │
│  ask (CLI)  │           │  │ (primary +  │  │  pub/sub        │  │
└─────────────┘           │  │  replicas)  │  │  sessions       │  │
                          │  └─────────────┘  │  offline queue  │  │
                          │                   │  rate limits    │  │
                          │                   └─────────────────┘  │
                          └──────────────────────────┬─────────────┘
                                                     │
                              ┌──────────────────────┤
                              │                      │
                     ┌────────▼──────┐     ┌─────────▼──────┐
                     │   FCM / APNs  │     │  Mobile App    │
                     │  (push when   │     │  WebSocket     │
                     │   offline)    │     │  (when online) │
                     └───────────────┘     └────────────────┘
                              │                      │
                              └──────────┬───────────┘
                                         │
                              ┌──────────▼───────────┐
                              │     iOS / Android    │
                              │   Nod App       │
                              │                      │
                              │  [Approve] [Deny]    │
                              │  Lock screen actions │
                              └──────────────────────┘
```

---

## Repository Structure

```
tapguard/
├── docs/
│   └── ARCHITECTURE.md        ← this file
├── server/                    ← Bun + Hono + Prisma + PostgreSQL + Redis
│   ├── src/
│   │   ├── index.ts
│   │   ├── auth/
│   │   ├── ws/
│   │   ├── api/
│   │   ├── rules/
│   │   ├── notifications/
│   │   └── db/
│   ├── prisma/
│   │   └── schema.prisma
│   └── package.json
├── agent/                     ← Python CLI (pip install tapguard-agent)
│   ├── tap_guard/
│   │   ├── hook.py            ← Claude Code PreToolUse hook
│   │   ├── pty_wrap.py        ← Codex/Gemini PTY wrapper
│   │   ├── session.py         ← WebSocket client + session management
│   │   ├── crypto.py          ← Ed25519 signing
│   │   └── cli.py             ← tap-guard CLI entrypoint
│   └── pyproject.toml
└── mobile/                    ← Expo React Native (iOS + Android)
    ├── app/
    │   ├── (auth)/
    │   ├── (app)/
    │   └── _layout.tsx
    ├── components/
    ├── hooks/
    └── package.json
```

---

## 1. Machine Identity Layer

### Core Insight

Sessions are ephemeral runtime facts. Machines are durable identity anchors. Conflating them is the biggest architectural mistake to avoid.

```
Organization
  └── Workspace
        └── Machine  ← stable, survives reboots
              └── Session  ← ephemeral, one per agent process
                    └── PermissionRequest
```

### Machine Entity

```
Machine {
  id:            cuid()          # stable forever
  workspaceId:   FK
  name:          string          # "prod-api-1", "macbook-puneeth"
  type:          ENUM            # LOCAL | AWS_EC2 | DOCKER | REMOTE_SSH | TMUX | CODESPACE
  fingerprint:   string          # see fingerprint strategy
  publicKey:     string          # Ed25519 public key, set at registration
  tokenHash:     string          # bcrypt of machine token
  tokenPrefix:   string          # "mtk_" + first 8 chars, for display
  registeredBy:  userId
  ownedBy:       userId
  lastSeenAt:    timestamp
  revokedAt:     timestamp?
  metadata:      JSONB           # { instanceId, region, imageId, hostname }
}
```

### Fingerprint Strategy

```
LOCAL:    SHA256(hostname + cpu_serial + mac_addr + os_type)
AWS_EC2:  instance-id from http://169.254.169.254/latest/meta-data/instance-id
DOCKER:   SHA256(container_id + image_digest)
TMUX:     SHA256(hostname + tmux_session_name + uid)
SSH:      SHA256(hostname + remote_addr + ssh_client_fingerprint)
```

Fingerprint is validated on every WebSocket connection. Mismatch = connection rejected + security alert raised.

### Machine Token vs API Key

```
API Key       workspace-scoped credential     used only for first-time machine registration
Machine Token issued by server post-register  used for all subsequent connections
              Ed25519-signed JWT               includes machineId, workspaceId, fingerprint
              90-day expiry                    rotated with 24h overlap window
              stored at ~/.tap-guard/machine.token (chmod 0600)
```

API keys are workspace-scoped and shared. Machine tokens are machine-scoped. Compromise one machine without affecting others.

### Machine Registration Flow

```
1. User creates API key in dashboard
2. On machine: tap-guard init --api-key apk_xxx --workspace prod
3. Agent generates Ed25519 keypair, stores private key locally (chmod 0600)
4. Agent calls POST /machines { apiKey, name, type, fingerprint, publicKey }
5. Server validates API key, creates Machine record
6. Server issues machine token (JWT)
7. Agent stores machine token at ~/.tap-guard/machine.token
8. API key no longer needed — machine token handles all future connections
```

### Machine Revocation

Server sets `revokedAt`. Machine token added to Redis blocklist (TTL = remaining token expiry). Running sessions receive `{ type: "revoked" }` via WebSocket — agent halts immediately.

### Machine Ownership Transfer

```
POST /machines/:machineId/transfer
Body: { targetUserId, targetWorkspaceId? }
Requires: OWNER or ADMIN role
Effect: sessions continue uninterrupted, only ownership changes
Audit: transfer event recorded
```

---

## 2. Session Lifecycle

### Durable Session IDs

Agent generates sessionId on first connect: `<machineId>:<pid>:<unix_ts>`. Stored in memory. Sessions are intentionally ephemeral — a restarted process is a new session. The machine is what persists.

### Reconnect Behavior (network blip, not process death)

```
Agent WS drops
  → Agent has in-flight requestId(s) in memory
  → Reconnect with: { type: "reconnect", sessionId, inflightRequests: [requestId] }
  → Server checks each requestId:
      PENDING   → re-subscribe agent to decision updates
      DECIDED   → immediately send { type: "decision", requestId, approved }
      EXPIRED   → send { type: "expired", requestId }
  → Agent resumes without losing approval state
```

Reconnect backoff: 1s → 2s → 4s → 8s → 16s → cap 30s. After 5 minutes disconnected, server expires all pending requests for that session.

### Network Partition Defense

```
Agent:  sends { type: "ping", ts: unix_ms } every 15s
        expects { type: "pong" } within 5s
        TCP keepalive: SO_KEEPALIVE, 10s interval

Server: no ping in 30s → mark session DISCONNECTED
        no ping in 90s → mark session ZOMBIE, expire all pending requests
```

### Zombie Session Cleanup

Background job every 60s:
```sql
UPDATE sessions SET status = 'ZOMBIE'
WHERE last_heartbeat_at < NOW() - INTERVAL '90 seconds'
  AND status = 'ACTIVE';

UPDATE permission_requests SET status = 'EXPIRED'
WHERE session_id IN (SELECT id FROM sessions WHERE status = 'ZOMBIE')
  AND status = 'PENDING';
```

Zombie sessions kept 24h for audit, then purged.

### Concurrent Sessions on Same Machine

Fully supported. Each process gets unique sessionId. Mobile UI groups by machine:

```
Machine: prod-api-1
  ├── Session: claude-code [PID 1234] ACTIVE   3 pending
  ├── Session: claude-code [PID 5678] ACTIVE   1 pending
  └── Session: codex [PID 9012]       IDLE     0 pending
```

---

## 3. Realtime Architecture

### WebSocket Topology

```
Tier 1 (< 1k connections):
  Single Bun process, in-process Map<connectionId, WsConn>
  No Redis needed — direct function calls

Tier 2 (1k–50k connections):
  Multiple server instances behind L4 LB (sticky sessions via IP hash)
  Redis pub/sub for cross-instance fanout
  Channel per workspace: workspace:{workspaceId}

Tier 3 (50k+ connections):
  Dedicated WS gateway (stateless routing layer)
  HTTP API servers are pure stateless REST
  Redis routing table: userId → instanceId
```

### Presence System

Redis hash per workspace: `presence:ws:{workspaceId}`

```
HSET presence:ws:{workspaceId} {machineId} {JSON: { sessionIds, connectedAt, lastSeen }}
EXPIRE presence:ws:{workspaceId} 120  // refreshed by heartbeat
```

Mobile clients receive:
```json
{ "type": "presence", "machineId": "m_xxx", "event": "connected|disconnected|session_added|session_removed" }
```

### Heartbeat Protocol

```
Agent → Server:  { "type": "ping", "ts": 1234567890 }       every 15s
Server → Agent:  { "type": "pong", "ts": 1234567890, "serverTs": 1234567891 }

Server → Mobile: { "type": "ping" }                          every 30s (battery conscious)
Mobile → Server: { "type": "pong" }
```

`ts` echoed back for round-trip latency measurement and clock skew detection.

### Backpressure Handling

Per-connection send buffer tracked in memory:

```
0–50 messages:   normal
50–100:          drop presence/heartbeat, keep approval requests
100–200:         drop all non-approval messages, log warning
200+:            terminate connection
```

Approval requests are never dropped — they go to Redis offline queue if buffer full.

### Fanout to Multiple Mobile Devices

```
Request created
  → Rules engine evaluates first (may short-circuit to AUTO_APPROVED/AUTO_DENIED)
  → Find all APPROVER+ users in workspace
  → For each user:
      a. Send to all connected WS sessions
      b. Send FCM/APNs push to registered device tokens
  → Redis pub/sub broadcasts to other server instances
```

FCM batch send: up to 500 tokens per request. Use FCM `data` payload (not `notification`) so app controls display and handles lock-screen actions.

### Offline Queueing

Redis sorted set per user: `offline_queue:{userId}`, score = expiresAt unix timestamp.

```
ZADD offline_queue:{userId} {expiresAt} {JSON: requestPayload}
```

On mobile reconnect:
```
ZRANGEBYSCORE offline_queue:{userId} {now} +inf   // non-expired
ZREMRANGEBYSCORE offline_queue:{userId} -inf {now} // prune expired
```

### Event Ordering

Each session has monotonic `seq` counter (in-memory). Events carry `{ sessionSeq, globalTs }`. Mobile detects gaps and can request replay. Server keeps last 1000 events per session in Redis ring buffer (`LPUSH` + `LTRIM`).

---

## 4. Approval Lifecycle — Full State Machine

```
                    ┌─────────────┐
                    │   PENDING   │
                    └──────┬──────┘
           ┌───────────────┼────────────────────────┐
           │               │              │          │
           ▼               ▼              ▼          ▼
     ┌──────────┐   ┌──────────┐   ┌──────────┐  ┌───────────┐
     │ APPROVED │   │  DENIED  │   │ EXPIRED  │  │ CANCELLED │
     └──────────┘   └──────────┘   └──────────┘  └───────────┘

AUTO_APPROVED  ← rules engine fires before any mobile notification
AUTO_DENIED    ← rules engine fires before any mobile notification
```

All non-PENDING states are terminal. No transitions out.

### Race Condition Prevention

All state transitions use optimistic locking:

```sql
UPDATE permission_requests
SET status = $newStatus,
    decided_at = NOW(),
    decided_by_user = $userId,
    decision_source = $source
WHERE id = $id
  AND status = 'PENDING'
RETURNING *;
```

0 rows affected = already decided. Return current state to caller. Mobile receives `{ type: "already_decided", requestId, currentStatus }`.

### Idempotency

Every decision carries a `idempotencyKey` (UUID, generated by mobile on tap). Server stores `(idempotencyKey → response)` in Redis for 24h. Duplicate requests return cached response immediately. Prevents double-tap on mobile.

### Request Integrity (Ed25519 Signing)

```
Agent signs each request:
  payload = requestId || machineId || tool || SHA256(inputJSON) || timestamp || nonce
  signature = Ed25519.sign(payload, machinePrivateKey)

Server verifies:
  Ed25519.verify(payload, signature, machine.publicKey)
  Reject if fails

Mobile shows:
  "✓ Cryptographically verified — prod-api-1"
  "⚠ Unverified request" (old agent without signing)
```

Even if server is compromised, attacker cannot fabricate requests that pass verification.

---

## 5. Audit and Compliance

### Immutable Event Log

Separate `audit_events` table. No UPDATE, no DELETE. Enforced by DB trigger that raises exception on mutation attempts.

```
AuditEvent {
  id:             UUID (not cuid — harder to enumerate)
  org_id:         FK
  workspace_id:   FK?
  session_id:     string?
  request_id:     string?
  event_type:     TEXT (see enum below)
  actor_type:     ENUM: USER | MACHINE | RULES_ENGINE | SYSTEM | ADMIN
  actor_id:       TEXT
  device_id:      TEXT?
  ip_address:     INET
  payload:        JSONB
  previous_hash:  TEXT (SHA256 of previous event — hash chain)
  checksum:       TEXT (SHA256 of all fields)
  created_at:     TIMESTAMPTZ (partitioned by month)
}
```

Hash chain: tamper with any event and the chain breaks from that point forward.

### Event Types

```
AUTH_LOGIN, AUTH_LOGOUT, AUTH_TOKEN_REFRESH, AUTH_OAUTH_LINKED
ORG_CREATED, ORG_DELETED, ORG_MEMBER_ADDED, ORG_MEMBER_REMOVED, ORG_MEMBER_ROLE_CHANGED
WORKSPACE_CREATED, WORKSPACE_DELETED
MACHINE_REGISTERED, MACHINE_REVOKED, MACHINE_RENAMED, MACHINE_TRANSFERRED, MACHINE_TOKEN_ROTATED
SESSION_STARTED, SESSION_ENDED, SESSION_ZOMBIE
REQUEST_CREATED, REQUEST_APPROVED, REQUEST_DENIED, REQUEST_EXPIRED, REQUEST_CANCELLED
REQUEST_AUTO_APPROVED, REQUEST_AUTO_DENIED
RULE_CREATED, RULE_UPDATED, RULE_DELETED
API_KEY_CREATED, API_KEY_REVOKED
DEVICE_REGISTERED, DEVICE_UNREGISTERED
DELEGATION_CREATED, DELEGATION_EXPIRED
ESCALATION_TRIGGERED
SECURITY_ALERT_FINGERPRINT_MISMATCH, SECURITY_ALERT_TOKEN_REUSE
```

### Decision Attribution

Every terminal state stores:
```
decided_by_user_id:   nullable
decided_by_device_id: nullable (which phone model/token)
decision_source:      ENUM: MANUAL | RULE:{ruleId} | TIMEOUT | CANCELLATION
rule_id:              nullable FK
response_time_ms:     INT (latency analytics)
```

### Export

```
GET /audit/export
    ?format=csv|json|ndjson
    &from=ISO8601
    &to=ISO8601
    &eventTypes=REQUEST_APPROVED,REQUEST_DENIED
    &workspaceId=...
    &cursor=...
```

Streamed response — no buffering. Large exports: async job → signed S3 URL → email notification.

SIEM integration: webhook endpoint posting events in CEF or JSON. Configurable per org.

---

## 6. Rules Engine

### Rule Entity

```
Rule {
  id:           cuid()
  org_id:       FK
  workspace_id: FK?     (null = org-wide)
  created_by:   userId
  scope:        ENUM: ORG | WORKSPACE | USER | SESSION
  scope_target: string? (userId or sessionId)
  name:         string
  action:       ENUM: AUTO_APPROVE | AUTO_DENY
  priority:     INT     (lower number = evaluated first)
  conditions:   JSONB
  expires_at:   TIMESTAMPTZ?
  enabled:      BOOLEAN
  hit_count:    INT
}
```

### Condition Schema

```json
{
  "all": [
    { "field": "tool", "op": "eq", "value": "Read" },
    { "field": "tool", "op": "in", "value": ["Read", "Glob"] },
    { "field": "input.command", "op": "matches", "value": "^(ls|pwd|cat)" },
    { "field": "machine.name", "op": "eq", "value": "local-dev" },
    { "field": "session.agentType", "op": "eq", "value": "CLAUDE_CODE" }
  ],
  "any": [...]
}
```

Operators: `eq`, `neq`, `in`, `not_in`, `matches` (regex), `contains`, `starts_with`.

### Evaluation Order

```
1. SESSION-scoped rules for this sessionId       (highest priority)
2. USER-scoped rules for machine owner
3. WORKSPACE-scoped rules
4. ORG-scoped rules                              (lowest priority)

Within each tier: sort by priority ASC, then createdAt DESC.
First match wins. No match → requires manual approval (default).
```

### Conflict Resolution

Same scope, same priority, conflicting actions: **DENY wins** (security-conservative default). Configurable per org: `DENY_WINS | APPROVE_WINS | MANUAL_ON_CONFLICT`.

### Caching

Workspace rules cached in Redis for 60s: `rule:cache:{workspaceId}`. Invalidated on any rule change. Cold evaluation < 1ms for typical rule sets.

---

## 7. Security Model

### Threat Model

| Threat | Mitigation |
|--------|-----------|
| API key leaked | Machine tokens replace API keys for daily ops. API keys only for one-time registration. |
| Machine token stolen | Token includes fingerprint. Useless without matching machine fingerprint. Redis blocklist on revocation. |
| Server compromised | Agent Ed25519 signatures. Server cannot forge requests that pass verification. |
| Replay attack | Request nonce + 30s timestamp window. Seen nonces cached in Redis (60s TTL). |
| Session hijacking | Machine token bound to machineId. WebSocket revalidates machineId on every reconnect. |
| Rogue mobile device | Devices registered per user, revocable. Admin sees all registered devices. |

### WebSocket Authentication Protocol

```
1. Client opens WS
2. Server starts 5s authentication timer
3. Client sends first message:
     { type: "auth", token: "...", fingerprint: "..." }
4. Server validates:
     a. JWT signature
     b. Token not expired
     c. Token not in Redis revocation list
     d. Fingerprint matches registered machine (agents only)
5. Valid → { type: "auth_ok", connectionId: "..." }
6. Invalid or 5s elapsed → close with code 4001
```

No token in URL query params (logs). Token always in first WS message.

### Token Rotation

```
Machine token rotation:
  Agent: every 85 days, call POST /machines/:id/rotate-token
  Server: issue new token
          old token valid for 24h overlap window (stored in Redis)
          after 24h: old token rejected

Agent: checks token age on startup, auto-rotates if > 80 days old
```

### End-to-End Signing

```
Registration:
  Agent generates Ed25519 keypair
  Private key: ~/.tap-guard/machine.key (chmod 0600)
                optionally hardware-backed via TPM or Secure Enclave
  Public key: registered with server

Per-request:
  payload = requestId || machineId || tool || SHA256(input) || timestamp || nonce
  signature = Ed25519.sign(payload, privateKey)
  signature sent with every request

Server:
  Ed25519.verify(payload, signature, machine.publicKey)
  reject on failure

Mobile:
  "✓ Cryptographically verified — prod-api-1"
```

---

## 8. Mobile Experience

### Push Notification Strategy

```
iOS:
  UNNotificationCategory "APPROVAL_REQUEST"
  Actions:
    "APPROVE" — foreground: false, authRequired: true
    "DENY"    — foreground: false, authRequired: true, destructive: true
  Requires: com.apple.developer.usernotifications.critical-alerts entitlement

Android:
  FCM data message (not notification — app controls display)
  NotificationCompat with action buttons
  Heads-up via high priority channel
  BroadcastReceiver handles APPROVE/DENY without opening app
```

Lock-screen flow:
```
Request arrives → FCM push →
User sees on lock screen: "claude-code on prod-api-1: rm -rf /tmp/build"
Buttons: [Approve] [Deny]
User taps without unlocking → background task sends decision
Confirmation: "Approved — 4.2s"
```

### Swipe Gestures

```
Pending list:
  Swipe right (green) → Approve + haptic
  Swipe left (red)    → Deny + haptic
  Long press          → Show full details before deciding
  Tap                 → Open detail modal
```

### Bulk Approval

When same tool + same machine has 5+ pending:
```
"claude-code on prod-api-1 has 7 Read requests pending"
[Review All]  [Approve All Read]  [Deny All]
```

"Approve All Read" only approves matching tool type.

### Multi-Device Sync

```
Device A taps Approve →
  Server records decision →
  WebSocket broadcast to all connected devices for this user:
    { type: "request_decided", requestId, status: "APPROVED", decidedByDevice: "iPhone 15 Pro" }
  Device B, C: pending card animates out — "Approved on iPhone 15 Pro"

Both devices tap simultaneously:
  One wins (optimistic DB lock)
  Loser gets: { type: "already_decided", currentStatus: "APPROVED" }
  Loser UI: card slides out with "Already decided"
```

### Offline Behavior

```
Mobile offline:
  Feed shows "● Offline" banner
  Tap Approve/Deny → queued locally with timestamp

Reconnects:
  Drain local queue, send decisions
  Server responds per decision:
    ACCEPTED:         processed
    EXPIRED:          "Request expired while offline — rm -rf /tmp/build"
    ALREADY_DECIDED:  another device or rule decided it
  Re-sync pending from server (offline queue drain)
```

---

## 9. Multi-Tenancy

### Hierarchy

```
User (global identity, multiple orgs)
  └── OrgMembership (role scoped per org)
        └── Organization
              └── Workspace
                    ├── ApiKey
                    ├── Rules
                    ├── ApprovalPolicy
                    └── Machine
                          └── Session
                                └── PermissionRequest
```

### RBAC

| Action | OWNER | ADMIN | APPROVER | VIEWER |
|--------|-------|-------|----------|--------|
| Approve/Deny | ✓ | ✓ | ✓ | ✗ |
| View requests/audit | ✓ | ✓ | ✓ | ✓ |
| Manage rules | ✓ | ✓ | ✗ | ✗ |
| Manage machines | ✓ | ✓ | ✗ | ✗ |
| Manage workspace | ✓ | ✓ | ✗ | ✗ |
| Manage members | ✓ | ✓ | ✗ | ✗ |
| Billing / delete org | ✓ | ✗ | ✗ | ✗ |
| Transfer ownership | ✓ | ✗ | ✗ | ✗ |

### Approval Delegation

```
Delegation {
  delegator_id:  FK
  delegatee_id:  FK
  workspace_id:  FK?  (null = all workspaces in org)
  starts_at:     TIMESTAMPTZ
  expires_at:    TIMESTAMPTZ
  reason:        TEXT  ("On vacation 12–19 June")
}
```

Active delegations queried on each fan-out. Delegatee notified alongside delegator.

### Team-Based Approvals (Approval Policy)

```
ApprovalPolicy {
  workspace_id:       FK
  name:               TEXT
  match_tools:        TEXT[]
  required_approvers: INT    (2 = requires 2 distinct users)
  escalate_after_ms:  INT    (15000 = escalate after 15s)
  escalate_to_user:   userId?
}
```

Multi-approver requests tracked in `request_approvals` table. Stay PENDING until N distinct approvals received.

### Escalation

```
1. Request created → Redis key set: escalation:{requestId} (TTL = escalateAfterMs)
2. Key expires → triggers escalation event handler
3. Handler: push + WS to escalate_to_user
4. Primary approver responds before escalation → Redis DEL cancels it
```

---

## 10. Scalability

### Capacity by Tier

| Component | 10 users | 1k users | 10k users | 100k users |
|-----------|----------|----------|-----------|------------|
| HTTP API | trivial | trivial | horizontal scale | horizontal scale |
| WebSocket | single process | single + Redis pub/sub | dedicated gateway | dedicated gateway cluster |
| PostgreSQL | single | single | read replica | pooling + sharding by orgId |
| Redis | optional | single | cluster | cluster |
| FCM | trivial | trivial | batch API | rate limit aware + queue |
| Audit log | PG partitioned | PG partitioned | PG partitioned | ClickHouse |

### Bottlenecks

1. WebSocket fanout during approval bursts (100k users all receive same request)
2. DB writes on every decision
3. FCM default rate limit: 1,000 req/s per project

### Service Boundaries

**Stay monolith forever:**
- Auth (co-located = no network hop on every auth check)
- Org/workspace CRUD
- Rules management CRUD

**Extract at tier 2 (1k+ users):**
- WebSocket gateway (stateful, different scaling profile)
- Notification service (FCM rate limits, APNs certs, different failure modes)

**Extract at tier 3 (10k+ users):**
- Rules engine (CPU-bound, cache separately)
- Audit service (write-heavy, different storage)

**Extract at tier 4 (100k+ users):**
- Presence service
- Event bus (Kafka)

**Recommendation:** build tier 1, design for tier 2, do not prematurely optimize for tier 3.

---

## 11. Plugin / Adapter Architecture

### Adapter Contract

All adapters communicate via stdin/stdout JSON protocol:

```
Adapter is any executable that:
  1. Reads tool call info from stdin (or intercepts PTY output)
  2. Forwards to tap-guard daemon (Unix socket or localhost HTTP)
  3. Waits for { "type": "decision", "approved": true|false }
  4. Exits 0 (approved) or exits 2 (denied)
     OR injects y/n into PTY stdin
```

### Supported Adapters

```
Claude Code:
  Type:     PreToolUse hook
  Config:   .claude/settings.json → hooks → PreToolUse → command: "tap-guard hook"
  Protocol: Claude sends tool JSON to hook stdin, reads exit code

Codex:
  Type:     PTY wrapper
  Usage:    tap-guard exec --codex -- codex ...
  Protocol: forks codex in PTY, parses "Allow this action?" prompts,
            intercepts before codex reads input, injects y/n post-decision

Aider:
  Type:     Subprocess wrapper
  Usage:    tap-guard exec --aider -- aider ...

Gemini CLI:
  Type:     PTY wrapper (same as Codex)
  Usage:    tap-guard exec --gemini -- gemini ...

OpenCode:
  Type:     Native plugin if API supports hooks, else PTY wrapper

Generic shell:
  Usage:    if tap-guard ask "Delete production DB?" --timeout 30; then delete_db; fi
  Protocol: creates request, blocks, exits 0/1 based on mobile decision
```

### Config File

```toml
# ~/.tap-guard/config.toml

[agent]
type = "claude-code"
workspace_id = "ws_xxx"
machine_token_path = "~/.tap-guard/machine.token"
machine_key_path = "~/.tap-guard/machine.key"
server_url = "wss://tapguard.example.com"

[adapters.claude-code]
hook_mode = "pre-tool-use"
default_timeout_ms = 30000

[adapters.codex]
pty_mode = true
prompt_patterns = ["Allow this action?", "Proceed?"]
```

---

## 12. Database Schema

```sql
-- Users & Auth
CREATE TABLE users (
  id              TEXT PRIMARY KEY,
  email           TEXT UNIQUE NOT NULL,
  password_hash   TEXT,
  name            TEXT,
  avatar_url      TEXT,
  created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE oauth_accounts (
  id                TEXT PRIMARY KEY,
  user_id           TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider          TEXT NOT NULL,
  provider_user_id  TEXT NOT NULL,
  access_token      TEXT,
  refresh_token     TEXT,
  UNIQUE(provider, provider_user_id)
);

CREATE TABLE refresh_tokens (
  id          TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  TEXT UNIQUE NOT NULL,
  expires_at  TIMESTAMPTZ NOT NULL,
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE device_tokens (
  id           TEXT PRIMARY KEY,
  user_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token        TEXT UNIQUE NOT NULL,
  platform     TEXT NOT NULL,
  created_at   TIMESTAMPTZ DEFAULT NOW(),
  last_used_at TIMESTAMPTZ
);

-- Org hierarchy
CREATE TABLE organizations (
  id         TEXT PRIMARY KEY,
  name       TEXT NOT NULL,
  slug       TEXT UNIQUE NOT NULL,
  rule_conflict_resolution TEXT NOT NULL DEFAULT 'DENY_WINS',
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TYPE org_role AS ENUM ('OWNER', 'ADMIN', 'APPROVER', 'VIEWER');

CREATE TABLE org_members (
  id        TEXT PRIMARY KEY,
  user_id   TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  org_id    TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  role      org_role NOT NULL DEFAULT 'VIEWER',
  joined_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(user_id, org_id)
);

CREATE TABLE workspaces (
  id         TEXT PRIMARY KEY,
  org_id     TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  name       TEXT NOT NULL,
  slug       TEXT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(org_id, slug)
);

CREATE TABLE api_keys (
  id           TEXT PRIMARY KEY,
  workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  name         TEXT NOT NULL,
  key_hash     TEXT UNIQUE NOT NULL,
  key_prefix   TEXT NOT NULL,
  created_by   TEXT NOT NULL REFERENCES users(id),
  last_used_at TIMESTAMPTZ,
  expires_at   TIMESTAMPTZ,
  revoked_at   TIMESTAMPTZ,
  created_at   TIMESTAMPTZ DEFAULT NOW()
);

-- Machine identity
CREATE TYPE machine_type AS ENUM ('LOCAL', 'AWS_EC2', 'DOCKER', 'REMOTE_SSH', 'TMUX', 'CODESPACE');

CREATE TABLE machines (
  id           TEXT PRIMARY KEY,
  workspace_id TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  name         TEXT NOT NULL,
  type         machine_type NOT NULL,
  fingerprint  TEXT NOT NULL,
  public_key   TEXT NOT NULL,
  token_hash   TEXT UNIQUE NOT NULL,
  token_prefix TEXT NOT NULL,
  registered_by TEXT NOT NULL REFERENCES users(id),
  owned_by     TEXT NOT NULL REFERENCES users(id),
  last_seen_at TIMESTAMPTZ,
  revoked_at   TIMESTAMPTZ,
  metadata     JSONB DEFAULT '{}',
  created_at   TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(workspace_id, fingerprint)
);

-- Sessions
CREATE TYPE session_status AS ENUM ('ACTIVE', 'DISCONNECTED', 'ZOMBIE', 'ENDED');
CREATE TYPE agent_type AS ENUM ('CLAUDE_CODE', 'CODEX', 'AIDER', 'GEMINI_CLI', 'OPENCODE', 'GENERIC');

CREATE TABLE sessions (
  id                 TEXT PRIMARY KEY,
  machine_id         TEXT NOT NULL REFERENCES machines(id) ON DELETE CASCADE,
  workspace_id       TEXT NOT NULL REFERENCES workspaces(id),
  agent_type         agent_type NOT NULL,
  pid                INT,
  tty                TEXT,
  status             session_status NOT NULL DEFAULT 'ACTIVE',
  last_heartbeat_at  TIMESTAMPTZ DEFAULT NOW(),
  connected_at       TIMESTAMPTZ DEFAULT NOW(),
  ended_at           TIMESTAMPTZ,
  metadata           JSONB DEFAULT '{}'
);

-- Permission requests
CREATE TYPE request_status AS ENUM (
  'PENDING', 'APPROVED', 'DENIED', 'EXPIRED', 'CANCELLED', 'AUTO_APPROVED', 'AUTO_DENIED'
);
CREATE TYPE decision_source AS ENUM ('MANUAL', 'RULE', 'TIMEOUT', 'CANCELLATION');

CREATE TABLE permission_requests (
  id                  TEXT PRIMARY KEY,
  session_id          TEXT NOT NULL REFERENCES sessions(id),
  workspace_id        TEXT NOT NULL REFERENCES workspaces(id),
  tool                TEXT NOT NULL,
  input_json          JSONB NOT NULL,
  input_hash          TEXT NOT NULL,
  description         TEXT,
  agent_signature     TEXT NOT NULL,
  seq                 INT NOT NULL,
  status              request_status NOT NULL DEFAULT 'PENDING',
  decided_at          TIMESTAMPTZ,
  decided_by_user     TEXT REFERENCES users(id),
  decided_by_device   TEXT REFERENCES device_tokens(id),
  decision_source     decision_source,
  rule_id             TEXT,
  idempotency_key     TEXT UNIQUE,
  response_time_ms    INT,
  expires_at          TIMESTAMPTZ NOT NULL,
  created_at          TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_permission_requests_session ON permission_requests(session_id);
CREATE INDEX idx_permission_requests_workspace_pending
  ON permission_requests(workspace_id) WHERE status = 'PENDING';

-- Multi-approver
CREATE TABLE request_approvals (
  id          TEXT PRIMARY KEY,
  request_id  TEXT NOT NULL REFERENCES permission_requests(id),
  user_id     TEXT NOT NULL REFERENCES users(id),
  device_id   TEXT REFERENCES device_tokens(id),
  approved_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(request_id, user_id)
);

-- Approval policies
CREATE TABLE approval_policies (
  id                 TEXT PRIMARY KEY,
  workspace_id       TEXT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  name               TEXT NOT NULL,
  match_tools        TEXT[],
  required_approvers INT NOT NULL DEFAULT 1,
  escalate_after_ms  INT NOT NULL DEFAULT 30000,
  escalate_to_user   TEXT REFERENCES users(id),
  created_at         TIMESTAMPTZ DEFAULT NOW()
);

-- Rules engine
CREATE TYPE rule_scope AS ENUM ('ORG', 'WORKSPACE', 'USER', 'SESSION');
CREATE TYPE rule_action AS ENUM ('AUTO_APPROVE', 'AUTO_DENY');

CREATE TABLE rules (
  id           TEXT PRIMARY KEY,
  org_id       TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  workspace_id TEXT REFERENCES workspaces(id) ON DELETE CASCADE,
  created_by   TEXT NOT NULL REFERENCES users(id),
  scope        rule_scope NOT NULL,
  scope_target TEXT,
  name         TEXT NOT NULL,
  description  TEXT,
  action       rule_action NOT NULL,
  priority     INT NOT NULL DEFAULT 100,
  conditions   JSONB NOT NULL,
  expires_at   TIMESTAMPTZ,
  enabled      BOOLEAN NOT NULL DEFAULT TRUE,
  hit_count    INT NOT NULL DEFAULT 0,
  last_hit_at  TIMESTAMPTZ,
  created_at   TIMESTAMPTZ DEFAULT NOW()
);

-- Delegation
CREATE TABLE delegations (
  id            TEXT PRIMARY KEY,
  delegator_id  TEXT NOT NULL REFERENCES users(id),
  delegatee_id  TEXT NOT NULL REFERENCES users(id),
  workspace_id  TEXT REFERENCES workspaces(id),
  reason        TEXT,
  starts_at     TIMESTAMPTZ NOT NULL,
  expires_at    TIMESTAMPTZ NOT NULL,
  created_at    TIMESTAMPTZ DEFAULT NOW()
);

-- Audit log (append-only)
CREATE TYPE audit_actor_type AS ENUM ('USER', 'MACHINE', 'RULES_ENGINE', 'SYSTEM', 'ADMIN');

CREATE TABLE audit_events (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id        TEXT NOT NULL REFERENCES organizations(id),
  workspace_id  TEXT REFERENCES workspaces(id),
  session_id    TEXT,
  request_id    TEXT,
  event_type    TEXT NOT NULL,
  actor_type    audit_actor_type NOT NULL,
  actor_id      TEXT NOT NULL,
  device_id     TEXT,
  ip_address    INET,
  user_agent    TEXT,
  payload       JSONB NOT NULL DEFAULT '{}',
  previous_hash TEXT,
  checksum      TEXT NOT NULL,
  created_at    TIMESTAMPTZ DEFAULT NOW()
) PARTITION BY RANGE (created_at);

CREATE OR REPLACE FUNCTION prevent_audit_mutation() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION 'audit_events is append-only'; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_no_update
  BEFORE UPDATE ON audit_events FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();
CREATE TRIGGER audit_no_delete
  BEFORE DELETE ON audit_events FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();

CREATE TABLE audit_events_default PARTITION OF audit_events DEFAULT;
```

---

## 13. WebSocket Protocol (complete)

```typescript
// ── Agent → Server ──────────────────────────────────────────────────

type AgentMessage =
  | {
      type: "auth"
      machineToken: string
      fingerprint: string
      sessionMeta: {
        agentType: "CLAUDE_CODE" | "CODEX" | "AIDER" | "GEMINI_CLI" | "OPENCODE" | "GENERIC"
        pid: number
        tty?: string
        cwd?: string
        version?: string
      }
    }
  | {
      type: "reconnect"
      sessionId: string
      inflightRequests: string[]
    }
  | {
      type: "request"
      requestId: string     // client-generated cuid
      tool: string
      input: unknown
      inputHash: string     // SHA256 of JSON.stringify(input)
      description?: string
      timeoutMs: number
      signature: string     // Ed25519 signature
      seq: number           // monotonic per session
      nonce: string         // UUID, for replay prevention
      ts: number            // unix ms
    }
  | { type: "cancel"; requestId: string }
  | { type: "ping"; ts: number }

// ── Server → Agent ──────────────────────────────────────────────────

type ServerToAgentMessage =
  | { type: "auth_ok"; sessionId: string; connectionId: string }
  | { type: "decision"; requestId: string; approved: boolean }
  | { type: "already_decided"; requestId: string; status: RequestStatus }
  | { type: "expired"; requestId: string }
  | { type: "revoked"; reason: string }
  | { type: "pong"; ts: number; serverTs: number }
  | { type: "error"; code: string; message: string }

// ── Mobile → Server ─────────────────────────────────────────────────

type MobileMessage =
  | { type: "auth"; jwtToken: string }
  | {
      type: "decide"
      requestId: string
      approved: boolean
      idempotencyKey: string  // UUID generated on button tap
    }
  | { type: "subscribe"; workspaceIds: string[] }
  | { type: "pong" }

// ── Server → Mobile ─────────────────────────────────────────────────

type ServerToMobileMessage =
  | { type: "auth_ok"; userId: string }
  | {
      type: "request"
      requestId: string
      sessionId: string
      machineId: string
      machineName: string
      tool: string
      input: unknown
      description?: string
      expiresAt: string       // ISO8601
      agentSignature: string
      signatureVerified: boolean
      seq: number
      sessionSeq: number
    }
  | {
      type: "decided"
      requestId: string
      status: RequestStatus
      decidedByDevice?: string
    }
  | { type: "already_decided"; requestId: string; currentStatus: RequestStatus }
  | {
      type: "presence"
      machineId: string
      event: "connected" | "disconnected" | "session_added" | "session_removed"
      sessionId?: string
    }
  | { type: "ping" }
  | { type: "error"; code: string; message: string }

type RequestStatus =
  | "PENDING" | "APPROVED" | "DENIED" | "EXPIRED"
  | "CANCELLED" | "AUTO_APPROVED" | "AUTO_DENIED"
```

---

## 14. API Routes

```
Auth
  POST   /auth/register
  POST   /auth/login
  POST   /auth/refresh
  DELETE /auth/logout
  GET    /auth/me
  GET    /auth/oauth/github
  GET    /auth/oauth/github/callback
  GET    /auth/oauth/google
  GET    /auth/oauth/google/callback

Organizations
  POST   /orgs
  GET    /orgs
  GET    /orgs/:orgId
  PATCH  /orgs/:orgId
  DELETE /orgs/:orgId
  POST   /orgs/:orgId/members
  GET    /orgs/:orgId/members
  PATCH  /orgs/:orgId/members/:userId
  DELETE /orgs/:orgId/members/:userId

Workspaces
  POST   /orgs/:orgId/workspaces
  GET    /orgs/:orgId/workspaces
  GET    /orgs/:orgId/workspaces/:wsId
  PATCH  /orgs/:orgId/workspaces/:wsId
  DELETE /orgs/:orgId/workspaces/:wsId

API Keys
  POST   /orgs/:orgId/workspaces/:wsId/api-keys
  GET    /orgs/:orgId/workspaces/:wsId/api-keys
  DELETE /orgs/:orgId/workspaces/:wsId/api-keys/:keyId

Machines
  POST   /machines                                 (register, uses apiKey in body)
  GET    /orgs/:orgId/workspaces/:wsId/machines
  GET    /machines/:machineId
  PATCH  /machines/:machineId
  DELETE /machines/:machineId
  POST   /machines/:machineId/rotate-token
  POST   /machines/:machineId/transfer

Sessions
  GET    /orgs/:orgId/workspaces/:wsId/sessions
  GET    /sessions/:sessionId
  DELETE /sessions/:sessionId

Requests
  GET    /orgs/:orgId/workspaces/:wsId/requests?status=PENDING&cursor=
  GET    /requests/:requestId
  POST   /requests/:requestId/decide               { approved, idempotencyKey }

Rules
  POST   /orgs/:orgId/workspaces/:wsId/rules
  GET    /orgs/:orgId/workspaces/:wsId/rules
  PATCH  /rules/:ruleId
  DELETE /rules/:ruleId
  POST   /rules/:ruleId/test                       { tool, input }

Policies
  POST   /orgs/:orgId/workspaces/:wsId/policies
  GET    /orgs/:orgId/workspaces/:wsId/policies
  PATCH  /policies/:policyId
  DELETE /policies/:policyId

Delegations
  POST   /delegations
  GET    /delegations
  DELETE /delegations/:id

Devices
  POST   /devices
  GET    /devices
  DELETE /devices/:deviceId

Audit
  GET    /orgs/:orgId/audit
  GET    /orgs/:orgId/audit/export?format=csv|json&from=&to=

WebSocket
  WS     /ws/agent
  WS     /ws/mobile
```

---

## 15. Deployment Architecture

### V1 — Single Server

```
fly.io / Railway / Render
  └── Single Bun process (HTTP + WebSocket combined)

Neon (PostgreSQL serverless)
Upstash (Redis serverless)

Mobile: Expo EAS Build + Submit to App Store / Play Store
```

### V2 — Containerized

```
fly.io multi-VM
  ├── api (N instances, stateless)
  └── ws-gateway (separate, sticky sessions)

Supabase or RDS PostgreSQL
ElastiCache or Upstash Redis

Expo EAS + OTA updates
```

### V3 — Kubernetes

```
EKS / GKE
  ├── api (HPA, stateless)
  ├── ws-gateway (connection-aware LB)
  ├── notification-service
  ├── rules-engine
  └── audit-service

RDS Aurora PostgreSQL (multi-AZ)
ElastiCache Redis Cluster
ClickHouse (audit log)
Datadog + Grafana
```

---

## 16. Roadmap

### V1 — Foundation (weeks 1–6)

Working end-to-end for a single developer.

- Auth (email/password + GitHub OAuth)
- Single org + workspace
- Machine registration + token issuance
- Claude Code PreToolUse hook
- WebSocket: agent ↔ server ↔ mobile
- Mobile iOS + Android (Expo)
- Push notifications (FCM + APNs via Expo push service)
- Lock screen Yes/No actions
- Basic rules (auto-approve Read, auto-deny patterns)
- Append-only audit log
- Ed25519 request signing
- Replay attack defense (nonce + timestamp window)
- Codex PTY wrapper

**Ship criteria:** solo developer approves Claude Code hooks from phone on a tmux session.

### V2 — Team Features (weeks 7–14)

Usable for 5–20 person engineering team.

- Full RBAC (OWNER/ADMIN/APPROVER/VIEWER)
- Multi-org, multi-workspace
- Approval delegation
- Escalation policies
- Multi-approver policies (M-of-N)
- Rules engine with full condition schema
- Temporary rules
- Bulk approval in mobile
- Fast swipe mode
- Multi-device sync
- Offline queue + reconnect decision replay
- Machine ownership transfer
- Audit export (CSV, JSON)
- Aider + Gemini CLI adapters
- Presence system
- Zombie session cleanup
- SIEM webhook

**Ship criteria:** team running AI agents in production can centrally govern all tool approvals.

### V3 — Enterprise (weeks 15–26)

- SSO (SAML 2.0, OIDC)
- Audit hash chain tamper detection
- ClickHouse audit log migration
- Kubernetes deployment with separate services
- OpenCode + generic shell adapters
- Custom plugin API
- Security anomaly alerts
- Compliance export (SOC2, ISO27001 evidence)
- Admin web dashboard
- API client SDKs (Python, TypeScript, Go)
- Machine auto-discovery (AWS inventory, Kubernetes pod scan)
- Approval policy templates
- Grafana dashboard + /metrics endpoint

---

## Design Decisions & Trade-offs

**No Redis at V1.** In-process Map for WS hub. Add Redis only when second server instance needed. Premature Redis = operational complexity for zero gain.

**Expo push service wraps FCM + APNs at V1.** Avoids managing Firebase credentials and APNs certs separately. Migrate to direct FCM/APNs at V2 when push volume or customization needs it.

**No separate WS gateway at V1.** Same Hono server handles HTTP + WS. Extract when horizontally scaling.

**Ed25519 signing from day 1.** Retrofitting cryptographic signing is a protocol break. Security architecture that isn't there at start never gets added.

**Immutable audit log from day 1.** It's 5 lines of SQL. No reason to defer.

**SAML/OIDC at V3 only.** GitHub OAuth covers 95% of target audience in V1/V2. SAML is enterprise and belongs at V3.

**ClickHouse at V3 only.** PostgreSQL with monthly partitions handles audit log at reasonable scale for years.
