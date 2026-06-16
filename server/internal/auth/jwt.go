package auth

import (
	"crypto/ed25519"
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// UserClaims are the claims embedded in an access token.
type UserClaims struct {
	UserID string `json:"uid"`
	Email  string `json:"email"`
	OrgID  string `json:"org"`
	Role   string `json:"role"`
	jwt.RegisteredClaims
}

var (
	ed25519PrivKey ed25519.PrivateKey
	ed25519PubKey  ed25519.PublicKey
)

// InitKeys initialises the module-level Ed25519 key pair from raw bytes.
// Must be called once at startup before issuing or verifying tokens.
func InitKeys(priv, pub []byte) {
	ed25519PrivKey = ed25519.PrivateKey(priv)
	ed25519PubKey = ed25519.PublicKey(pub)
}

// SignAccessToken issues a 15-minute Ed25519-signed JWT for the given user.
func SignAccessToken(userID, email, orgID, role string) (string, error) {
	now := time.Now()
	claims := UserClaims{
		UserID: userID,
		Email:  email,
		OrgID:  orgID,
		Role:   role,
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   userID,
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(15 * time.Minute)),
		},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodEdDSA, claims)
	signed, err := token.SignedString(ed25519PrivKey)
	if err != nil {
		return "", fmt.Errorf("sign access token: %w", err)
	}
	return signed, nil
}

// VerifyAccessToken parses and validates a JWT access token.
func VerifyAccessToken(tokenStr string) (*UserClaims, error) {
	token, err := jwt.ParseWithClaims(tokenStr, &UserClaims{}, func(t *jwt.Token) (any, error) {
		if _, ok := t.Method.(*jwt.SigningMethodEd25519); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return ed25519PubKey, nil
	})
	if err != nil {
		return nil, fmt.Errorf("parse access token: %w", err)
	}
	claims, ok := token.Claims.(*UserClaims)
	if !ok || !token.Valid {
		return nil, fmt.Errorf("invalid token claims")
	}
	return claims, nil
}
