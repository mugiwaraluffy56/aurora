package sessions

import (
	"context"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/rs/zerolog"
)

// Session represents a machine's active WebSocket session.
type Session struct {
	ID          string
	MachineID   string
	WorkspaceID string
	ConnectionID string
	AgentType   string
	Status      string
	StartedAt   time.Time
	EndedAt     *time.Time
	LastHeartbeat time.Time
}

// CreateSessionRequest holds the fields for creating a session.
type CreateSessionRequest struct {
	MachineID    string
	WorkspaceID  string
	ConnectionID string
	AgentType    string
}

// SessionService manages session lifecycle.
type SessionService interface {
	Create(ctx context.Context, req CreateSessionRequest) (*Session, error)
	Get(ctx context.Context, id string) (*Session, error)
	List(ctx context.Context, workspaceID string) ([]*Session, error)
	UpdateHeartbeat(ctx context.Context, id string) error
	End(ctx context.Context, id string) error
	MarkZombie(ctx context.Context, id string) error
}

type sessionService struct {
	db  *pgxpool.Pool
	log zerolog.Logger
}

// NewService creates a production SessionService.
func NewService(db *pgxpool.Pool, log zerolog.Logger) SessionService {
	return &sessionService{db: db, log: log}
}

func (s *sessionService) Get(ctx context.Context, id string) (*Session, error) {
	sess := &Session{}
	err := s.db.QueryRow(ctx,
		`SELECT id, machine_id, workspace_id, connection_id, agent_type, status, started_at, ended_at, last_heartbeat
		 FROM sessions WHERE id=$1`, id,
	).Scan(&sess.ID, &sess.MachineID, &sess.WorkspaceID, &sess.ConnectionID, &sess.AgentType, &sess.Status,
		&sess.StartedAt, &sess.EndedAt, &sess.LastHeartbeat)
	if err != nil {
		return nil, fmt.Errorf("get session %s: %w", id, err)
	}
	return sess, nil
}

func (s *sessionService) List(ctx context.Context, workspaceID string) ([]*Session, error) {
	rows, err := s.db.Query(ctx,
		`SELECT id, machine_id, workspace_id, connection_id, agent_type, status, started_at, ended_at, last_heartbeat
		 FROM sessions WHERE workspace_id=$1 ORDER BY started_at DESC`, workspaceID,
	)
	if err != nil {
		return nil, fmt.Errorf("list sessions: %w", err)
	}
	defer rows.Close()

	var sessions []*Session
	for rows.Next() {
		sess := &Session{}
		if err := rows.Scan(&sess.ID, &sess.MachineID, &sess.WorkspaceID, &sess.ConnectionID, &sess.AgentType, &sess.Status,
			&sess.StartedAt, &sess.EndedAt, &sess.LastHeartbeat); err != nil {
			return nil, fmt.Errorf("scan session: %w", err)
		}
		sessions = append(sessions, sess)
	}
	return sessions, nil
}

func (s *sessionService) UpdateHeartbeat(ctx context.Context, id string) error {
	if _, err := s.db.Exec(ctx,
		`UPDATE sessions SET last_heartbeat=NOW() WHERE id=$1`, id,
	); err != nil {
		return fmt.Errorf("update heartbeat %s: %w", id, err)
	}
	return nil
}

func (s *sessionService) End(ctx context.Context, id string) error {
	return transitionStatus(ctx, s.db, id, []string{"ACTIVE", "ZOMBIE"}, "ENDED")
}

func (s *sessionService) MarkZombie(ctx context.Context, id string) error {
	return transitionStatus(ctx, s.db, id, []string{"ACTIVE"}, "ZOMBIE")
}
