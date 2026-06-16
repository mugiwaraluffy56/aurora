package sessions

import (
	"context"
	"fmt"
	"strings"

	"github.com/jackc/pgx/v5/pgxpool"
)

// validTransitions maps from current status to allowed next statuses.
var validTransitions = map[string]map[string]bool{
	"ACTIVE": {"ZOMBIE": true, "ENDED": true},
	"ZOMBIE": {"ENDED": true},
	"ENDED":  {},
}

// transitionStatus atomically updates a session status if the transition is valid.
// The current status must be in the allowedFrom list.
func transitionStatus(ctx context.Context, db *pgxpool.Pool, sessionID string, allowedFrom []string, toStatus string) error {
	placeholders := make([]string, len(allowedFrom))
	args := []any{sessionID}
	for i, s := range allowedFrom {
		args = append(args, s)
		placeholders[i] = fmt.Sprintf("$%d", i+2)
	}
	args = append(args, toStatus)
	nextIdx := len(args)

	endedAtExpr := "ended_at"
	if toStatus == "ENDED" {
		endedAtExpr = "NOW()"
	}

	query := fmt.Sprintf(
		`UPDATE sessions SET status=$%d, ended_at=%s
		 WHERE id=$1 AND status IN (%s)`,
		nextIdx, endedAtExpr, strings.Join(placeholders, ","),
	)

	result, err := db.Exec(ctx, query, args...)
	if err != nil {
		return fmt.Errorf("transition session %s to %s: %w", sessionID, toStatus, err)
	}
	if result.RowsAffected() == 0 {
		return fmt.Errorf("session %s not found or invalid status transition to %s", sessionID, toStatus)
	}
	return nil
}
