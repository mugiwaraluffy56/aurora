package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/golang-migrate/migrate/v4"
	_ "github.com/golang-migrate/migrate/v4/database/postgres"
	_ "github.com/golang-migrate/migrate/v4/source/file"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"

	"github.com/nod/server/internal/api"
	"github.com/nod/server/internal/audit"
	"github.com/nod/server/internal/auth"
	"github.com/nod/server/internal/config"
	"github.com/nod/server/internal/db"
	"github.com/nod/server/internal/machines"
	"github.com/nod/server/internal/notifications"
	redisPkg "github.com/nod/server/internal/redis"
	"github.com/nod/server/internal/requests"
	"github.com/nod/server/internal/rules"
	"github.com/nod/server/internal/scheduler"
	"github.com/nod/server/internal/sessions"
	"github.com/nod/server/internal/ws"
)

func main() {
	// Configure logger.
	logger := zerolog.New(os.Stdout).With().Timestamp().Logger()
	if os.Getenv("ENVIRONMENT") != "production" {
		logger = logger.Output(zerolog.ConsoleWriter{Out: os.Stdout, TimeFormat: time.RFC3339})
	}
	log.Logger = logger

	// Load configuration.
	cfg, err := config.Load()
	if err != nil {
		logger.Fatal().Err(err).Msg("failed to load config")
	}

	// Initialise JWT key pair.
	auth.InitKeys(cfg.JWTPrivateKey, cfg.JWTPublicKey)

	ctx := context.Background()

	// Connect to PostgreSQL.
	pool, err := db.NewPool(ctx, cfg.DatabaseURL)
	if err != nil {
		logger.Fatal().Err(err).Msg("failed to connect to database")
	}
	defer pool.Close()
	logger.Info().Msg("database connected")

	// Connect to Redis.
	rdb, err := redisPkg.NewClient(cfg.RedisURL)
	if err != nil {
		logger.Fatal().Err(err).Msg("failed to connect to redis")
	}
	defer rdb.Close()
	logger.Info().Msg("redis connected")

	// Run migrations.
	m, err := migrate.New("file://migrations", cfg.DatabaseURL)
	if err != nil {
		logger.Fatal().Err(err).Msg("failed to initialise migrations")
	}
	if err := m.Up(); err != nil && err != migrate.ErrNoChange {
		logger.Fatal().Err(err).Msg("failed to run migrations")
	}
	logger.Info().Msg("migrations applied")

	// Build services.
	auditSvc := audit.NewService(pool, logger)
	machineSvc := machines.NewService(pool, rdb, logger)
	sessionSvc := sessions.NewService(pool, logger)
	rulesEngine := rules.NewEngine(pool, rdb, logger)

	// Notifications service.
	notifSvc := notifications.NewExpoService(pool, logger)

	// WebSocket hub.
	hub := ws.NewHub(rdb, logger)

	// Requests service.
	requestSvc := requests.NewService(pool, rdb, hub, notifSvc, auditSvc, logger)

	// Scheduler.
	sched := scheduler.NewScheduler(pool, requestSvc, sessionSvc, logger)

	// Build HTTP router.
	router := api.NewRouter(api.RouterDeps{
		DB:          pool,
		RDB:         rdb,
		Cfg:         cfg,
		AuditSvc:    auditSvc,
		MachineSvc:  machineSvc,
		SessionSvc:  sessionSvc,
		RequestSvc:  requestSvc,
		RulesEngine: rulesEngine,
		Hub:         hub,
		Log:         logger,
	})

	// Start scheduler in background.
	schedCtx, schedCancel := context.WithCancel(ctx)
	defer schedCancel()
	go sched.Start(schedCtx)

	// Start HTTP server.
	addr := fmt.Sprintf(":%d", cfg.ServerPort)
	srv := &http.Server{
		Addr:            addr,
		Handler:         router,
		ReadHeaderTimeout: 15 * time.Second, // header only; body/WS handled per-conn
		WriteTimeout:    0,                  // disabled — WS connections are long-lived
		IdleTimeout:     120 * time.Second,
	}

	go func() {
		logger.Info().Str("addr", addr).Msg("server listening")
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Fatal().Err(err).Msg("server error")
		}
	}()

	// Graceful shutdown on SIGTERM / SIGINT.
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGTERM, syscall.SIGINT)
	<-quit

	logger.Info().Msg("shutting down server...")
	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer shutdownCancel()

	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Error().Err(err).Msg("server forced to shut down")
	}
	logger.Info().Msg("server stopped")
}
