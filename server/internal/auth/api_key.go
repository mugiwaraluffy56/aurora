package auth

import (
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"fmt"
)

const apiKeyRawBytes = 32

// GenerateAPIKey creates a new random API key. It returns:
//   - raw: the full key to return to the caller (shown once)
//   - hash: the SHA-256 hex hash to store in the database
//   - prefix: "apk_" + first 8 chars of the base64 for display
func GenerateAPIKey() (raw, hash, prefix string, err error) {
	buf := make([]byte, apiKeyRawBytes)
	if _, err = rand.Read(buf); err != nil {
		return "", "", "", fmt.Errorf("generate api key bytes: %w", err)
	}

	b64 := base64.RawURLEncoding.EncodeToString(buf)
	raw = "apk_" + b64
	prefix = "apk_" + b64[:8]
	hash = HashAPIKey(raw)
	return raw, hash, prefix, nil
}

// HashAPIKey returns the SHA-256 hex digest of the raw API key.
func HashAPIKey(raw string) string {
	sum := sha256.Sum256([]byte(raw))
	return hex.EncodeToString(sum[:])
}

// VerifyAPIKey compares a raw API key against a stored hash in constant time.
func VerifyAPIKey(raw, hash string) bool {
	candidate := HashAPIKey(raw)
	return subtle.ConstantTimeCompare([]byte(candidate), []byte(hash)) == 1
}
