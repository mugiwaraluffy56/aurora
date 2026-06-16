# Nod — Full Repository Tree

```
tapguard/
│
├── docs/
│   ├── ARCHITECTURE.md
│   ├── TREE.md
│   ├── api/
│   │   ├── openapi.yaml                  ← full OpenAPI 3.1 spec
│   │   └── websocket-protocol.md         ← WS message reference
│   ├── guides/
│   │   ├── quickstart.md
│   │   ├── claude-code-setup.md
│   │   ├── codex-setup.md
│   │   ├── aider-setup.md
│   │   ├── gemini-cli-setup.md
│   │   ├── aws-setup.md
│   │   ├── docker-setup.md
│   │   └── self-hosting.md
│   └── decisions/
│       ├── 001-go-server-rust-agent.md
│       ├── 002-ed25519-signing.md
│       ├── 003-session-vs-machine-identity.md
│       └── 004-audit-append-only.md
│
├── server/                               ← Go server
│   ├── cmd/
│   │   └── server/
│   │       └── main.go                   ← entrypoint, wires everything
│   │
│   ├── internal/
│   │   │
│   │   ├── config/
│   │   │   ├── config.go                 ← Config struct, env loading
│   │   │   └── validate.go               ← config validation
│   │   │
│   │   ├── db/
│   │   │   ├── db.go                     ← pgx pool setup
│   │   │   ├── tx.go                     ← transaction helpers
│   │   │   └── generated/                ← sqlc output (never edit manually)
│   │   │       ├── db.go
│   │   │       ├── models.go
│   │   │       ├── querier.go
│   │   │       ├── audit.sql.go
│   │   │       ├── auth.sql.go
│   │   │       ├── delegations.sql.go
│   │   │       ├── devices.sql.go
│   │   │       ├── machines.sql.go
│   │   │       ├── members.sql.go
│   │   │       ├── orgs.sql.go
│   │   │       ├── policies.sql.go
│   │   │       ├── requests.sql.go
│   │   │       ├── rules.sql.go
│   │   │       ├── sessions.sql.go
│   │   │       └── workspaces.sql.go
│   │   │
│   │   ├── redis/
│   │   │   ├── client.go                 ← go-redis setup
│   │   │   ├── keys.go                   ← all Redis key templates
│   │   │   ├── presence.go               ← presence HSET/EXPIRE ops
│   │   │   ├── offline_queue.go          ← ZADD/ZRANGEBYSCORE ops
│   │   │   ├── nonce.go                  ← nonce dedup SET NX ops
│   │   │   ├── blocklist.go              ← token revocation blocklist
│   │   │   ├── rules_cache.go            ← workspace rules cache
│   │   │   └── escalation.go             ← escalation TTL keys
│   │   │
│   │   ├── auth/
│   │   │   ├── service.go                ← AuthService interface + impl
│   │   │   ├── jwt.go                    ← Ed25519 JWT sign/verify
│   │   │   ├── password.go               ← argon2id hash/verify
│   │   │   ├── tokens.go                 ← access + refresh token logic
│   │   │   ├── machine_token.go          ← machine token issue/verify/rotate
│   │   │   ├── api_key.go                ← API key generate/hash/verify
│   │   │   ├── oauth/
│   │   │   │   ├── github.go             ← GitHub OAuth flow
│   │   │   │   └── google.go             ← Google OAuth flow
│   │   │   └── middleware.go             ← chi middleware: requireAuth, requireRole
│   │   │
│   │   ├── middleware/
│   │   │   ├── request_id.go             ← inject X-Request-ID
│   │   │   ├── logger.go                 ← structured request logging
│   │   │   ├── recover.go                ← panic recovery
│   │   │   ├── rate_limit.go             ← per-IP + per-workspace rate limits
│   │   │   ├── cors.go                   ← strict origin CORS
│   │   │   └── timeout.go                ← per-route timeouts
│   │   │
│   │   ├── api/
│   │   │   ├── router.go                 ← chi router setup, all routes wired
│   │   │   ├── respond.go                ← JSON response helpers
│   │   │   ├── errors.go                 ← error types → HTTP codes
│   │   │   ├── validate.go               ← request body validation helpers
│   │   │   │
│   │   │   ├── auth/
│   │   │   │   ├── handler.go            ← POST /auth/register, /login, /refresh, /logout
│   │   │   │   ├── me.go                 ← GET /auth/me
│   │   │   │   └── oauth.go              ← GET /auth/oauth/github, /google + callbacks
│   │   │   │
│   │   │   ├── orgs/
│   │   │   │   ├── handler.go            ← CRUD /orgs
│   │   │   │   └── members.go            ← /orgs/:orgId/members CRUD
│   │   │   │
│   │   │   ├── workspaces/
│   │   │   │   └── handler.go            ← CRUD /orgs/:orgId/workspaces
│   │   │   │
│   │   │   ├── api_keys/
│   │   │   │   └── handler.go            ← CRUD /orgs/:orgId/workspaces/:wsId/api-keys
│   │   │   │
│   │   │   ├── machines/
│   │   │   │   ├── handler.go            ← register, list, get, revoke
│   │   │   │   ├── rotate.go             ← POST /machines/:id/rotate-token
│   │   │   │   └── transfer.go           ← POST /machines/:id/transfer
│   │   │   │
│   │   │   ├── sessions/
│   │   │   │   └── handler.go            ← list, get, force-end
│   │   │   │
│   │   │   ├── requests/
│   │   │   │   ├── handler.go            ← list, get
│   │   │   │   └── decide.go             ← POST /requests/:id/decide (idempotent)
│   │   │   │
│   │   │   ├── rules/
│   │   │   │   ├── handler.go            ← CRUD /orgs/:orgId/workspaces/:wsId/rules
│   │   │   │   └── test.go               ← POST /rules/:id/test
│   │   │   │
│   │   │   ├── policies/
│   │   │   │   └── handler.go            ← CRUD approval policies
│   │   │   │
│   │   │   ├── delegations/
│   │   │   │   └── handler.go            ← CRUD delegations
│   │   │   │
│   │   │   ├── devices/
│   │   │   │   └── handler.go            ← register/list/revoke device tokens
│   │   │   │
│   │   │   └── audit/
│   │   │       ├── handler.go            ← GET /orgs/:orgId/audit (paginated)
│   │   │       └── export.go             ← GET /orgs/:orgId/audit/export (streamed)
│   │   │
│   │   ├── ws/
│   │   │   ├── hub.go                    ← connection registry, pub/sub, fanout
│   │   │   ├── agent_handler.go          ← WS /ws/agent — auth, recv, dispatch
│   │   │   ├── mobile_handler.go         ← WS /ws/mobile — auth, recv, dispatch
│   │   │   ├── connection.go             ← Conn type, send queue, backpressure
│   │   │   ├── protocol.go               ← all message types (Agent* + Mobile*)
│   │   │   ├── auth.go                   ← first-message auth, 5s timeout
│   │   │   ├── heartbeat.go              ← ping/pong goroutine per conn
│   │   │   └── router.go                 ← message type → handler dispatch
│   │   │
│   │   ├── machines/
│   │   │   ├── service.go                ← MachineService interface + impl
│   │   │   ├── fingerprint.go            ← fingerprint validation
│   │   │   ├── register.go               ← registration flow
│   │   │   ├── revoke.go                 ← revocation + Redis blocklist
│   │   │   └── rotate.go                 ← token rotation with overlap
│   │   │
│   │   ├── sessions/
│   │   │   ├── service.go                ← SessionService interface + impl
│   │   │   ├── register.go               ← session create on WS connect
│   │   │   └── lifecycle.go              ← status transitions
│   │   │
│   │   ├── requests/
│   │   │   ├── service.go                ← RequestService interface + impl
│   │   │   ├── create.go                 ← validate signature, store, fan out
│   │   │   ├── decide.go                 ← optimistic lock update, notify
│   │   │   ├── expire.go                 ← TTL expiry handler
│   │   │   └── cancel.go                 ← agent-initiated cancel
│   │   │
│   │   ├── rules/
│   │   │   ├── engine.go                 ← RulesEngine interface + impl
│   │   │   ├── evaluate.go               ← scope ordering + first-match
│   │   │   ├── conditions.go             ← condition evaluators (eq/in/matches/etc)
│   │   │   ├── cache.go                  ← Redis cache layer for rules
│   │   │   └── conflict.go               ← conflict resolution (DENY_WINS etc)
│   │   │
│   │   ├── audit/
│   │   │   ├── service.go                ← AuditService interface + impl
│   │   │   ├── emit.go                   ← append event with hash chain
│   │   │   ├── hash_chain.go             ← previous_hash + checksum computation
│   │   │   ├── export.go                 ← streaming CSV/JSON/NDJSON
│   │   │   └── siem.go                   ← webhook delivery to SIEM
│   │   │
│   │   ├── notifications/
│   │   │   ├── service.go                ← NotificationService interface + impl
│   │   │   ├── expo.go                   ← Expo push API (V1)
│   │   │   ├── fcm.go                    ← FCM direct (V2+)
│   │   │   ├── apns.go                   ← APNs direct (V2+)
│   │   │   ├── batch.go                  ← batch up to 500 tokens
│   │   │   └── payload.go                ← notification payload builder
│   │   │
│   │   ├── presence/
│   │   │   ├── service.go                ← PresenceService interface + impl
│   │   │   ├── track.go                  ← HSET on connect/disconnect
│   │   │   └── broadcast.go              ← fan out presence events to mobile WS
│   │   │
│   │   ├── escalation/
│   │   │   ├── service.go                ← EscalationService interface + impl
│   │   │   ├── schedule.go               ← set Redis TTL key on request create
│   │   │   └── trigger.go                ← fired when TTL expires
│   │   │
│   │   ├── scheduler/
│   │   │   ├── scheduler.go              ← background job runner
│   │   │   ├── zombie_cleanup.go         ← mark ZOMBIE sessions every 60s
│   │   │   ├── expire_requests.go        ← expire PENDING past TTL
│   │   │   ├── expire_rules.go           ← disable expired rules
│   │   │   └── expire_delegations.go     ← expire ended delegations
│   │   │
│   │   ├── crypto/
│   │   │   ├── ed25519.go                ← Ed25519 verify (server side)
│   │   │   └── nonce.go                  ← nonce validation against Redis
│   │   │
│   │   └── orgs/
│   │       └── service.go                ← OrgService interface + impl
│   │
│   ├── pkg/                              ← exported, reusable across cmds
│   │   ├── id/
│   │   │   └── id.go                     ← cuid2 generator
│   │   ├── ptr/
│   │   │   └── ptr.go                    ← pointer helpers
│   │   ├── timeutil/
│   │   │   └── timeutil.go
│   │   └── httputil/
│   │       └── client.go                 ← shared HTTP client (OAuth etc)
│   │
│   ├── migrations/                       ← SQL migrations (golang-migrate)
│   │   ├── 001_init.up.sql
│   │   ├── 001_init.down.sql
│   │   ├── 002_audit_partitions.up.sql
│   │   ├── 002_audit_partitions.down.sql
│   │   ├── 003_rules.up.sql
│   │   ├── 003_rules.down.sql
│   │   ├── 004_delegations.up.sql
│   │   └── 004_delegations.down.sql
│   │
│   ├── sqlc/
│   │   ├── sqlc.yaml                     ← sqlc config
│   │   └── queries/                      ← raw SQL queries (sqlc input)
│   │       ├── audit.sql
│   │       ├── auth.sql
│   │       ├── delegations.sql
│   │       ├── devices.sql
│   │       ├── machines.sql
│   │       ├── members.sql
│   │       ├── orgs.sql
│   │       ├── policies.sql
│   │       ├── requests.sql
│   │       ├── rules.sql
│   │       ├── sessions.sql
│   │       └── workspaces.sql
│   │
│   ├── go.mod
│   ├── go.sum
│   ├── .env.example
│   └── Makefile                          ← make dev, make migrate, make sqlc, make test
│
├── agent/                                ← Rust CLI + daemon
│   ├── src/
│   │   ├── main.rs                       ← clap CLI root, subcommand dispatch
│   │   │
│   │   ├── cli/
│   │   │   ├── mod.rs
│   │   │   ├── init.rs                   ← tap-guard init (machine registration)
│   │   │   ├── hook.rs                   ← tap-guard hook (Claude Code PreToolUse)
│   │   │   ├── exec.rs                   ← tap-guard exec (PTY wrapper for Codex/Aider)
│   │   │   ├── ask.rs                    ← tap-guard ask (generic one-shot approval)
│   │   │   ├── daemon.rs                 ← tap-guard daemon (start background daemon)
│   │   │   ├── status.rs                 ← tap-guard status (show machine/session state)
│   │   │   ├── rules.rs                  ← tap-guard rules list/add/remove (local shortcuts)
│   │   │   └── rotate.rs                 ← tap-guard rotate-token
│   │   │
│   │   ├── daemon/
│   │   │   ├── mod.rs
│   │   │   ├── server.rs                 ← Unix socket server (listens for hook requests)
│   │   │   ├── client.rs                 ← Unix socket client (used by hook binary)
│   │   │   ├── request_queue.rs          ← in-flight request map (requestId → oneshot)
│   │   │   └── lifecycle.rs              ← PID file, shutdown, restart detection
│   │   │
│   │   ├── ws/
│   │   │   ├── mod.rs
│   │   │   ├── client.rs                 ← tokio-tungstenite WS client
│   │   │   ├── reconnect.rs              ← backoff reconnect loop
│   │   │   ├── auth.rs                   ← send auth message on connect
│   │   │   ├── heartbeat.rs              ← ping/pong task
│   │   │   └── protocol.rs               ← AgentMessage / ServerToAgentMessage types
│   │   │
│   │   ├── crypto/
│   │   │   ├── mod.rs
│   │   │   ├── signing.rs                ← Ed25519 sign (ed25519-dalek)
│   │   │   ├── fingerprint.rs            ← machine fingerprint generation
│   │   │   ├── nonce.rs                  ← UUID nonce generation
│   │   │   └── keypair.rs                ← generate/load/save keypair (0600 file)
│   │   │
│   │   ├── pty/
│   │   │   ├── mod.rs
│   │   │   ├── wrapper.rs                ← PTY fork + I/O intercept
│   │   │   ├── patterns.rs               ← prompt pattern matching per agent type
│   │   │   └── inject.rs                 ← write y/n into PTY master fd
│   │   │
│   │   ├── adapters/
│   │   │   ├── mod.rs
│   │   │   ├── claude_code.rs            ← parse Claude Code hook stdin JSON
│   │   │   ├── codex.rs                  ← Codex prompt patterns
│   │   │   ├── aider.rs                  ← Aider prompt patterns
│   │   │   ├── gemini.rs                 ← Gemini CLI patterns
│   │   │   └── generic.rs                ← generic shell approval
│   │   │
│   │   ├── config/
│   │   │   ├── mod.rs
│   │   │   ├── types.rs                  ← Config, AgentConfig, AdapterConfig structs
│   │   │   ├── load.rs                   ← load ~/.tap-guard/config.toml
│   │   │   └── paths.rs                  ← ~/.tap-guard/* path constants
│   │   │
│   │   ├── session/
│   │   │   ├── mod.rs
│   │   │   ├── id.rs                     ← sessionId generation
│   │   │   └── state.rs                  ← in-memory session state (seq counter etc)
│   │   │
│   │   ├── http/
│   │   │   ├── mod.rs
│   │   │   └── client.rs                 ← reqwest client for registration + token rotation
│   │   │
│   │   └── error.rs                      ← NodError enum, thiserror
│   │
│   ├── tests/
│   │   ├── integration/
│   │   │   ├── hook_test.rs
│   │   │   ├── daemon_test.rs
│   │   │   └── ws_reconnect_test.rs
│   │   └── unit/
│   │       ├── signing_test.rs
│   │       ├── fingerprint_test.rs
│   │       └── conditions_test.rs
│   │
│   ├── Cargo.toml
│   ├── Cargo.lock
│   ├── build.rs                          ← cross-compile targets setup
│   └── .cargo/
│       └── config.toml                   ← cross-compile target configs
│
├── mobile/                               ← Expo React Native
│   ├── app/
│   │   ├── _layout.tsx                   ← root layout, auth gate, WS provider
│   │   │
│   │   ├── (auth)/
│   │   │   ├── _layout.tsx
│   │   │   ├── index.tsx                 ← login screen
│   │   │   ├── register.tsx              ← register screen
│   │   │   └── forgot-password.tsx
│   │   │
│   │   └── (app)/
│   │       ├── _layout.tsx               ← tab layout
│   │       │
│   │       ├── index.tsx                 ← approval feed (home tab)
│   │       │
│   │       ├── sessions/
│   │       │   ├── index.tsx             ← sessions list (grouped by machine)
│   │       │   └── [sessionId]/
│   │       │       ├── index.tsx         ← session detail + pending requests
│   │       │       └── history.tsx       ← past decisions for this session
│   │       │
│   │       ├── history/
│   │       │   └── index.tsx             ← all decisions, filterable
│   │       │
│   │       ├── rules/
│   │       │   ├── index.tsx             ← rules list
│   │       │   ├── new.tsx               ← create rule
│   │       │   └── [ruleId]/
│   │       │       └── edit.tsx
│   │       │
│   │       └── settings/
│   │           ├── index.tsx             ← settings root
│   │           ├── profile.tsx           ← name, avatar, email
│   │           ├── devices.tsx           ← registered devices
│   │           ├── machines.tsx          ← machines in workspace
│   │           ├── delegates.tsx         ← approval delegations
│   │           ├── notifications.tsx     ← push prefs, do-not-disturb
│   │           └── workspace.tsx         ← workspace picker, org switcher
│   │
│   ├── components/
│   │   ├── approval/
│   │   │   ├── ApprovalCard.tsx          ← main card: tool, command, yes/no, sig badge
│   │   │   ├── ApprovalFeed.tsx          ← live feed of pending approvals
│   │   │   ├── SwipeableApproval.tsx     ← swipe right=approve left=deny
│   │   │   ├── BulkApprovalSheet.tsx     ← bulk approve/deny sheet
│   │   │   ├── ApprovalDetail.tsx        ← full detail modal
│   │   │   ├── DecisionBadge.tsx         ← APPROVED/DENIED/EXPIRED status chip
│   │   │   ├── SignatureBadge.tsx         ← "✓ Verified — prod-api-1"
│   │   │   ├── ExpiryTimer.tsx           ← countdown ring
│   │   │   └── EmptyFeed.tsx             ← idle state illustration
│   │   │
│   │   ├── session/
│   │   │   ├── SessionCard.tsx           ← single session row
│   │   │   ├── MachineGroup.tsx          ← machine header + sessions under it
│   │   │   ├── SessionList.tsx           ← full grouped list
│   │   │   ├── PresenceDot.tsx           ← green/grey connected indicator
│   │   │   └── AgentTypeBadge.tsx        ← CLAUDE_CODE / CODEX chip
│   │   │
│   │   ├── rules/
│   │   │   ├── RuleCard.tsx
│   │   │   ├── RuleConditionBuilder.tsx  ← visual condition editor
│   │   │   └── RuleTestResult.tsx
│   │   │
│   │   └── ui/
│   │       ├── Button.tsx
│   │       ├── Badge.tsx
│   │       ├── Sheet.tsx                 ← bottom sheet wrapper
│   │       ├── Modal.tsx
│   │       ├── Input.tsx
│   │       ├── Avatar.tsx
│   │       ├── Skeleton.tsx              ← loading placeholders
│   │       ├── Toast.tsx
│   │       ├── Divider.tsx
│   │       ├── KeyboardAvoid.tsx
│   │       └── SafeArea.tsx
│   │
│   ├── hooks/
│   │   ├── useWebSocket.ts               ← WS connection, reconnect, message dispatch
│   │   ├── useApprovals.ts               ← pending + decided approvals, decide()
│   │   ├── useSessions.ts                ← session list, presence updates
│   │   ├── usePushNotifications.ts       ← register token, handle notification actions
│   │   ├── useAuth.ts                    ← login, logout, token refresh
│   │   ├── useOrg.ts                     ← org + workspace switcher
│   │   ├── useRules.ts                   ← rules CRUD
│   │   └── useBulkApproval.ts            ← bulk selection state
│   │
│   ├── lib/
│   │   ├── api.ts                        ← typed fetch client, auth header injection
│   │   ├── ws.ts                         ← reconnecting WS wrapper
│   │   ├── storage.ts                    ← expo-secure-store typed wrappers
│   │   ├── notifications.ts              ← Expo push token + category registration
│   │   ├── idempotency.ts                ← UUID generation for decision keys
│   │   └── time.ts                       ← relative time formatting
│   │
│   ├── store/
│   │   ├── auth.ts                       ← Zustand: user, tokens, org context
│   │   ├── approvals.ts                  ← Zustand: pending map, optimistic updates
│   │   ├── sessions.ts                   ← Zustand: sessions + presence state
│   │   └── ui.ts                         ← Zustand: toasts, sheets, modals
│   │
│   ├── constants/
│   │   ├── colors.ts
│   │   ├── layout.ts
│   │   └── api.ts                        ← API base URL, WS URL
│   │
│   ├── assets/
│   │   ├── images/
│   │   │   ├── icon.png
│   │   │   ├── splash.png
│   │   │   └── adaptive-icon.png
│   │   └── fonts/
│   │
│   ├── app.json                          ← Expo config
│   ├── babel.config.js
│   ├── tsconfig.json
│   ├── tailwind.config.js                ← NativeWind
│   └── package.json
│
├── deploy/
│   ├── docker/
│   │   ├── server.Dockerfile
│   │   └── docker-compose.yml            ← server + postgres + redis for local dev
│   ├── fly/
│   │   ├── fly.toml                      ← fly.io config
│   │   └── fly.secrets.example
│   ├── k8s/                              ← V3 Kubernetes manifests
│   │   ├── namespace.yaml
│   │   ├── server-deployment.yaml
│   │   ├── ws-gateway-deployment.yaml
│   │   ├── notification-deployment.yaml
│   │   ├── postgres-statefulset.yaml
│   │   ├── redis-statefulset.yaml
│   │   ├── ingress.yaml
│   │   └── secrets.yaml.example
│   └── scripts/
│       ├── setup-dev.sh                  ← one-shot local dev setup
│       ├── migrate.sh
│       └── generate-keys.sh              ← generate server Ed25519 keypair
│
├── .github/
│   └── workflows/
│       ├── server-ci.yml                 ← Go test + build
│       ├── agent-ci.yml                  ← Rust test + cross-compile (linux/mac/win/arm)
│       ├── mobile-ci.yml                 ← Expo type-check + EAS build
│       └── release.yml                   ← tag → GitHub release + binary assets
│
└── Makefile                              ← root: make dev, make build, make test-all
```

