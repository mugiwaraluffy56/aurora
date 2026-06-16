package machines

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"net/http"
	"strings"

	internalAuth "github.com/nod/server/internal/auth"
	"github.com/nod/server/pkg/id"
)

// Register validates the API key, checks fingerprint uniqueness, creates the machine record,
// issues a machine JWT, and returns the Machine and raw token.
func (s *machineService) Register(ctx context.Context, req RegisterRequest) (*Machine, string, error) {
	if !strings.HasPrefix(req.APIKey, "apk_") {
		return nil, "", newMachineError(http.StatusUnauthorized, "invalid_api_key", "invalid API key format")
	}

	// Hash the API key and look it up.
	hash := hashAPIKey(req.APIKey)
	var workspaceID, keyID, ownerUserID string
	err := s.db.QueryRow(ctx,
		`SELECT k.workspace_id, k.id, k.created_by
		 FROM api_keys k
		 WHERE k.key_hash=$1 AND k.revoked_at IS NULL`,
		hash,
	).Scan(&workspaceID, &keyID, &ownerUserID)
	if err != nil {
		return nil, "", newMachineError(http.StatusUnauthorized, "invalid_api_key", "API key not found or revoked")
	}

	// Check fingerprint uniqueness.
	var existing string
	err = s.db.QueryRow(ctx,
		`SELECT id FROM machines WHERE fingerprint=$1`, req.Fingerprint,
	).Scan(&existing)
	if err == nil {
		return nil, "", newMachineError(http.StatusConflict, "fingerprint_conflict", "a machine with this fingerprint already exists")
	}

	machineID := id.New()
	machineType := req.MachineType
	if machineType == "" {
		machineType = "generic"
	}

	if _, err := s.db.Exec(ctx,
		`INSERT INTO machines (id, workspace_id, api_key_id, owned_by, name, fingerprint, public_key, machine_type)
		 VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
		machineID, workspaceID, keyID, ownerUserID, req.Name, req.Fingerprint, req.PublicKey, machineType,
	); err != nil {
		return nil, "", fmt.Errorf("create machine: %w", err)
	}

	rawToken, err := internalAuth.IssueMachineToken(machineID, workspaceID, req.Fingerprint)
	if err != nil {
		return nil, "", fmt.Errorf("issue machine token: %w", err)
	}

	m := &Machine{
		ID:          machineID,
		WorkspaceID: workspaceID,
		APIKeyID:    keyID,
		OwnedBy:     ownerUserID,
		Name:        req.Name,
		Fingerprint: req.Fingerprint,
		PublicKey:   req.PublicKey,
		MachineType: machineType,
		Status:      "ACTIVE",
	}

	return m, rawToken, nil
}

// hashAPIKey returns the SHA-256 hex digest of a raw API key (same as auth.GenerateAPIKey hash).
func hashAPIKey(raw string) string {
	sum := sha256.Sum256([]byte(raw))
	return hex.EncodeToString(sum[:])
}
