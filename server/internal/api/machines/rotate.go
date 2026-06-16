package machines

import (
	"net/http"

	"github.com/go-chi/chi/v5"

	"github.com/nod/server/internal/respond"
	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/auth"
)

// RotateToken issues a new machine token for the given machine.
func (h *Handler) RotateToken(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	machineID := chi.URLParam(r, "machineId")

	newToken, err := h.svc.RotateToken(r.Context(), machineID)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	_ = h.audit.Emit(r.Context(), audit.AuditEvent{
		EventType: "machine.token_rotated",
		ActorType: "USER",
		ActorID:   claims.UserID,
		Payload:   map[string]any{"machine_id": machineID},
	})

	respond.JSON(w, http.StatusOK, map[string]any{
		"token": newToken, // shown once
	})
}
