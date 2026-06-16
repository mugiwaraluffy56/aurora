package machines

import (
	"context"
	"fmt"
	"time"

	internalAuth "github.com/nod/server/internal/auth"
	redisPkg "github.com/nod/server/internal/redis"
)

const rotationOverlapTTL = 24 * time.Hour

// RotateToken issues a new machine token for the given machine.
// The old token JTI is kept in a Redis overlap key for 24h so in-flight connections
// can gracefully close before the old token is blocklisted.
func (s *machineService) RotateToken(ctx context.Context, id string) (string, error) {
	var workspaceID, fingerprint string
	var oldJTI *string
	err := s.db.QueryRow(ctx,
		`SELECT workspace_id, fingerprint, current_token_jti FROM machines WHERE id=$1 AND status='ACTIVE'`, id,
	).Scan(&workspaceID, &fingerprint, &oldJTI)
	if err != nil {
		return "", fmt.Errorf("machine not found or revoked: %w", err)
	}

	newToken, err := internalAuth.IssueMachineToken(id, workspaceID, fingerprint)
	if err != nil {
		return "", fmt.Errorf("issue rotated token: %w", err)
	}

	// Parse the new token to get its JTI.
	newClaims, err := internalAuth.VerifyMachineToken(newToken)
	if err != nil {
		return "", fmt.Errorf("verify new token: %w", err)
	}

	// Store the old JTI in Redis overlap key with a 24h grace period.
	if oldJTI != nil && *oldJTI != "" {
		overlapKey := redisPkg.RotationOverlapKey(id)
		if err := s.rdb.Set(ctx, overlapKey, *oldJTI, rotationOverlapTTL).Err(); err != nil {
			s.log.Warn().Err(err).Str("machine_id", id).Msg("failed to set rotation overlap key")
		}
	}

	// Update DB with new JTI.
	if _, err := s.db.Exec(ctx,
		`UPDATE machines SET current_token_jti=$2, updated_at=NOW() WHERE id=$1`,
		id, newClaims.ID,
	); err != nil {
		return "", fmt.Errorf("update token jti: %w", err)
	}

	return newToken, nil
}
