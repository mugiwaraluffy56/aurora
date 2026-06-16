package sessions

import (
	"context"
	"fmt"

	"github.com/nod/server/pkg/id"
)

// Create creates a new session record in the database.
func (s *sessionService) Create(ctx context.Context, req CreateSessionRequest) (*Session, error) {
	sessID := id.New()
	if _, err := s.db.Exec(ctx,
		`INSERT INTO sessions (id, machine_id, workspace_id, connection_id, agent_type) VALUES ($1,$2,$3,$4,$5)`,
		sessID, req.MachineID, req.WorkspaceID, req.ConnectionID, req.AgentType,
	); err != nil {
		return nil, fmt.Errorf("create session: %w", err)
	}

	sess, err := s.Get(ctx, sessID)
	if err != nil {
		return nil, fmt.Errorf("fetch created session: %w", err)
	}
	return sess, nil
}
