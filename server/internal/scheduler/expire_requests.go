package scheduler

import "context"

// runExpireRequests expires PENDING requests past their expires_at deadline
// and notifies the relevant agent WebSocket connections.
func (s *Scheduler) runExpireRequests(ctx context.Context) {
	if err := s.reqSvc.ExpirePending(ctx); err != nil {
		s.log.Error().Err(err).Msg("expire requests failed")
	}
}
