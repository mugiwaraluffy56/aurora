package auth

import (
	"crypto/ed25519"
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

const (
	refreshTokenTTL = 30 * 24 * time.Hour // 30 days
	machineTokenTTL = 90 * 24 * time.Hour // 90 days
)

// MachineTokenClaims are the claims embedded in a machine JWT.
type MachineTokenClaims struct {
	MachineID   string `json:"mid"`
	WorkspaceID string `json:"wid"`
	Fingerprint string `json:"fp"`
	jwt.RegisteredClaims
}

// IssueAccessToken creates a short-lived JWT access token for a user.
func IssueAccessToken(userID, email string) (string, error) {
	return SignAccessToken(userID, email, "", "")
}

// IssueRefreshToken creates an opaque refresh token and returns the raw token
// string and its expiry time. The caller is responsible for storing the hash.
func IssueRefreshToken(userID string) (string, time.Time, error) {
	raw := uuid.NewString() + "." + uuid.NewString()
	expiresAt := time.Now().Add(refreshTokenTTL)
	return raw, expiresAt, nil
}

// IssueMachineToken creates a long-lived Ed25519 JWT for a registered machine.
func IssueMachineToken(machineID, workspaceID, fingerprint string) (string, error) {
	now := time.Now()
	claims := MachineTokenClaims{
		MachineID:   machineID,
		WorkspaceID: workspaceID,
		Fingerprint: fingerprint,
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   machineID,
			ID:        uuid.NewString(),
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(machineTokenTTL)),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodEdDSA, claims)
	signed, err := token.SignedString(ed25519.PrivateKey(ed25519PrivKey))
	if err != nil {
		return "", fmt.Errorf("sign machine token: %w", err)
	}
	return signed, nil
}

// VerifyMachineToken parses and validates a machine JWT.
func VerifyMachineToken(tokenStr string) (*MachineTokenClaims, error) {
	token, err := jwt.ParseWithClaims(tokenStr, &MachineTokenClaims{}, func(t *jwt.Token) (any, error) {
		if _, ok := t.Method.(*jwt.SigningMethodEd25519); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return ed25519.PublicKey(ed25519PubKey), nil
	})
	if err != nil {
		return nil, fmt.Errorf("parse machine token: %w", err)
	}
	claims, ok := token.Claims.(*MachineTokenClaims)
	if !ok || !token.Valid {
		return nil, fmt.Errorf("invalid machine token claims")
	}
	return claims, nil
}
