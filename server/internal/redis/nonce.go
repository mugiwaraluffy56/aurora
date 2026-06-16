package redis

import (
	"context"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
)

// CheckAndSetNonce atomically checks whether a nonce has been seen before and,
// if not, records it. Returns false if the nonce was already present (replay).
func CheckAndSetNonce(ctx context.Context, rdb *redis.Client, nonce string, ttl time.Duration) (bool, error) {
	key := NonceKey(nonce)
	// SET NX: only set if not exists. Returns true when the key was set (new nonce).
	set, err := rdb.SetNX(ctx, key, "1", ttl).Result()
	if err != nil {
		return false, fmt.Errorf("check and set nonce: %w", err)
	}
	return set, nil
}
