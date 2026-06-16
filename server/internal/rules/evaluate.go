package rules

import (
	"context"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// RuleRow mirrors the DB row fields we need for evaluation.
type RuleRow struct {
	ID         string
	Scope      string
	ScopeID    string
	Name       string
	Priority   int
	Action     string
	Conditions map[string]any
	Enabled    bool
	ExpiresAt  *time.Time
}

// loadRulesForSession queries all applicable rules in scope priority order.
// Scopes: SESSION (1) → USER (2) → WORKSPACE (3) → ORG (4), then priority ASC.
func loadRulesForSession(ctx context.Context, db *pgxpool.Pool, req *EvalRequest) ([]RuleRow, error) {
	rows, err := db.Query(ctx, `
		SELECT id, scope, scope_id, name, priority, action, conditions, enabled, expires_at
		FROM rules
		WHERE workspace_id = $1
		  AND enabled = TRUE
		  AND (expires_at IS NULL OR expires_at > NOW())
		  AND (
		      (scope = 'ORG' AND scope_id = $2)
		      OR (scope = 'WORKSPACE' AND scope_id = $1)
		      OR (scope = 'USER' AND scope_id = $3)
		      OR (scope = 'SESSION' AND scope_id = $4)
		  )
		ORDER BY
		    CASE scope
		        WHEN 'SESSION' THEN 1
		        WHEN 'USER' THEN 2
		        WHEN 'WORKSPACE' THEN 3
		        WHEN 'ORG' THEN 4
		    END ASC,
		    priority ASC`,
		req.WorkspaceID, req.OrgID, req.OwnerUserID, req.SessionID,
	)
	if err != nil {
		return nil, fmt.Errorf("query rules: %w", err)
	}
	defer rows.Close()

	var rules []RuleRow
	for rows.Next() {
		var r RuleRow
		var conditions []byte
		if err := rows.Scan(&r.ID, &r.Scope, &r.ScopeID, &r.Name, &r.Priority, &r.Action, &conditions, &r.Enabled, &r.ExpiresAt); err != nil {
			return nil, fmt.Errorf("scan rule row: %w", err)
		}
		conds, err := parseConditions(conditions)
		if err != nil {
			return nil, fmt.Errorf("parse conditions for rule %s: %w", r.ID, err)
		}
		r.Conditions = conds
		rules = append(rules, r)
	}
	if err := rows.Err(); err != nil && err != pgx.ErrNoRows {
		return nil, fmt.Errorf("iterate rule rows: %w", err)
	}
	return rules, nil
}

// evaluateRule checks whether a rule matches the given EvalRequest.
func evaluateRule(r RuleRow, req *EvalRequest) (bool, error) {
	var group ConditionGroup
	if err := decodeConditionGroup(r.Conditions, &group); err != nil {
		return false, fmt.Errorf("decode condition group: %w", err)
	}
	return group.Evaluate(req), nil
}

// incrementHitCount increments the hit_count for a rule in the DB.
func incrementHitCount(ctx context.Context, db *pgxpool.Pool, ruleID string) error {
	_, err := db.Exec(ctx, `UPDATE rules SET hit_count = hit_count + 1 WHERE id = $1`, ruleID)
	if err != nil {
		return fmt.Errorf("increment rule hit count: %w", err)
	}
	return nil
}
