package machines

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
	svcMachines "github.com/nod/server/internal/machines"
)

var validate = validator.New()

// Handler handles machine endpoints.
type Handler struct {
	db    *pgxpool.Pool
	svc   svcMachines.MachineService
	audit audit.AuditService
	log   zerolog.Logger
}

// NewHandler creates a Handler.
func NewHandler(db *pgxpool.Pool, svc svcMachines.MachineService, auditSvc audit.AuditService, log zerolog.Logger) *Handler {
	return &Handler{db: db, svc: svc, audit: auditSvc, log: log}
}

type registerRequest struct {
	APIKey      string `json:"apiKey" validate:"required"`
	Name        string `json:"name" validate:"required"`
	Type        string `json:"type" validate:"required"`
	Fingerprint string `json:"fingerprint" validate:"required"`
	PublicKey   string `json:"publicKey"`
}

// Register creates a new machine registration.
func (h *Handler) Register(w http.ResponseWriter, r *http.Request) {
	var req registerRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := validate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	machine, rawToken, err := h.svc.Register(r.Context(), svcMachines.RegisterRequest{
		APIKey:      req.APIKey,
		Name:        req.Name,
		MachineType: req.Type,
		Fingerprint: req.Fingerprint,
		PublicKey:   req.PublicKey,
	})
	if err != nil {
		if mErr, ok := err.(*svcMachines.MachineError); ok {
			respond.Error(w, mErr.Status, mErr.Code, mErr.Message)
			return
		}
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		WorkspaceID: machine.WorkspaceID,
		EventType:   "machine.registered",
		ActorType:   "MACHINE",
		ActorID:     machine.ID,
		Payload:     map[string]any{"name": machine.Name, "type": machine.MachineType},
	})

	respond.JSON(w, http.StatusCreated, map[string]any{
		"id":    machine.ID,
		"token": rawToken, // shown once
	})
}

// List returns all machines in a workspace.
func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	wsID := chi.URLParam(r, "wsId")

	list, err := h.svc.List(r.Context(), wsID)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	out := make([]map[string]any, 0, len(list))
	for _, m := range list {
		out = append(out, machineToMap(m))
	}
	respond.JSON(w, http.StatusOK, map[string]any{"machines": out})
}

// Get returns a single machine.
func (h *Handler) Get(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	machineID := chi.URLParam(r, "machineId")

	m, err := h.svc.Get(r.Context(), machineID)
	if err != nil {
		respond.NotFound(w)
		return
	}

	respond.JSON(w, http.StatusOK, machineToMap(m))
}

type updateRequest struct {
	Name string `json:"name" validate:"required"`
}

// Update renames a machine.
func (h *Handler) Update(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	machineID := chi.URLParam(r, "machineId")

	var req updateRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := validate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	if err := h.svc.Rename(r.Context(), machineID, req.Name); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		EventType: "machine.renamed",
		ActorType: "USER",
		ActorID:   claims.UserID,
		Payload:   map[string]any{"machine_id": machineID, "new_name": req.Name},
	})

	respond.JSON(w, http.StatusOK, map[string]any{"id": machineID, "name": req.Name})
}

// Revoke revokes a machine.
func (h *Handler) Revoke(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	machineID := chi.URLParam(r, "machineId")

	if err := h.svc.Revoke(r.Context(), machineID); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		EventType: "machine.revoked",
		ActorType: "USER",
		ActorID:   claims.UserID,
		Payload:   map[string]any{"machine_id": machineID},
	})

	w.WriteHeader(http.StatusNoContent)
}

func machineToMap(m *svcMachines.Machine) map[string]any {
	return map[string]any{
		"id":          m.ID,
		"workspaceId": m.WorkspaceID,
		"name":        m.Name,
		"type":        m.MachineType,
		"fingerprint": m.Fingerprint,
		"status":      m.Status,
		"lastSeenAt":  m.LastSeenAt,
		"createdAt":   m.CreatedAt,
	}
}
