package notifications

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/rs/zerolog"
)

const (
	expoPushURL = "https://exp.host/--/api/v2/push/send"
	expoBatchSize = 100
)

type expoService struct {
	db  *pgxpool.Pool
	log zerolog.Logger
}

// NewExpoService creates an Expo push notification service.
func NewExpoService(db *pgxpool.Pool, log zerolog.Logger) NotificationService {
	return &expoService{db: db, log: log}
}

type expoMessage struct {
	To    string         `json:"to"`
	Title string         `json:"title"`
	Body  string         `json:"body"`
	Data  map[string]any `json:"data,omitempty"`
}

type expoResponse struct {
	Data []struct {
		Status  string `json:"status"`
		Message string `json:"message"`
		Details struct {
			Error string `json:"error"`
		} `json:"details"`
	} `json:"data"`
}

// SendApprovalRequest sends push notifications to all given device tokens.
// Tokens are batched in groups of 100 per Expo limits.
// Invalid tokens (DeviceNotRegistered) are deleted from the DB.
func (s *expoService) SendApprovalRequest(ctx context.Context, req *ApprovalRequestNotif, deviceTokens []string) error {
	if len(deviceTokens) == 0 {
		return nil
	}

	title := fmt.Sprintf("Permission request: %s", req.Tool)
	body := req.MachineName
	if req.Description != "" {
		body = req.Description
	}

	var messages []expoMessage
	for _, token := range deviceTokens {
		messages = append(messages, expoMessage{
			To:    token,
			Title: title,
			Body:  body,
			Data: map[string]any{
				"requestId":  req.RequestID,
				"sessionId":  req.SessionID,
				"machineId":  req.MachineID,
				"tool":       req.Tool,
				"expiresAt":  req.ExpiresAt,
			},
		})
	}

	var invalidTokens []string
	for i := 0; i < len(messages); i += expoBatchSize {
		end := i + expoBatchSize
		if end > len(messages) {
			end = len(messages)
		}
		batch := messages[i:end]
		batchTokens := deviceTokens[i:end]

		invalid, err := s.sendBatch(ctx, batch, batchTokens)
		if err != nil {
			s.log.Error().Err(err).Msg("expo send batch error")
		}
		invalidTokens = append(invalidTokens, invalid...)
	}

	// Remove invalid tokens from the DB.
	for _, token := range invalidTokens {
		if _, err := s.db.Exec(ctx, `DELETE FROM device_tokens WHERE token = $1`, token); err != nil {
			s.log.Warn().Err(err).Str("token_prefix", token[:min(8, len(token))]).Msg("failed to remove invalid device token")
		}
	}
	return nil
}

func (s *expoService) sendBatch(ctx context.Context, messages []expoMessage, tokens []string) (invalidTokens []string, err error) {
	body, err := json.Marshal(messages)
	if err != nil {
		return nil, fmt.Errorf("marshal expo messages: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, expoPushURL, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("create expo request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("send expo batch: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read expo response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("expo API returned %d: %s", resp.StatusCode, respBody)
	}

	var expoResp expoResponse
	if err := json.Unmarshal(respBody, &expoResp); err != nil {
		return nil, fmt.Errorf("unmarshal expo response: %w", err)
	}

	for i, d := range expoResp.Data {
		if d.Status == "error" && d.Details.Error == "DeviceNotRegistered" {
			if i < len(tokens) {
				invalidTokens = append(invalidTokens, tokens[i])
			}
		}
	}
	return invalidTokens, nil
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}
