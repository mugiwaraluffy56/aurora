package rules

import (
	"encoding/json"
	"net/http"

	"github.com/go-chi/chi/v5"

	"github.com/nod/server/internal/respond"
	"github.com/nod/server/internal/auth"
	rulesEngine "github.com/nod/server/internal/rules"
)

type testRuleRequest struct {
	Tool  string         `json:"tool" validate:"required"`
	Input map[string]any `json:"input"`
}

// Test evaluates what would happen if a hypothetical request hit the rules engine
// for the given rule's workspace.
func (h *Handler) Test(w http.ResponseWriter, r *http.Request) {
	claims := auth.UserFromContext(r.Context())
	if claims == nil {
		respond.Unauthorized(w)
		return
	}
	ruleID := chi.URLParam(r, "ruleId")

	var req testRuleRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.BadRequest(w, "invalid request body")
		return
	}
	if err := validate.Struct(req); err != nil {
		respond.BadRequest(w, err.Error())
		return
	}

	// Load the rule's workspace so we can construct a targeted eval request.
	var workspaceID, action, name string
	var enabled bool
	err := h.db.QueryRow(r.Context(),
		`SELECT workspace_id, action, name, enabled FROM rules WHERE id=$1`, ruleID,
	).Scan(&workspaceID, &action, &name, &enabled)
	if err != nil {
		respond.NotFound(w)
		return
	}

	if !enabled {
		respond.JSON(w, http.StatusOK, map[string]any{
			"matched": false,
			"ruleId":  ruleID,
			"action":  nil,
			"reason":  "rule is disabled",
		})
		return
	}

	engine := rulesEngine.NewEngine(h.db, h.rdb, h.log)
	evalReq := &rulesEngine.EvalRequest{
		Tool:        req.Tool,
		Input:       req.Input,
		WorkspaceID: workspaceID,
	}

	result, err := engine.Evaluate(r.Context(), evalReq)
	if err != nil {
		respond.InternalError(w, err, h.log)
		return
	}

	out := map[string]any{
		"matched": result.Matched,
		"ruleId":  ruleID,
		"action":  nil,
	}
	if result.Matched {
		out["matchedRuleId"] = result.RuleID
		out["matchedRuleName"] = result.RuleName
		out["action"] = result.Action
	}

	// Suppress unused variable warning.
	_ = name

	respond.JSON(w, http.StatusOK, out)
}
