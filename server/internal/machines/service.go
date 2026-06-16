package machines

import (
	"context"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
	"github.com/rs/zerolog"
)

// MachineError is a domain error from the machine service.
type MachineError struct {
	Status  int
	Code    string
	Message string
}

func (e *MachineError) Error() string {
	return fmt.Sprintf("[%d] %s: %s", e.Status, e.Code, e.Message)
}

// Machine represents a registered machine.
type Machine struct {
	ID          string
	WorkspaceID string
	APIKeyID    string
	OwnedBy     string
	Name        string
	Fingerprint string
	PublicKey   string
	MachineType string
	Status      string
	LastSeenAt  *time.Time
	CreatedAt   time.Time
}

// RegisterRequest holds all fields needed to register a machine.
type RegisterRequest struct {
	APIKey      string
	Name        string
	MachineType string
	Fingerprint string
	PublicKey   string
}

// MachineService manages machine lifecycle.
type MachineService interface {
	Register(ctx context.Context, req RegisterRequest) (*Machine, string, error)
	Get(ctx context.Context, id string) (*Machine, error)
	List(ctx context.Context, workspaceID string) ([]*Machine, error)
	Rename(ctx context.Context, id, name string) error
	Revoke(ctx context.Context, id string) error
	RotateToken(ctx context.Context, id string) (string, error)
	Transfer(ctx context.Context, id, targetUserID string, targetWorkspaceID *string) error
	VerifyToken(ctx context.Context, token, fingerprint string) (*Machine, error)
}

type machineService struct {
	db  *pgxpool.Pool
	rdb *redis.Client
	log zerolog.Logger
}

// NewService creates a production MachineService.
func NewService(db *pgxpool.Pool, rdb *redis.Client, log zerolog.Logger) MachineService {
	return &machineService{db: db, rdb: rdb, log: log}
}

func (s *machineService) Get(ctx context.Context, id string) (*Machine, error) {
	m := &Machine{}
	err := s.db.QueryRow(ctx,
		`SELECT id, workspace_id, COALESCE(api_key_id,''), owned_by, name, fingerprint, public_key, machine_type, status, last_seen_at, created_at
		 FROM machines WHERE id=$1`, id,
	).Scan(&m.ID, &m.WorkspaceID, &m.APIKeyID, &m.OwnedBy, &m.Name, &m.Fingerprint, &m.PublicKey, &m.MachineType, &m.Status, &m.LastSeenAt, &m.CreatedAt)
	if err != nil {
		return nil, fmt.Errorf("get machine %s: %w", id, err)
	}
	return m, nil
}

func (s *machineService) List(ctx context.Context, workspaceID string) ([]*Machine, error) {
	rows, err := s.db.Query(ctx,
		`SELECT id, workspace_id, COALESCE(api_key_id,''), owned_by, name, fingerprint, public_key, machine_type, status, last_seen_at, created_at
		 FROM machines WHERE workspace_id=$1 ORDER BY created_at DESC`, workspaceID,
	)
	if err != nil {
		return nil, fmt.Errorf("list machines: %w", err)
	}
	defer rows.Close()

	var machines []*Machine
	for rows.Next() {
		m := &Machine{}
		if err := rows.Scan(&m.ID, &m.WorkspaceID, &m.APIKeyID, &m.OwnedBy, &m.Name, &m.Fingerprint, &m.PublicKey, &m.MachineType, &m.Status, &m.LastSeenAt, &m.CreatedAt); err != nil {
			return nil, fmt.Errorf("scan machine: %w", err)
		}
		machines = append(machines, m)
	}
	return machines, nil
}

func (s *machineService) Rename(ctx context.Context, id, name string) error {
	if _, err := s.db.Exec(ctx,
		`UPDATE machines SET name=$2, updated_at=NOW() WHERE id=$1`, id, name,
	); err != nil {
		return fmt.Errorf("rename machine %s: %w", id, err)
	}
	return nil
}

func (s *machineService) Transfer(ctx context.Context, id, targetUserID string, targetWorkspaceID *string) error {
	if targetWorkspaceID != nil && *targetWorkspaceID != "" {
		if _, err := s.db.Exec(ctx,
			`UPDATE machines SET owned_by=$2, workspace_id=$3, updated_at=NOW() WHERE id=$1`,
			id, targetUserID, *targetWorkspaceID,
		); err != nil {
			return fmt.Errorf("transfer machine %s: %w", id, err)
		}
	} else {
		if _, err := s.db.Exec(ctx,
			`UPDATE machines SET owned_by=$2, updated_at=NOW() WHERE id=$1`,
			id, targetUserID,
		); err != nil {
			return fmt.Errorf("transfer machine %s: %w", id, err)
		}
	}
	return nil
}

func (s *machineService) VerifyToken(ctx context.Context, token, fingerprint string) (*Machine, error) {
	// Token verification is done via JWT; here we just look up the machine by fingerprint.
	m := &Machine{}
	err := s.db.QueryRow(ctx,
		`SELECT id, workspace_id, COALESCE(api_key_id,''), owned_by, name, fingerprint, public_key, machine_type, status, last_seen_at, created_at
		 FROM machines WHERE fingerprint=$1 AND status='ACTIVE'`, fingerprint,
	).Scan(&m.ID, &m.WorkspaceID, &m.APIKeyID, &m.OwnedBy, &m.Name, &m.Fingerprint, &m.PublicKey, &m.MachineType, &m.Status, &m.LastSeenAt, &m.CreatedAt)
	if err != nil {
		return nil, fmt.Errorf("verify machine token: %w", err)
	}
	return m, nil
}

func newMachineError(status int, code, message string) *MachineError {
	return &MachineError{Status: status, Code: code, Message: message}
}
