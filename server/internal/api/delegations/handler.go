package delegations

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

// Handler handles delegation endpoints.
type Handler struct {
	db    *pgxpool.Pool
	audit audit.AuditService
	log   zerolog.Logger
}

// NewHandler creates a Handler.
func NewHandler(db *pgxpool.Pool, auditSvc audit.AuditService, log zerolog.Logger) *Handler {
	return &Handler{db: db, audit: auditSvc, log: log}
}

type createDelegationRequest struct {
	DelegateID  string `json:"delegateId" validate:"required"`
	WorkspaceID string `json:"workspaceId" validate:"required"`
	Reason      string `json:"reason"`
	ExpiresAt   string `json:"expiresAt" validate:"required"`
}

// Create creates a new delegation.
func (h *Handler) Create(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}

	var req createDelegationRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := validate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	delegationID := id.New()
	if _, err := h.db.Exec(r.Context(),
		`INSERT INTO delegations (id, delegator_id, delegate_id, workspace_id, reason, expires_at)
		 VALUES ($1,$2,$3,$4,$5,$6)
		 ON CONFLICT (delegator_id, delegate_id, workspace_id) DO NOTHING`,
		delegationID, claims.UserID, req.DelegateID, req.WorkspaceID, req.Reason, req.ExpiresAt,
	); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		WorkspaceID: req.WorkspaceID,
		EventType:   "delegation.created",
		ActorType:   "USER",
		ActorID:     claims.UserID,
		Payload: map[string]any{
			"delegation_id": delegationID,
			"delegate_id":   req.DelegateID,
		},
	})

	respond.JSON(w, http.StatusCreated, map[string]any{
		"id":          delegationID,
		"delegatorId": claims.UserID,
		"delegateId":  req.DelegateID,
		"workspaceId": req.WorkspaceID,
		"reason":      req.Reason,
		"expiresAt":   req.ExpiresAt,
	})
}

// List returns all delegations where the caller is the delegator.
func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}

	rows, err := h.db.Query(r.Context(),
		`SELECT d.id, d.delegate_id, d.workspace_id, d.reason, d.expires_at, d.created_at,
		        u.email, u.name
		 FROM delegations d JOIN users u ON u.id=d.delegate_id
		 WHERE d.delegator_id=$1 AND d.expires_at > NOW()
		 ORDER BY d.created_at DESC`, claims.UserID,
	)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}
	defer rows.Close()

	var delegList []map[string]any
	for rows.Next() {
		var dID, delegateID, wsID, reason, email, name string
		var expiresAt, createdAt interface{}
		if err := rows.Scan(&dID, &delegateID, &wsID, &reason, &expiresAt, &createdAt, &email, &name); err != nil {
			continue
		}
		delegList = append(delegList, map[string]any{
			"id": dID, "delegatorId": claims.UserID, "delegateId": delegateID,
			"workspaceId": wsID, "reason": reason, "expiresAt": expiresAt, "createdAt": createdAt,
			"delegateEmail": email, "delegateName": name,
		})
	}
	if delegList == nil {
		delegList = []map[string]any{}
	}
	respond.JSON(w, http.StatusOK, map[string]any{"delegations": delegList})
}

// Delete deletes a delegation.
func (h *Handler) Delete(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	delegID := chi.URLParam(r, "id")

	if _, err := h.db.Exec(r.Context(),
		`DELETE FROM delegations WHERE id=$1 AND delegator_id=$2`, delegID, claims.UserID,
	); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		EventType: "delegation.deleted",
		ActorType: "USER",
		ActorID:   claims.UserID,
		Payload:   map[string]any{"delegation_id": delegID},
	})

	w.WriteHeader(http.StatusNoContent)
}
