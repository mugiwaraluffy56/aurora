package orgs

import (
	"encoding/json"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/rs/zerolog"

	"github.com/nod/server/internal/respond"
	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/auth"
	"github.com/nod/server/pkg/id"
)

// MembersHandler handles org membership endpoints.
type MembersHandler struct {
	db    *pgxpool.Pool
	audit audit.AuditService
	log   zerolog.Logger
}

// NewMembersHandler creates a MembersHandler.
func NewMembersHandler(db *pgxpool.Pool, auditSvc audit.AuditService, log zerolog.Logger) *MembersHandler {
	return &MembersHandler{db: db, audit: auditSvc, log: log}
}

type inviteRequest struct {
	Email string `json:"email" validate:"required,email"`
	Role  string `json:"role" validate:"required,oneof=ADMIN MEMBER VIEWER"`
}

// Invite adds a user to the org by email. Only OWNER or ADMIN may invite.
func (h *MembersHandler) Invite(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	orgID := chi.URLParam(r, "orgId")

	// Check caller role.
	var callerRole string
	if err := h.db.QueryRow(r.Context(),
		`SELECT role FROM org_members WHERE org_id=$1 AND user_id=$2`, orgID, claims.UserID,
	).Scan(&callerRole); err != nil || (callerRole != "OWNER" && callerRole != "ADMIN") {
		respond.Forbidden(w)
		return
	}

	var req inviteRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := validate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	// Find user by email.
	var targetUserID string
	if err := h.db.QueryRow(r.Context(),
		`SELECT id FROM users WHERE email=$1`, req.Email,
	).Scan(&targetUserID); err != nil {
		respond.Error(w, http.StatusNotFound, "user_not_found", "no user with that email")
		return
	}

	memberID := id.New()
	if _, err := h.db.Exec(r.Context(),
		`INSERT INTO org_members (id, org_id, user_id, role, invited_by) VALUES ($1,$2,$3,$4,$5)
		 ON CONFLICT (org_id, user_id) DO NOTHING`,
		memberID, orgID, targetUserID, req.Role, claims.UserID,
	); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		OrgID:     orgID,
		EventType: "org.member_invited",
		ActorType: "USER",
		ActorID:   claims.UserID,
		Payload:   map[string]any{"invited_user_id": targetUserID, "role": req.Role},
	})

	respond.JSON(w, http.StatusCreated, map[string]any{
		"id": memberID, "userId": targetUserID, "orgId": orgID, "role": req.Role,
	})
}

// List lists org members.
func (h *MembersHandler) List(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	orgID := chi.URLParam(r, "orgId")

	// Verify membership.
	var role string
	if err := h.db.QueryRow(r.Context(),
		`SELECT role FROM org_members WHERE org_id=$1 AND user_id=$2`, orgID, claims.UserID,
	).Scan(&role); err != nil {
		respond.Forbidden(w)
		return
	}

	rows, err := h.db.Query(r.Context(),
		`SELECT m.id, m.user_id, m.role, u.email, u.name, u.avatar_url, m.created_at
		 FROM org_members m JOIN users u ON u.id=m.user_id
		 WHERE m.org_id=$1 ORDER BY m.created_at`, orgID,
	)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}
	defer rows.Close()

	var members []map[string]any
	for rows.Next() {
		var mID, userID, mRole, email, name, avatarURL string
		var createdAt interface{}
		if err := rows.Scan(&mID, &userID, &mRole, &email, &name, &avatarURL, &createdAt); err != nil {
			continue
		}
		members = append(members, map[string]any{
			"id": mID, "userId": userID, "role": mRole, "email": email,
			"name": name, "avatarUrl": avatarURL, "createdAt": createdAt,
		})
	}
	if members == nil {
		members = []map[string]any{}
	}
	respond.JSON(w, http.StatusOK, map[string]any{"members": members})
}

type updateRoleRequest struct {
	Role string `json:"role" validate:"required,oneof=ADMIN MEMBER VIEWER"`
}

// UpdateRole changes a member's role.
func (h *MembersHandler) UpdateRole(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	orgID := chi.URLParam(r, "orgId")
	memberUserID := chi.URLParam(r, "userId")

	var callerRole string
	if err := h.db.QueryRow(r.Context(),
		`SELECT role FROM org_members WHERE org_id=$1 AND user_id=$2`, orgID, claims.UserID,
	).Scan(&callerRole); err != nil || (callerRole != "OWNER" && callerRole != "ADMIN") {
		respond.Forbidden(w)
		return
	}

	var req updateRoleRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := validate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	if _, err := h.db.Exec(r.Context(),
		`UPDATE org_members SET role=$3, updated_at=NOW() WHERE org_id=$1 AND user_id=$2`,
		orgID, memberUserID, req.Role,
	); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		OrgID:     orgID,
		EventType: "org.member_role_changed",
		ActorType: "USER",
		ActorID:   claims.UserID,
		Payload:   map[string]any{"target_user_id": memberUserID, "new_role": req.Role},
	})

	respond.JSON(w, http.StatusOK, map[string]any{"userId": memberUserID, "role": req.Role})
}

// Remove removes a member from the org.
func (h *MembersHandler) Remove(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	orgID := chi.URLParam(r, "orgId")
	memberUserID := chi.URLParam(r, "userId")

	var callerRole string
	if err := h.db.QueryRow(r.Context(),
		`SELECT role FROM org_members WHERE org_id=$1 AND user_id=$2`, orgID, claims.UserID,
	).Scan(&callerRole); err != nil || (callerRole != "OWNER" && callerRole != "ADMIN") {
		respond.Forbidden(w)
		return
	}

	if _, err := h.db.Exec(r.Context(),
		`DELETE FROM org_members WHERE org_id=$1 AND user_id=$2`, orgID, memberUserID,
	); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		OrgID:     orgID,
		EventType: "org.member_removed",
		ActorType: "USER",
		ActorID:   claims.UserID,
		Payload:   map[string]any{"removed_user_id": memberUserID},
	})

	w.WriteHeader(http.StatusNoContent)
}
