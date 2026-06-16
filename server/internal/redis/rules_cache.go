package redis

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
)

// Rule is a cached rule entry. The full definition lives in the rules package;
// this mirrors only what needs to be cached.
type Rule struct {
	ID          string         `json:"id"`
	Scope       string         `json:"scope"`
	ScopeID     string         `json:"scopeId"`
	Name        string         `json:"name"`
	Priority    int            `json:"priority"`
	Action      string         `json:"action"`
	Conditions  map[string]any `json:"conditions"`
	Enabled     bool           `json:"enabled"`
	ExpiresAt   *time.Time     `json:"expiresAt,omitempty"`
}

// GetCachedRules returns cached workspace rules. The bool is false on cache miss.
func GetCachedRules(ctx context.Context, rdb *redis.Client, workspaceID string) ([]Rule, bool, error) {
	key := RulesCacheKey(workspaceID)
	raw, err := rdb.Get(ctx, key).Bytes()
	if err != nil {
		if errors.Is(err, redis.Nil) {
			return nil, false, nil
		}
		return nil, false, fmt.Errorf("get cached rules: %w", err)
	}

	var rules []Rule
	if err := json.Unmarshal(raw, &rules); err != nil {
		return nil, false, fmt.Errorf("unmarshal cached rules: %w", err)
	}
	return rules, true, nil
}

// SetCachedRules stores workspace rules in the cache.
func SetCachedRules(ctx context.Context, rdb *redis.Client, workspaceID string, rules []Rule, ttl time.Duration) error {
	raw, err := json.Marshal(rules)
	if err != nil {
		return fmt.Errorf("marshal rules for cache: %w", err)
	}

	key := RulesCacheKey(workspaceID)
	if err := rdb.Set(ctx, key, raw, ttl).Err(); err != nil {
		return fmt.Errorf("set cached rules: %w", err)
	}
	return nil
}

// InvalidateRulesCache removes the workspace rules cache entry.
func InvalidateRulesCache(ctx context.Context, rdb *redis.Client, workspaceID string) error {
	key := RulesCacheKey(workspaceID)
	if err := rdb.Del(ctx, key).Err(); err != nil {
		return fmt.Errorf("invalidate rules cache: %w", err)
	}
	return nil
}
