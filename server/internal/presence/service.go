package presence

import (
	"context"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
	"github.com/rs/zerolog"

	redisPkg "github.com/nod/server/internal/redis"
)

// PresenceData holds metadata about a connected agent machine.
type PresenceData = redisPkg.PresenceData

// PresenceService tracks agent online/offline status via Redis.
type PresenceService interface {
	SetConnected(ctx context.Context, workspaceID, machineID, sessionID string) error
	SetDisconnected(ctx context.Context, workspaceID, machineID, sessionID string) error
	GetPresence(ctx context.Context, workspaceID string) (map[string]PresenceData, error)
}

type presenceService struct {
	rdb *redis.Client
	log zerolog.Logger
}

// NewService creates a production PresenceService.
func NewService(rdb *redis.Client, log zerolog.Logger) PresenceService {
	return &presenceService{rdb: rdb, log: log}
}

func (s *presenceService) SetConnected(ctx context.Context, workspaceID, machineID, sessionID string) error {
	data := redisPkg.PresenceData{
		MachineID:   machineID,
		SessionID:   sessionID,
		ConnectedAt: time.Now().UTC(),
		LastSeen:    time.Now().UTC(),
	}
	if err := redisPkg.SetPresence(ctx, s.rdb, workspaceID, machineID, data); err != nil {
		return fmt.Errorf("set connected presence: %w", err)
	}
	return nil
}

func (s *presenceService) SetDisconnected(ctx context.Context, workspaceID, machineID, sessionID string) error {
	if err := redisPkg.RemovePresence(ctx, s.rdb, workspaceID, machineID); err != nil {
		return fmt.Errorf("remove presence: %w", err)
	}
	return nil
}

func (s *presenceService) GetPresence(ctx context.Context, workspaceID string) (map[string]PresenceData, error) {
	data, err := redisPkg.GetPresence(ctx, s.rdb, workspaceID)
	if err != nil {
		return nil, fmt.Errorf("get presence: %w", err)
	}
	return data, nil
}
