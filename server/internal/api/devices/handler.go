package devices

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

// Handler handles device token endpoints.
type Handler struct {
	db    *pgxpool.Pool
	audit audit.AuditService
	log   zerolog.Logger
}

// NewHandler creates a Handler.
func NewHandler(db *pgxpool.Pool, auditSvc audit.AuditService, log zerolog.Logger) *Handler {
	return &Handler{db: db, audit: auditSvc, log: log}
}

type registerDeviceRequest struct {
	Token    string `json:"token" validate:"required"`
	Platform string `json:"platform" validate:"required,oneof=ios android"`
}

// Register registers a push notification token for the current user.
func (h *Handler) Register(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}

	var req registerDeviceRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := validate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	deviceID := id.New()
	if _, err := h.db.Exec(r.Context(),
		`INSERT INTO device_tokens (id, user_id, token, platform)
		 VALUES ($1,$2,$3,$4)
		 ON CONFLICT (user_id, token) DO UPDATE SET updated_at=NOW()`,
		deviceID, claims.UserID, req.Token, req.Platform,
	); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	// Fetch the actual ID (may differ if conflict occurred).
	var actualID string
	_ = h.db.QueryRow(r.Context(),
		`SELECT id FROM device_tokens WHERE user_id=$1 AND token=$2`, claims.UserID, req.Token,
	).Scan(&actualID)
	if actualID != "" {
		deviceID = actualID
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		EventType: "device.registered",
		ActorType: "USER",
		ActorID:   claims.UserID,
		DeviceID:  deviceID,
		Payload:   map[string]any{"platform": req.Platform},
	})

	respond.JSON(w, http.StatusCreated, map[string]any{
		"id":       deviceID,
		"platform": req.Platform,
	})
}

// List returns all push notification devices for the current user.
func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}

	rows, err := h.db.Query(r.Context(),
		`SELECT id, token, platform, created_at, updated_at FROM device_tokens WHERE user_id=$1 ORDER BY created_at DESC`,
		claims.UserID,
	)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}
	defer rows.Close()

	var deviceList []map[string]any
	for rows.Next() {
		var dID, token, platform string
		var createdAt, updatedAt interface{}
		if err := rows.Scan(&dID, &token, &platform, &createdAt, &updatedAt); err != nil {
			continue
		}
		deviceList = append(deviceList, map[string]any{
			"id": dID, "token": token, "platform": platform,
			"createdAt": createdAt, "updatedAt": updatedAt,
		})
	}
	if deviceList == nil {
		deviceList = []map[string]any{}
	}
	respond.JSON(w, http.StatusOK, map[string]any{"devices": deviceList})
}

// Unregister removes a push notification device for the current user.
func (h *Handler) Unregister(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	deviceID := chi.URLParam(r, "deviceId")

	if _, err := h.db.Exec(r.Context(),
		`DELETE FROM device_tokens WHERE id=$1 AND user_id=$2`, deviceID, claims.UserID,
	); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		EventType: "device.unregistered",
		ActorType: "USER",
		ActorID:   claims.UserID,
		DeviceID:  deviceID,
	})

	w.WriteHeader(http.StatusNoContent)
}