---

## Key File Counts

| Component | Files | Language |
|-----------|-------|----------|
| Server | ~65 | Go |
| Agent | ~35 | Rust |
| Mobile | ~55 | TypeScript/TSX |
| SQL migrations | 8 | SQL |
| sqlc queries | 12 | SQL |
| Deploy/CI | 12 | YAML/Shell |
| Docs | 12 | Markdown |
| **Total** | **~199** | |

---

## Module Boundaries

```
server/internal/     ← never imported outside server/
server/pkg/          ← safe to import from server cmd/ only
agent/src/daemon/    ← process boundary (Unix socket)
agent/src/cli/       ← thin command handlers only, no business logic
mobile/lib/          ← pure functions, no React
mobile/hooks/        ← React hooks, depend on lib/ + store/
mobile/store/        ← Zustand stores, no React
mobile/components/   ← React components, depend on hooks/ + store/ + lib/
```

---

## Dependency Rules

```
Server:
  handlers → services → db/redis
  handlers must not import db directly
  services must not import handlers
  redis package must not import db package
  audit service imported by every other service (emit events)

Agent:
  cli/ → daemon/client → (daemon/ → ws/ → crypto/)
  hook binary communicates with daemon only via Unix socket
  daemon owns the WS connection (one connection per machine)
  adapters/ is pure parsing, zero I/O

Mobile:
  components/ must not call fetch() directly
  all server calls go through lib/api.ts
  all WS interaction goes through hooks/useWebSocket.ts
  store/ must not import components/
```
