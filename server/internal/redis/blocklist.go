package redis

import (
	"context"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
)

// BlockToken adds a JWT ID to the blocklist with the given TTL.
func BlockToken(ctx context.Context, rdb *redis.Client, tokenID string, ttl time.Duration) error {
	key := BlocklistKey(tokenID)
	if err := rdb.Set(ctx, key, "1", ttl).Err(); err != nil {
		return fmt.Errorf("block token: %w", err)
	}
	return nil
}

// IsBlocked reports whether the given JWT ID is on the blocklist.
func IsBlocked(ctx context.Context, rdb *redis.Client, tokenID string) (bool, error) {
	key := BlocklistKey(tokenID)
	exists, err := rdb.Exists(ctx, key).Result()
	if err != nil {
		return false, fmt.Errorf("check blocklist: %w", err)
	}
	return exists > 0, nil
}
