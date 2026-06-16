package redis

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
)

// PresenceData holds metadata about a connected agent machine.
type PresenceData struct {
	MachineID   string    `json:"machineId"`
	MachineName string    `json:"machineName"`
	SessionID   string    `json:"sessionId"`
	ConnectedAt time.Time `json:"connectedAt"`
	LastSeen    time.Time `json:"lastSeen"`
}

const presenceTTL = 2 * time.Minute

// SetPresence upserts a machine's presence entry in the workspace presence hash.
func SetPresence(ctx context.Context, rdb *redis.Client, workspaceID, machineID string, data PresenceData) error {
	raw, err := json.Marshal(data)
	if err != nil {
		return fmt.Errorf("marshal presence data: %w", err)
	}

	key := PresenceKey(workspaceID)
	pipe := rdb.Pipeline()
	pipe.HSet(ctx, key, machineID, raw)
	pipe.Expire(ctx, key, presenceTTL)
	if _, err := pipe.Exec(ctx); err != nil {
		return fmt.Errorf("set presence: %w", err)
	}
	return nil
}

// GetPresence returns all machines currently present in a workspace.
func GetPresence(ctx context.Context, rdb *redis.Client, workspaceID string) (map[string]PresenceData, error) {
	key := PresenceKey(workspaceID)
	raw, err := rdb.HGetAll(ctx, key).Result()
	if err != nil {
		return nil, fmt.Errorf("get presence: %w", err)
	}

	result := make(map[string]PresenceData, len(raw))
	for machineID, v := range raw {
		var data PresenceData
		if err := json.Unmarshal([]byte(v), &data); err != nil {
			return nil, fmt.Errorf("unmarshal presence data for %s: %w", machineID, err)
		}
		result[machineID] = data
	}
	return result, nil
}

// RemovePresence removes a machine's entry from the workspace presence hash.
func RemovePresence(ctx context.Context, rdb *redis.Client, workspaceID, machineID string) error {
	key := PresenceKey(workspaceID)
	if err := rdb.HDel(ctx, key, machineID).Err(); err != nil {
		return fmt.Errorf("remove presence: %w", err)
	}
	return nil
}
