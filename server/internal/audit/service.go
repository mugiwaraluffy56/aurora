package audit

import (
	"context"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/rs/zerolog"
)

// AuditEvent is the data needed to emit one audit event.
type AuditEvent struct {
	ID          string
	OrgID       string
	WorkspaceID string
	SessionID   string
	RequestID   string
	EventType   string
	ActorType   string // USER | MACHINE | SYSTEM
	ActorID     string
	DeviceID    string
	IPAddress   string
	UserAgent   string
	Payload     map[string]any
}

// AuditService records audit events.
type AuditService interface {
	Emit(ctx context.Context, event AuditEvent) error
}

type service struct {
	db  *pgxpool.Pool
	log zerolog.Logger
}

// NewService constructs a production AuditService.
func NewService(db *pgxpool.Pool, log zerolog.Logger) AuditService {
	return &service{db: db, log: log}
}
