package scheduler

import "context"

// runZombieCleanup marks sessions ZOMBIE if their last heartbeat is more than 90s ago
// and then cancels all pending requests for those sessions.
func (s *Scheduler) runZombieCleanup(ctx context.Context) {
	rows, err := s.db.Query(ctx,
		`UPDATE sessions SET status='ZOMBIE'
		 WHERE status='ACTIVE' AND last_heartbeat < NOW() - INTERVAL '90 seconds'
		 RETURNING id, workspace_id, machine_id`,
	)
	if err != nil {
		s.log.Error().Err(err).Msg("zombie cleanup query failed")
		return
	}
	defer rows.Close()

	type zombie struct {
		sessionID   string
		workspaceID string
		machineID   string
	}

	var zombies []zombie
	for rows.Next() {
		var z zombie
		if err := rows.Scan(&z.sessionID, &z.workspaceID, &z.machineID); err != nil {
			s.log.Warn().Err(err).Msg("scan zombie session")
			continue
		}
		zombies = append(zombies, z)
	}
	_ = rows.Err()

	for _, z := range zombies {
		// Cancel all pending requests for the zombie session.
		if err := s.reqSvc.Cancel(ctx, z.sessionID); err != nil {
			s.log.Warn().Err(err).Str("session_id", z.sessionID).Msg("cancel zombie session requests failed")
		}

		// Record a zombie audit event directly via DB to avoid an audit service dependency.
		_, _ = s.db.Exec(ctx,
			`INSERT INTO audit_events (id, org_id, workspace_id, session_id, event_type, actor_type, actor_id, previous_hash, checksum)
			 SELECT gen_random_uuid()::text, COALESCE(w.org_id,''), $2, $1, 'session.zombie', 'SYSTEM', $3, '', 'scheduler'
			 FROM sessions se LEFT JOIN workspaces w ON w.id=se.workspace_id
			 WHERE se.id=$1 LIMIT 1`,
			z.sessionID, z.workspaceID, z.machineID,
		)

		s.log.Info().Str("session_id", z.sessionID).Msg("session marked zombie")
	}
}
