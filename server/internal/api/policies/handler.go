package policies

import (
	"encoding/json"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-playground/validator/v10"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/rs/zerolog"

	"github.com/nod/server/internal/respond"
	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/auth"
	"github.com/nod/server/pkg/id"
)

var validate = validator.New()

// Handler handles approval policy endpoints.
type Handler struct {
	db    *pgxpool.Pool
	audit audit.AuditService
	log   zerolog.Logger
}

// NewHandler creates a Handler.
func NewHandler(db *pgxpool.Pool, auditSvc audit.AuditService, log zerolog.Logger) *Handler {
	return &Handler{db: db, audit: auditSvc, log: log}
}

type createPolicyRequest struct {
	Name         string `json:"name" validate:"required"`
	Description  string `json:"description"`
	RequireCount int    `json:"requireCount"`
	TimeoutMs    int    `json:"timeoutMs"`
}

// Create creates a new approval policy.
func (h *Handler) Create(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	orgID := chi.URLParam(r, "orgId")
	wsID := chi.URLParam(r, "wsId")

	var req createPolicyRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := validate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	if req.RequireCount == 0 {
		req.RequireCount = 1
	}
	if req.TimeoutMs == 0 {
		req.TimeoutMs = 30000
	}

	policyID := id.New()
	if _, err := h.db.Exec(r.Context(),
		`INSERT INTO approval_policies (id, workspace_id, name, description, require_count, timeout_ms)
		 VALUES ($1,$2,$3,$4,$5,$6)`,
		policyID, wsID, req.Name, req.Description, req.RequireCount, req.TimeoutMs,
	); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		OrgID:       orgID,
		WorkspaceID: wsID,
		EventType:   "policy.created",
		ActorType:   "USER",
		ActorID:     claims.UserID,
		Payload:     map[string]any{"policy_id": policyID, "name": req.Name},
	})

	respond.JSON(w, http.StatusCreated, map[string]any{
		"id":           policyID,
		"workspaceId":  wsID,
		"name":         req.Name,
		"description":  req.Description,
		"requireCount": req.RequireCount,
		"timeoutMs":    req.TimeoutMs,
	})
}

// List returns all approval policies in a workspace.
func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	wsID := chi.URLParam(r, "wsId")

	rows, err := h.db.Query(r.Context(),
		`SELECT id, name, description, require_count, timeout_ms, created_at, updated_at
		 FROM approval_policies WHERE workspace_id=$1 ORDER BY created_at DESC`, wsID,
	)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}
	defer rows.Close()

	var policyList []map[string]any
	for rows.Next() {
		var pID, name, description string
		var requireCount, timeoutMs int
		var createdAt, updatedAt interface{}
		if err := rows.Scan(&pID, &name, &description, &requireCount, &timeoutMs, &createdAt, &updatedAt); err != nil {
			continue
		}
		policyList = append(policyList, map[string]any{
			"id": pID, "name": name, "description": description,
			"requireCount": requireCount, "timeoutMs": timeoutMs,
			"createdAt": createdAt, "updatedAt": updatedAt,
		})
	}
	if policyList == nil {
		policyList = []map[string]any{}
	}
	respond.JSON(w, http.StatusOK, map[string]any{"policies": policyList})
}

type updatePolicyRequest struct {
	Name         string `json:"name"`
	Description  string `json:"description"`
	RequireCount *int   `json:"requireCount"`
	TimeoutMs    *int   `json:"timeoutMs"`
}

// Update updates an approval policy.
func (h *Handler) Update(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	policyID := chi.URLParam(r, "policyId")

	var req updatePolicyRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}

	if _, err := h.db.Exec(r.Context(),
		`UPDATE approval_policies SET
			name=COALESCE(NULLIF($2,''), name),
			description=COALESCE(NULLIF($3,''), description),
			require_count=COALESCE($4, require_count),
			timeout_ms=COALESCE($5, timeout_ms),
			updated_at=NOW()
		 WHERE id=$1`,
		policyID, req.Name, req.Description, req.RequireCount, req.TimeoutMs,
	); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		EventType: "policy.updated",
		ActorType: "USER",
		ActorID:   claims.UserID,
		Payload:   map[string]any{"policy_id": policyID},
	})

	respond.JSON(w, http.StatusOK, map[string]any{"id": policyID})
}

// Delete deletes an approval policy.
func (h *Handler) Delete(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	policyID := chi.URLParam(r, "policyId")

	if _, err := h.db.Exec(r.Context(), `DELETE FROM approval_policies WHERE id=$1`, policyID); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		EventType: "policy.deleted",
		ActorType: "USER",
		ActorID:   claims.UserID,
		Payload:   map[string]any{"policy_id": policyID},
	})

	w.WriteHeader(http.StatusNoContent)
}
