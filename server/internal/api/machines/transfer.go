package machines

import (
	"encoding/json"
	"net/http"

	"github.com/go-chi/chi/v5"

	"github.com/nod/server/internal/respond"
	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/auth"
)

type transferRequest struct {
	TargetUserID      string  `json:"targetUserId" validate:"required"`
	TargetWorkspaceID *string `json:"targetWorkspaceId"`
}

// Transfer moves a machine to another user (and optionally workspace).
func (h *Handler) Transfer(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	machineID := chi.URLParam(r, "machineId")

	var req transferRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := validate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	if err := h.svc.Transfer(r.Context(), machineID, req.TargetUserID, req.TargetWorkspaceID); err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		EventType: "machine.transferred",
		ActorType: "USER",
		ActorID:   claims.UserID,
		Payload: map[string]any{
			"machine_id":          machineID,
			"target_user_id":      req.TargetUserID,
			"target_workspace_id": req.TargetWorkspaceID,
		},
	})

	w.WriteHeader(http.StatusNoContent)
}
