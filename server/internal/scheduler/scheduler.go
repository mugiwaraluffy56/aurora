package scheduler

import (
	"context"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/rs/zerolog"

	"github.com/nod/server/internal/requests"
	"github.com/nod/server/internal/sessions"
)

// Scheduler runs periodic background maintenance tasks.
type Scheduler struct {
	db         *pgxpool.Pool
	reqSvc     requests.RequestService
	sessionSvc sessions.SessionService
	log        zerolog.Logger
}

// NewScheduler creates a Scheduler.
func NewScheduler(
	db *pgxpool.Pool,
	reqSvc requests.RequestService,
	sessionSvc sessions.SessionService,
	log zerolog.Logger,
) *Scheduler {
	return &Scheduler{
		db:         db,
		reqSvc:     reqSvc,
		sessionSvc: sessionSvc,
		log:        log,
	}
}

// Start launches all background goroutines. It blocks until ctx is cancelled.
func (s *Scheduler) Start(ctx context.Context) {
	go s.runLoop(ctx, 10*time.Second, "expire_requests", s.runExpireRequests)
	go s.runLoop(ctx, 60*time.Second, "zombie_cleanup", s.runZombieCleanup)
	go s.runLoop(ctx, 60*time.Second, "expire_rules", s.runExpireRules)
	<-ctx.Done()
}

func (s *Scheduler) runLoop(ctx context.Context, interval time.Duration, name string, fn func(context.Context)) {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			func() {
				tickCtx, cancel := context.WithTimeout(ctx, interval-time.Second)
				defer cancel()
				if err := runSafe(tickCtx, fn); err != nil {
					s.log.Error().Err(err).Str("task", name).Msg("scheduler task failed")
				}
			}()
		}
	}
}

func runSafe(ctx context.Context, fn func(context.Context)) (retErr error) {
	defer func() {
		if r := recover(); r != nil {
			retErr = nil // already logged by caller
		}
	}()
	fn(ctx)
	return nil
}
