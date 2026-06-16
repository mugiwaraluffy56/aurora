package orgs

import (
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"github.com/go-chi/chi/v5"
	"github.com/go-playground/validator/v10"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/rs/zerolog"

	"github.com/nod/server/internal/respond"
	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/auth"
	"github.com/nod/server/pkg/id"
)

var validate = validator.New()

// Handler handles org endpoints.
type Handler struct {
	db    *pgxpool.Pool
	audit audit.AuditService
	log   zerolog.Logger
}

// NewHandler creates a Handler.
func NewHandler(db *pgxpool.Pool, auditSvc audit.AuditService, log zerolog.Logger) *Handler {
	return &Handler{db: db, audit: auditSvc, log: log}
}

type createOrgRequest struct {
	Name string `json:"name" validate:"required"`
	Slug string `json:"slug" validate:"required,alphanum"`
}

// Create creates an org and sets the caller as OWNER.
func (h *Handler) Create(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}

	var req createOrgRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := validate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	orgID := id.New()
	var existing string
	_ = h.db.QueryRow(r.Context(), `SELECT id FROM organizations WHERE slug=$1`, req.Slug).Scan(&existing)
	if existing != "" {
		respond.Error(w, http.StatusConflict, "slug_taken", "organization slug already in use")
		return
	}

	if _, err := h.db.Exec(r.Context(),
		`INSERT INTO organizations (id, name, slug) VALUES ($1,$2,$3)`,
		orgID, req.Name, req.Slug,
	); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	memberID := id.New()
	if _, err := h.db.Exec(r.Context(),
		`INSERT INTO org_members (id, org_id, user_id, role) VALUES ($1,$2,$3,'OWNER')`,
		memberID, orgID, claims.UserID,
	); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		OrgID:     orgID,
		EventType: "org.created",
		ActorType: "USER",
		ActorID:   claims.UserID,
		Payload:   map[string]any{"name": req.Name, "slug": req.Slug},
	})

	respond.JSON(w, http.StatusCreated, map[string]any{
		"id": orgID, "name": req.Name, "slug": req.Slug,
	})
}

// Get returns a single org.
func (h *Handler) Get(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	orgID := chi.URLParam(r, "orgId")

	// Verify membership.
	var role string
	err := h.db.QueryRow(r.Context(),
		`SELECT role FROM org_members WHERE org_id=$1 AND user_id=$2`, orgID, claims.UserID,
	).Scan(&role)
	if errors.Is(err, pgx.ErrNoRows) {
		respond.NotFound(w)
		return
	}

	var name, slug, avatarURL string
	_ = h.db.QueryRow(r.Context(),
		`SELECT name, slug, avatar_url FROM organizations WHERE id=$1`, orgID,
	).Scan(&name, &slug, &avatarURL)

	respond.JSON(w, http.StatusOK, map[string]any{
		"id": orgID, "name": name, "slug": slug, "avatarUrl": avatarURL, "role": role,
	})
}

// List returns all orgs the caller belongs to.
func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}

	rows, err := h.db.Query(r.Context(),
		`SELECT o.id, o.name, o.slug, o.avatar_url, m.role
		 FROM organizations o JOIN org_members m ON m.org_id=o.id
		 WHERE m.user_id=$1 ORDER BY o.name`, claims.UserID,
	)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}
	defer rows.Close()

	var orgs []map[string]any
	for rows.Next() {
		var orgID, name, slug, avatarURL, role string
		if err := rows.Scan(&orgID, &name, &slug, &avatarURL, &role); err != nil {
			respond.InternalError(w, err, h.log)
			return
		}
		orgs = append(orgs, map[string]any{
			"id": orgID, "name": name, "slug": slug, "avatarUrl": avatarURL, "role": role,
		})
	}
	if orgs == nil {
		orgs = []map[string]any{}
	}
	respond.JSON(w, http.StatusOK, map[string]any{"orgs": orgs})
}

type updateOrgRequest struct {
	Name string `json:"name" validate:"required"`
	Slug string `json:"slug" validate:"required,alphanum"`
}

// Update updates an org (OWNER/ADMIN only).
func (h *Handler) Update(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	orgID := chi.URLParam(r, "orgId")

	var role string
	if err := h.db.QueryRow(r.Context(),
		`SELECT role FROM org_members WHERE org_id=$1 AND user_id=$2`, orgID, claims.UserID,
	).Scan(&role); err != nil {
		respond.Forbidden(w)
		return
	}
	if role != "OWNER" && role != "ADMIN" {
		respond.Forbidden(w)
		return
	}

	var req updateOrgRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := validate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	if _, err := h.db.Exec(r.Context(),
		`UPDATE organizations SET name=$2, slug=$3, updated_at=NOW() WHERE id=$1`,
		orgID, req.Name, req.Slug,
	); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		OrgID:     orgID,
		EventType: "org.updated",
		ActorType: "USER",
		ActorID:   claims.UserID,
	})

	respond.JSON(w, http.StatusOK, map[string]any{"id": orgID, "name": req.Name, "slug": req.Slug})
}

// Delete deletes an org (OWNER only).
func (h *Handler) Delete(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	orgID := chi.URLParam(r, "orgId")

	var role string
	if err := h.db.QueryRow(r.Context(),
		`SELECT role FROM org_members WHERE org_id=$1 AND user_id=$2`, orgID, claims.UserID,
	).Scan(&role); err != nil || role != "OWNER" {
		respond.Forbidden(w)
		return
	}

	if _, err := h.db.Exec(r.Context(), `DELETE FROM organizations WHERE id=$1`, orgID); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		OrgID:     orgID,
		EventType: "org.deleted",
		ActorType: "USER",
		ActorID:   claims.UserID,
	})

	w.WriteHeader(http.StatusNoContent)
}

var _ = strings.TrimSpace // keep import
