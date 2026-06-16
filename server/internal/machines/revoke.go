package machines

import (
	"context"
	"fmt"
	"time"

	redisPkg "github.com/nod/server/internal/redis"
)

const machineTokenTTL = 90 * 24 * time.Hour

// Revoke marks a machine as REVOKED and blocklists its current token JTI.
func (s *machineService) Revoke(ctx context.Context, id string) error {
	// Fetch the current token JTI before revoking.
	var currentJTI *string
	_ = s.db.QueryRow(ctx,
		`SELECT current_token_jti FROM machines WHERE id=$1`, id,
	).Scan(&currentJTI)

	_, err := s.db.Exec(ctx,
		`UPDATE machines SET status='REVOKED', revoked_at=NOW(), updated_at=NOW() WHERE id=$1`, id,
	)
	if err != nil {
		return fmt.Errorf("revoke machine %s: %w", id, err)
	}

	// Add the current token JTI to the blocklist so existing WS connections are rejected.
	if currentJTI != nil && *currentJTI != "" {
		if err := redisPkg.BlockToken(ctx, s.rdb, *currentJTI, machineTokenTTL); err != nil {
			s.log.Warn().Err(err).Str("machine_id", id).Msg("failed to blocklist token on revoke")
		}
	}

	return nil
}
