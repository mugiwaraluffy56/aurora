package api

import (
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
	"github.com/rs/zerolog"

	apiAudit "github.com/nod/server/internal/api/audit"
	apiAuth "github.com/nod/server/internal/api/auth"
	apiDelegations "github.com/nod/server/internal/api/delegations"
	apiDevices "github.com/nod/server/internal/api/devices"
	apiKeys "github.com/nod/server/internal/api/api_keys"
	apiMachines "github.com/nod/server/internal/api/machines"
	apiOrgs "github.com/nod/server/internal/api/orgs"
	apiPolicies "github.com/nod/server/internal/api/policies"
	apiRequests "github.com/nod/server/internal/api/requests"
	apiRules "github.com/nod/server/internal/api/rules"
	apiSessions "github.com/nod/server/internal/api/sessions"
	apiWorkspaces "github.com/nod/server/internal/api/workspaces"
	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/auth"
	"github.com/nod/server/internal/auth/oauth"
	"github.com/nod/server/internal/config"
	"github.com/nod/server/internal/machines"
	"github.com/nod/server/internal/middleware"
	"github.com/nod/server/internal/requests"
	"github.com/nod/server/internal/rules"
	"github.com/nod/server/internal/sessions"
	"github.com/nod/server/internal/ws"
)

// RouterDeps holds all dependencies needed to build the HTTP router.
type RouterDeps struct {
	DB          *pgxpool.Pool
	RDB         *redis.Client
	Cfg         *config.Config
	AuditSvc    audit.AuditService
	MachineSvc  machines.MachineService
	SessionSvc  sessions.SessionService
	RequestSvc  requests.RequestService
	RulesEngine rules.RulesEngine
	Hub         *ws.Hub
	Log         zerolog.Logger
}

