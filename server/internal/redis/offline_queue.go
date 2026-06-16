package redis

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
)

type offlineEntry struct {
	Payload   []byte    `json:"payload"`
	ExpiresAt time.Time `json:"expiresAt"`
}

// EnqueueOffline pushes a payload to the user's offline queue with an expiry.
func EnqueueOffline(ctx context.Context, rdb *redis.Client, userID string, payload []byte, expiresAt time.Time) error {
	entry := offlineEntry{Payload: payload, ExpiresAt: expiresAt}
	raw, err := json.Marshal(entry)
	if err != nil {
		return fmt.Errorf("marshal offline entry: %w", err)
	}

	key := OfflineQueueKey(userID)
	if err := rdb.RPush(ctx, key, raw).Err(); err != nil {
		return fmt.Errorf("enqueue offline: %w", err)
	}
	// Keep the key alive for at least 7 days to avoid orphaned keys.
	if err := rdb.Expire(ctx, key, 7*24*time.Hour).Err(); err != nil {
		return fmt.Errorf("set offline queue ttl: %w", err)
	}
	return nil
}

// DrainOfflineQueue returns all non-expired payloads and removes the list.
// Expired entries are silently discarded.
func DrainOfflineQueue(ctx context.Context, rdb *redis.Client, userID string) ([][]byte, error) {
	key := OfflineQueueKey(userID)

	// Atomically get all entries and delete the list.
	pipe := rdb.Pipeline()
	lrangeCmd := pipe.LRange(ctx, key, 0, -1)
	pipe.Del(ctx, key)
	if _, err := pipe.Exec(ctx); err != nil {
		return nil, fmt.Errorf("drain offline queue: %w", err)
	}

	rawEntries, err := lrangeCmd.Result()
	if err != nil {
		return nil, fmt.Errorf("get offline queue entries: %w", err)
	}

	now := time.Now()
	var out [][]byte
	for _, raw := range rawEntries {
		var entry offlineEntry
		if err := json.Unmarshal([]byte(raw), &entry); err != nil {
			// Skip corrupt entries.
			continue
		}
		if entry.ExpiresAt.After(now) {
			out = append(out, entry.Payload)
		}
	}
	return out, nil
}
