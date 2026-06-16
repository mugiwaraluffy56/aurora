package redis

import "fmt"

// PresenceKey returns the Redis key for workspace agent presence.
func PresenceKey(workspaceID string) string {
	return fmt.Sprintf("presence:ws:%s", workspaceID)
}

// OfflineQueueKey returns the Redis key for a user's offline message queue.
func OfflineQueueKey(userID string) string {
	return fmt.Sprintf("offline_queue:%s", userID)
}

// NonceKey returns the Redis key for a one-time nonce.
func NonceKey(nonce string) string {
	return fmt.Sprintf("nonce:%s", nonce)
}

// BlocklistKey returns the Redis key for a blocklisted JWT ID.
func BlocklistKey(tokenID string) string {
	return fmt.Sprintf("blocklist:%s", tokenID)
}

// RulesCacheKey returns the Redis key for cached workspace rules.
func RulesCacheKey(workspaceID string) string {
	return fmt.Sprintf("rules:cache:%s", workspaceID)
}

// EscalationKey returns the Redis key for an escalation timer.
func EscalationKey(requestID string) string {
	return fmt.Sprintf("escalation:%s", requestID)
}

// RotationOverlapKey returns the Redis key for a machine token rotation overlap.
func RotationOverlapKey(machineID string) string {
	return fmt.Sprintf("rotation:overlap:%s", machineID)
}

// EventReplayKey returns the Redis key for session event replay buffer.
func EventReplayKey(sessionID string) string {
	return fmt.Sprintf("replay:%s", sessionID)
}

// IdempotencyKey returns the Redis key for a request idempotency record.
func IdempotencyKey(key string) string {
	return fmt.Sprintf("idempotency:%s", key)
}
