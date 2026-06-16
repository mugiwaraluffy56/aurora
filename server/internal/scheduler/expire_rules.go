package scheduler

import "context"

// runExpireRules disables rules whose expires_at has passed.
func (s *Scheduler) runExpireRules(ctx context.Context) {
	result, err := s.db.Exec(ctx,
		`UPDATE rules SET enabled=FALSE, updated_at=NOW()
		 WHERE enabled=TRUE AND expires_at IS NOT NULL AND expires_at < NOW()`,
	)
	if err != nil {
		s.log.Error().Err(err).Msg("expire rules failed")
		return
	}
	if n := result.RowsAffected(); n > 0 {
		s.log.Info().Int64("count", n).Msg("expired rules disabled")
	}
}
