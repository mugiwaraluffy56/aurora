package rules

import (
	"encoding/json"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-playground/validator/v10"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
	"github.com/rs/zerolog"

	"github.com/nod/server/internal/respond"
	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/auth"
	"github.com/nod/server/pkg/id"
)

var validate = validator.New()

// Handler handles rules endpoints.
type Handler struct {
	db    *pgxpool.Pool
	rdb   *redis.Client
	audit audit.AuditService
	log   zerolog.Logger
}

// NewHandler creates a Handler.
func NewHandler(db *pgxpool.Pool, rdb *redis.Client, auditSvc audit.AuditService, log zerolog.Logger) *Handler {
	return &Handler{db: db, rdb: rdb, audit: auditSvc, log: log}
}

type createRuleRequest struct {
	Name        string         `json:"name" validate:"required"`
	Description string         `json:"description"`
	Priority    int            `json:"priority"`
	Action      string         `json:"action" validate:"required,oneof=AUTO_APPROVE AUTO_DENY REQUIRE_APPROVAL"`
	Scope       string         `json:"scope" validate:"required,oneof=SESSION USER WORKSPACE ORG"`
	ScopeID     string         `json:"scopeId" validate:"required"`
	Conditions  map[string]any `json:"conditions"`
	ExpiresAt   *string        `json:"expiresAt"`
}

// Create creates a new rule.
func (h *Handler) Create(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	wsID := chi.URLParam(r, "wsId")
	orgID := chi.URLParam(r, "orgId")

	var req createRuleRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := validate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	condJSON, _ := json.Marshal(req.Conditions)
	ruleID := id.New()

	if req.Priority == 0 {
		req.Priority = 100
	}

	var expiresAt *string
	if req.ExpiresAt != nil && *req.ExpiresAt != "" {
		expiresAt = req.ExpiresAt
	}

	var expiresAtSQL interface{} = nil
	if expiresAt != nil {
		expiresAtSQL = *expiresAt
	}

	if _, err := h.db.Exec(r.Context(),
		`INSERT INTO rules (id, workspace_id, scope, scope_id, name, description, priority, action, conditions, created_by, expires_at)
		 VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)`,
		ruleID, wsID, req.Scope, req.ScopeID, req.Name, req.Description, req.Priority, req.Action, condJSON, claims.UserID, expiresAtSQL,
	); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		OrgID:       orgID,
		WorkspaceID: wsID,
		EventType:   "rule.created",
		ActorType:   "USER",
		ActorID:     claims.UserID,
		Payload:     map[string]any{"rule_id": ruleID, "name": req.Name, "action": req.Action},
	})

	respond.JSON(w, http.StatusCreated, map[string]any{
		"id": ruleID, "name": req.Name, "action": req.Action, "workspaceId": wsID,
	})
}

// List returns all rules in a workspace.
func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	wsID := chi.URLParam(r, "wsId")

	rows, err := h.db.Query(r.Context(),
		`SELECT id, name, description, priority, action, scope, scope_id, enabled, conditions, expires_at, hit_count, created_at
		 FROM rules WHERE workspace_id=$1 ORDER BY priority ASC, created_at DESC`, wsID,
	)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}
	defer rows.Close()

	var ruleList []map[string]any
	for rows.Next() {
		var ruleID, name, description, action, scope, scopeID string
		var priority int
		var enabled bool
		var conditions []byte
		var expiresAt, createdAt interface{}
		var hitCount int64
		if err := rows.Scan(&ruleID, &name, &description, &priority, &action, &scope, &scopeID, &enabled, &conditions, &expiresAt, &hitCount, &createdAt); err != nil {
			continue
		}
		var condMap map[string]any
		_ = json.Unmarshal(conditions, &condMap)
		ruleList = append(ruleList, map[string]any{
			"id": ruleID, "name": name, "description": description, "priority": priority,
			"action": action, "scope": scope, "scopeId": scopeID, "enabled": enabled,
			"conditions": condMap, "expiresAt": expiresAt, "hitCount": hitCount, "createdAt": createdAt,
		})
	}
	if ruleList == nil {
		ruleList = []map[string]any{}
	}
	respond.JSON(w, http.StatusOK, map[string]any{"rules": ruleList})
}

type updateRuleRequest struct {
	Name        string         `json:"name"`
	Description string         `json:"description"`
	Priority    *int           `json:"priority"`
	Action      string         `json:"action" validate:"omitempty,oneof=AUTO_APPROVE AUTO_DENY REQUIRE_APPROVAL"`
	Conditions  map[string]any `json:"conditions"`
	Enabled     *bool          `json:"enabled"`
	ExpiresAt   *string        `json:"expiresAt"`
}

// Update updates a rule.
func (h *Handler) Update(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	ruleID := chi.URLParam(r, "ruleId")

	var req updateRuleRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := validate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	var condJSONArg *string
	if req.Conditions != nil {
		raw, _ := json.Marshal(req.Conditions)
		s := string(raw)
		condJSONArg = &s
	}

	if _, err := h.db.Exec(r.Context(),
		`UPDATE rules SET
			name=COALESCE(NULLIF($2,''), name),
			description=COALESCE(NULLIF($3,''), description),
			action=COALESCE(NULLIF($4,'')::rule_action, action),
			conditions=CASE WHEN $5::text IS NOT NULL THEN $5::jsonb ELSE conditions END,
			enabled=COALESCE($6, enabled),
			updated_at=NOW()
		 WHERE id=$1`,
		ruleID, req.Name, req.Description, req.Action, condJSONArg, req.Enabled,
	); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		EventType: "rule.updated",
		ActorType: "USER",
		ActorID:   claims.UserID,
		Payload:   map[string]any{"rule_id": ruleID},
	})

	respond.JSON(w, http.StatusOK, map[string]any{"id": ruleID})
}

// Delete deletes a rule.
func (h *Handler) Delete(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	ruleID := chi.URLParam(r, "ruleId")

	if _, err := h.db.Exec(r.Context(), `DELETE FROM rules WHERE id=$1`, ruleID); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		EventType: "rule.deleted",
		ActorType: "USER",
		ActorID:   claims.UserID,
		Payload:   map[string]any{"rule_id": ruleID},
	})

	w.WriteHeader(http.StatusNoContent)
}
