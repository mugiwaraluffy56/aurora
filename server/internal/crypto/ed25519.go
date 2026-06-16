package crypto

import (
	"crypto/ed25519"
	"encoding/base64"
	"fmt"
	"strings"
)

// VerifyAgentSignature verifies an Ed25519 signature over the canonical agent
// request payload. The payload is the pipe-joined concatenation:
//
//	requestId || machineId || tool || inputHash || timestamp || nonce
func VerifyAgentSignature(publicKeyBase64, requestID, machineID, tool, inputHash, timestamp, nonce, signature string) error {
	pubKeyBytes, err := base64.StdEncoding.DecodeString(publicKeyBase64)
	if err != nil {
		return fmt.Errorf("decode public key: %w", err)
	}
	if len(pubKeyBytes) != ed25519.PublicKeySize {
		return fmt.Errorf("public key: expected %d bytes, got %d", ed25519.PublicKeySize, len(pubKeyBytes))
	}

	sigBytes, err := base64.StdEncoding.DecodeString(signature)
	if err != nil {
		return fmt.Errorf("decode signature: %w", err)
	}

	payload := strings.Join([]string{requestID, machineID, tool, inputHash, timestamp, nonce}, "||")

	if !ed25519.Verify(pubKeyBytes, []byte(payload), sigBytes) {
		return fmt.Errorf("signature verification failed")
	}
	return nil
}
