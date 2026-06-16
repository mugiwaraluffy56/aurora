package audit

import (
	"context"
	"crypto/sha256"
	"errors"
	"fmt"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// ComputeChecksum returns the SHA-256 hex digest of all relevant audit event fields.
func ComputeChecksum(event *AuditEvent) string {
	h := sha256.New()
	fmt.Fprintf(h, "%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s",
		event.ID,
		event.OrgID,
		event.WorkspaceID,
		event.SessionID,
		event.RequestID,
		event.EventType,
		event.ActorType,
		event.ActorID,
		event.DeviceID,
		event.IPAddress,
		event.UserAgent,
	)
	for k, v := range event.Payload {
		fmt.Fprintf(h, "|%s=%v", k, v)
	}
	return fmt.Sprintf("%x", h.Sum(nil))
}

// GetPreviousHash returns the checksum of the most recent audit event for the org.
// Returns empty string if no events exist yet.
func GetPreviousHash(ctx context.Context, db *pgxpool.Pool, orgID string) (string, error) {
	var checksum string
	err := db.QueryRow(ctx, `
		SELECT checksum FROM audit_events
		WHERE org_id = $1
		ORDER BY created_at DESC, id DESC
		LIMIT 1`, orgID,
	).Scan(&checksum)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return "", nil
		}
		return "", fmt.Errorf("get previous audit hash: %w", err)
	}
	return checksum, nil
}
