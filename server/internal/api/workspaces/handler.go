package workspaces

import (
	"encoding/json"
	"net/http"
	"strings"

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

// Handler handles workspace endpoints.
type Handler struct {
	db    *pgxpool.Pool
	audit audit.AuditService
	log   zerolog.Logger
}

// NewHandler creates a Handler.
func NewHandler(db *pgxpool.Pool, auditSvc audit.AuditService, log zerolog.Logger) *Handler {
	return &Handler{db: db, audit: auditSvc, log: log}
}

type createWorkspaceRequest struct {
	Name string `json:"name" validate:"required"`
	Slug string `json:"slug" validate:"required,alphanum"`
}

// Create creates a workspace within an org.
func (h *Handler) Create(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	orgID := chi.URLParam(r, "orgId")

	if !h.isMember(r, orgID, claims.UserID) {
		respond.Forbidden(w)
		return
	}

	var req createWorkspaceRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := validate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	wsID := id.New()
	if _, err := h.db.Exec(r.Context(),
		`INSERT INTO workspaces (id, org_id, name, slug) VALUES ($1,$2,$3,$4)`,
		wsID, orgID, req.Name, req.Slug,
	); err != nil {
		if strings.Contains(err.Error(), "unique") {
			respond.Error(w, http.StatusConflict, "slug_taken", "workspace slug already exists in this org")
			return
		}
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		OrgID:       orgID,
		WorkspaceID: wsID,
		EventType:   "workspace.created",
		ActorType:   "USER",
		ActorID:     claims.UserID,
		Payload:     map[string]any{"name": req.Name, "slug": req.Slug},
	})

	respond.JSON(w, http.StatusCreated, map[string]any{"id": wsID, "orgId": orgID, "name": req.Name, "slug": req.Slug})
}

// Get returns a single workspace.
func (h *Handler) Get(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	orgID := chi.URLParam(r, "orgId")
	wsID := chi.URLParam(r, "wsId")

	if !h.isMember(r, orgID, claims.UserID) {
		respond.Forbidden(w)
		return
	}

	var name, slug string
	if err := h.db.QueryRow(r.Context(),
		`SELECT name, slug FROM workspaces WHERE id=$1 AND org_id=$2`, wsID, orgID,
	).Scan(&name, &slug); err != nil {
		respond.NotFound(w)
		return
	}

	respond.JSON(w, http.StatusOK, map[string]any{"id": wsID, "orgId": orgID, "name": name, "slug": slug})
}

// List returns all workspaces in an org.
func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	orgID := chi.URLParam(r, "orgId")

	if !h.isMember(r, orgID, claims.UserID) {
		respond.Forbidden(w)
		return
	}

	rows, err := h.db.Query(r.Context(),
		`SELECT id, name, slug FROM workspaces WHERE org_id=$1 ORDER BY name`, orgID,
	)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}
	defer rows.Close()

	var workspaces []map[string]any
	for rows.Next() {
		var wsID, name, slug string
		if err := rows.Scan(&wsID, &name, &slug); err != nil {
			continue
		}
		workspaces = append(workspaces, map[string]any{"id": wsID, "orgId": orgID, "name": name, "slug": slug})
	}
	if workspaces == nil {
		workspaces = []map[string]any{}
	}
	respond.JSON(w, http.StatusOK, map[string]any{"workspaces": workspaces})
}

// Update renames a workspace.
func (h *Handler) Update(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	orgID := chi.URLParam(r, "orgId")
	wsID := chi.URLParam(r, "wsId")

	if !h.isAdminOrOwner(r, orgID, claims.UserID) {
		respond.Forbidden(w)
		return
	}

	var req createWorkspaceRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := validate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	if _, err := h.db.Exec(r.Context(),
		`UPDATE workspaces SET name=$2, slug=$3, updated_at=NOW() WHERE id=$1 AND org_id=$4`,
		wsID, req.Name, req.Slug, orgID,
	); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		OrgID: orgID, WorkspaceID: wsID, EventType: "workspace.updated",
		ActorType: "USER", ActorID: claims.UserID,
	})

	respond.JSON(w, http.StatusOK, map[string]any{"id": wsID, "name": req.Name, "slug": req.Slug})
}

// Delete deletes a workspace.
func (h *Handler) Delete(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	orgID := chi.URLParam(r, "orgId")
	wsID := chi.URLParam(r, "wsId")

	if !h.isAdminOrOwner(r, orgID, claims.UserID) {
		respond.Forbidden(w)
		return
	}

	if _, err := h.db.Exec(r.Context(),
		`DELETE FROM workspaces WHERE id=$1 AND org_id=$2`, wsID, orgID,
	); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		OrgID: orgID, WorkspaceID: wsID, EventType: "workspace.deleted",
		ActorType: "USER", ActorID: claims.UserID,
	})

	w.WriteHeader(http.StatusNoContent)
}

func (h *Handler) isMember(r *http.Request, orgID, userID string) bool {
	var role string
	return h.db.QueryRow(r.Context(),
		`SELECT role FROM org_members WHERE org_id=$1 AND user_id=$2`, orgID, userID,
	).Scan(&role) == nil
}

func (h *Handler) isAdminOrOwner(r *http.Request, orgID, userID string) bool {
	var role string
	if err := h.db.QueryRow(r.Context(),
		`SELECT role FROM org_members WHERE org_id=$1 AND user_id=$2`, orgID, userID,
	).Scan(&role); err != nil {
		return false
	}
	return role == "OWNER" || role == "ADMIN"
}