// NewRouter builds and returns the chi router with all routes and middleware mounted.
func NewRouter(deps RouterDeps) http.Handler {
	r := chi.NewRouter()

	// Global middleware — Timeout is NOT here. It is scoped to the HTTP routes
	// group below so WebSocket connections (long-lived) are not killed at 30s.
	r.Use(middleware.RequestID)
	r.Use(middleware.Logger(deps.Log))
	r.Use(middleware.Recover(deps.Log))
	r.Use(middleware.RateLimitAPI)
	r.Use(middleware.CORS([]string{})) // open CORS in dev; restrict in prod via env

	// OAuth providers.
	githubProvider := &oauth.GitHubProvider{
		ClientID:     deps.Cfg.GitHubClientID,
		ClientSecret: deps.Cfg.GitHubClientSecret,
		RedirectURL:  deps.Cfg.OAuthRedirectBase + "/auth/oauth/github/callback",
	}
	googleProvider := &oauth.GoogleProvider{
		ClientID:     deps.Cfg.GoogleClientID,
		ClientSecret: deps.Cfg.GoogleClientSecret,
		RedirectURL:  deps.Cfg.OAuthRedirectBase + "/auth/oauth/google/callback",
	}

	// Handlers.
	authH := apiAuth.NewHandler(deps.DB, deps.AuditSvc, deps.Log)
	oauthH := apiAuth.NewOAuthHandler(deps.DB, githubProvider, googleProvider, deps.AuditSvc, deps.Log)
	orgH := apiOrgs.NewHandler(deps.DB, deps.AuditSvc, deps.Log)
	orgMembersH := apiOrgs.NewMembersHandler(deps.DB, deps.AuditSvc, deps.Log)
	wsH := apiWorkspaces.NewHandler(deps.DB, deps.AuditSvc, deps.Log)
	apiKeyH := apiKeys.NewHandler(deps.DB, deps.AuditSvc, deps.Log)
	machineH := apiMachines.NewHandler(deps.DB, deps.MachineSvc, deps.AuditSvc, deps.Log)
	sessionH := apiSessions.NewHandler(deps.DB, deps.SessionSvc, deps.AuditSvc, deps.Log)
	requestH := apiRequests.NewHandler(deps.DB, deps.RDB, deps.RequestSvc, deps.AuditSvc, deps.Log)
	rulesH := apiRules.NewHandler(deps.DB, deps.RDB, deps.AuditSvc, deps.Log)
	policyH := apiPolicies.NewHandler(deps.DB, deps.AuditSvc, deps.Log)
	delegH := apiDelegations.NewHandler(deps.DB, deps.AuditSvc, deps.Log)
	deviceH := apiDevices.NewHandler(deps.DB, deps.AuditSvc, deps.Log)
	auditH := apiAudit.NewHandler(deps.DB, deps.AuditSvc, deps.Log)

	// WebSocket handlers.
	agentWsH := ws.NewAgentHandler(deps.Hub, deps.DB, deps.RDB, deps.RulesEngine, deps.AuditSvc, deps.Log)
	mobileWsH := ws.NewMobileHandler(deps.Hub, deps.DB, deps.RDB, deps.AuditSvc, deps.Log)
	deviceWsH := ws.NewDeviceHandler(deps.Hub, deps.DB, deps.RDB, deps.AuditSvc, deps.Log)

	// WebSocket routes — registered directly on root router, outside the Timeout
	// middleware group below. Long-lived connections must not inherit a 30s deadline.
	r.Get("/ws/agent", agentWsH.ServeHTTP)
	r.Get("/ws/mobile", mobileWsH.ServeHTTP)
	r.Get("/ws/device", deviceWsH.ServeHTTP)

	// All standard HTTP routes — scoped with 30s request timeout.
	r.Group(func(r chi.Router) {
		r.Use(middleware.Timeout)

		// Auth routes (rate-limited).
		r.Group(func(r chi.Router) {
			r.Use(middleware.RateLimitAuth)
			r.Post("/auth/register", authH.Register)
			r.Post("/auth/login", authH.Login)
			r.Post("/auth/refresh", authH.Refresh)
			r.Get("/auth/oauth/github", oauthH.GitHubRedirect)
			r.Get("/auth/oauth/github/callback", oauthH.GitHubCallback)
			r.Get("/auth/oauth/google", oauthH.GoogleRedirect)
			r.Get("/auth/oauth/google/callback", oauthH.GoogleCallback)
		})

		// Protected routes.
		r.Group(func(r chi.Router) {
			r.Use(auth.RequireAuth(deps.RDB))

			r.Delete("/auth/logout", authH.Logout)
			r.Get("/auth/me", authH.Me)

			// Org routes.
			r.Post("/orgs", orgH.Create)
			r.Get("/orgs", orgH.List)
			r.Get("/orgs/{orgId}", orgH.Get)
			r.Patch("/orgs/{orgId}", orgH.Update)
			r.Delete("/orgs/{orgId}", orgH.Delete)

			r.Post("/orgs/{orgId}/members", orgMembersH.Invite)
			r.Get("/orgs/{orgId}/members", orgMembersH.List)
			r.Patch("/orgs/{orgId}/members/{userId}", orgMembersH.UpdateRole)
			r.Delete("/orgs/{orgId}/members/{userId}", orgMembersH.Remove)

			// Workspace routes.
			r.Post("/orgs/{orgId}/workspaces", wsH.Create)
			r.Get("/orgs/{orgId}/workspaces", wsH.List)
			r.Get("/orgs/{orgId}/workspaces/{wsId}", wsH.Get)
			r.Patch("/orgs/{orgId}/workspaces/{wsId}", wsH.Update)
			r.Delete("/orgs/{orgId}/workspaces/{wsId}", wsH.Delete)

			// API key routes.
			r.Post("/orgs/{orgId}/workspaces/{wsId}/api-keys", apiKeyH.Create)
			r.Get("/orgs/{orgId}/workspaces/{wsId}/api-keys", apiKeyH.List)
			r.Delete("/orgs/{orgId}/workspaces/{wsId}/api-keys/{keyId}", apiKeyH.Revoke)

			// Machine routes.
			r.Get("/orgs/{orgId}/workspaces/{wsId}/machines", machineH.List)
			r.Get("/machines/{machineId}", machineH.Get)
			r.Patch("/machines/{machineId}", machineH.Update)
			r.Delete("/machines/{machineId}", machineH.Revoke)
			r.Post("/machines/{machineId}/rotate-token", machineH.RotateToken)
			r.Post("/machines/{machineId}/transfer", machineH.Transfer)

			// Session routes.
			r.Get("/orgs/{orgId}/workspaces/{wsId}/sessions", sessionH.List)
			r.Get("/sessions/{sessionId}", sessionH.Get)
			r.Delete("/sessions/{sessionId}", sessionH.End)

			// Request routes.
			r.Get("/orgs/{orgId}/workspaces/{wsId}/requests", requestH.List)
			r.Get("/requests/{requestId}", requestH.Get)
			r.Post("/requests/{requestId}/decide", requestH.Decide)

			// Rules routes.
			r.Post("/orgs/{orgId}/workspaces/{wsId}/rules", rulesH.Create)
			r.Get("/orgs/{orgId}/workspaces/{wsId}/rules", rulesH.List)
			r.Patch("/rules/{ruleId}", rulesH.Update)
			r.Delete("/rules/{ruleId}", rulesH.Delete)
			r.Post("/rules/{ruleId}/test", rulesH.Test)

			// Policy routes.
			r.Post("/orgs/{orgId}/workspaces/{wsId}/policies", policyH.Create)
			r.Get("/orgs/{orgId}/workspaces/{wsId}/policies", policyH.List)
			r.Patch("/policies/{policyId}", policyH.Update)
			r.Delete("/policies/{policyId}", policyH.Delete)

			// Delegation routes.
			r.Post("/delegations", delegH.Create)
			r.Get("/delegations", delegH.List)
			r.Delete("/delegations/{id}", delegH.Delete)

			// Device routes.
			r.Post("/devices", deviceH.Register)
			r.Get("/devices", deviceH.List)
			r.Delete("/devices/{deviceId}", deviceH.Unregister)

			// Audit routes.
			r.Get("/orgs/{orgId}/audit", auditH.List)
			r.Get("/orgs/{orgId}/audit/export", auditH.Export)
		})

		// Machine registration — API key auth only.
		r.With(auth.RequireAPIKey).Post("/machines", machineH.Register)
	})

	return r
}
