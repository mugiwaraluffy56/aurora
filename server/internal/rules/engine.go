package rules

import (
	"context"
	"fmt"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
	"github.com/rs/zerolog"
)

// EvalRequest holds everything the rules engine needs to make a decision.
type EvalRequest struct {
	Tool        string
	Input       map[string]any
	SessionID   string
	MachineID   string
	MachineName string
	MachineType string
	AgentType   string
	WorkspaceID string
	OrgID       string
	OwnerUserID string
}

// EvalResult is what the rules engine returns for a request.
type EvalResult struct {
	Matched  bool
	Action   string // "AUTO_APPROVE" | "AUTO_DENY" | "" means manual
	RuleID   string
	RuleName string
}

// RulesEngine evaluates permission requests against stored rules.
type RulesEngine interface {
	Evaluate(ctx context.Context, req *EvalRequest) (*EvalResult, error)
}

// engine is the production rules engine backed by PostgreSQL and Redis.
type engine struct {
	db  *pgxpool.Pool
	rdb *redis.Client
	log zerolog.Logger
}

// NewEngine creates a RulesEngine.
func NewEngine(db *pgxpool.Pool, rdb *redis.Client, log zerolog.Logger) RulesEngine {
	return &engine{db: db, rdb: rdb, log: log}
}

// Evaluate loads applicable rules in scope order and returns the first match.
func (e *engine) Evaluate(ctx context.Context, req *EvalRequest) (*EvalResult, error) {
	rules, err := loadRulesForSession(ctx, e.db, req)
	if err != nil {
		return nil, fmt.Errorf("load rules: %w", err)
	}

	for _, r := range rules {
		matched, err := evaluateRule(r, req)
		if err != nil {
			e.log.Warn().Err(err).Str("rule_id", r.ID).Msg("rule evaluation error, skipping")
			continue
		}
		if matched {
			// Fire-and-forget hit count increment — don't fail the request on DB error.
			go func(ruleID string) {
				if err := incrementHitCount(context.Background(), e.db, ruleID); err != nil {
					e.log.Warn().Err(err).Str("rule_id", ruleID).Msg("failed to increment rule hit count")
				}
			}(r.ID)

			action := ""
			switch r.Action {
			case "AUTO_APPROVE":
				action = "AUTO_APPROVE"
			case "AUTO_DENY":
				action = "AUTO_DENY"
			case "REQUIRE_APPROVAL":
				action = "" // manual review
			}
			return &EvalResult{
				Matched:  true,
				Action:   action,
				RuleID:   r.ID,
				RuleName: r.Name,
			}, nil
		}
	}

	return &EvalResult{Matched: false}, nil
}
