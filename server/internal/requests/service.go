package requests

import (
	"context"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
	"github.com/rs/zerolog"

	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/notifications"
	"github.com/nod/server/internal/ws"
)

// PermissionRequest represents a stored permission request.
type PermissionRequest struct {
	ID                string
	SessionID         string
	WorkspaceID       string
	MachineID         string
	Tool              string
	Input             any
	Description       string
	Status            string
	AutoDecided       bool
	RuleID            *string
	AgentSignature    string
	SignatureVerified  bool
	TimeoutMs         int
	Seq               int
	SessionSeq        int
	ExpiresAt         time.Time
	DecidedAt         *time.Time
	CreatedAt         time.Time
}

// CreateRequest holds the fields needed to create a permission request.
type CreateRequest struct {
	ID          string
	SessionID   string
	WorkspaceID string
	MachineID   string
	MachineName string
	Tool        string
	Input       any
	InputHash   string
	Description string
	Signature   string
	TimeoutMs   int
	Seq         int
	SessionSeq  int
	ExpiresAt   time.Time
}

// ListFilter holds filter parameters for listing requests.
type ListFilter struct {
	WorkspaceID string
	Status      string
	Cursor      string
	Limit       int
}

// RequestService manages permission request lifecycle.
type RequestService interface {
	Create(ctx context.Context, req CreateRequest) (*PermissionRequest, error)
	Get(ctx context.Context, id string) (*PermissionRequest, error)
	List(ctx context.Context, filter ListFilter) ([]*PermissionRequest, string, error)
	Decide(ctx context.Context, id, userID, deviceID string, approved bool) (*PermissionRequest, error)
	Cancel(ctx context.Context, sessionID string) error
	ExpirePending(ctx context.Context) error
}

type requestService struct {
	db       *pgxpool.Pool
	rdb      *redis.Client
	hub      *ws.Hub
	notifSvc notifications.NotificationService
	audit    audit.AuditService
	log      zerolog.Logger
}

// NewService creates a production RequestService.
func NewService(
	db *pgxpool.Pool,
	rdb *redis.Client,
	hub *ws.Hub,
	notifSvc notifications.NotificationService,
	auditSvc audit.AuditService,
	log zerolog.Logger,
) RequestService {
	return &requestService{
		db:       db,
		rdb:      rdb,
		hub:      hub,
		notifSvc: notifSvc,
		audit:    auditSvc,
		log:      log,
	}
}
