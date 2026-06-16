package escalation

import (
	"context"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
	"github.com/rs/zerolog"

	"github.com/nod/server/internal/notifications"
	redisPkg "github.com/nod/server/internal/redis"
)

// EscalationService schedules and cancels escalation timers for pending requests.
type EscalationService interface {
	Schedule(ctx context.Context, requestID string, delayMs int, escalateToUserID string) error
	Cancel(ctx context.Context, requestID string) error
}

type escalationService struct {
	rdb      *redis.Client
	notifSvc notifications.NotificationService
	log      zerolog.Logger
}

// NewService creates a production EscalationService.
func NewService(rdb *redis.Client, notifSvc notifications.NotificationService, log zerolog.Logger) EscalationService {
	svc := &escalationService{rdb: rdb, notifSvc: notifSvc, log: log}
	return svc
}

// Schedule sets a Redis key with a TTL equal to delayMs; a background poller will
// detect the expiry and send an escalation notification.
func (s *escalationService) Schedule(ctx context.Context, requestID string, delayMs int, escalateToUserID string) error {
	key := redisPkg.EscalationKey(requestID)
	ttl := time.Duration(delayMs) * time.Millisecond
	if err := s.rdb.Set(ctx, key, escalateToUserID, ttl).Err(); err != nil {
		return fmt.Errorf("schedule escalation: %w", err)
	}
	return nil
}

// Cancel removes the escalation timer for a request.
func (s *escalationService) Cancel(ctx context.Context, requestID string) error {
	key := redisPkg.EscalationKey(requestID)
	if err := s.rdb.Del(ctx, key).Err(); err != nil {
		return fmt.Errorf("cancel escalation: %w", err)
	}
	return nil
}
