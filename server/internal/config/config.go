package config

import (
	"encoding/base64"
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"
)

// Config holds all application configuration loaded from environment variables.
type Config struct {
	DatabaseURL       string
	RedisURL          string
	JWTPrivateKey     []byte // raw Ed25519 private key (64 bytes)
	JWTPublicKey      []byte // raw Ed25519 public key (32 bytes)
	ServerPort        int
	GitHubClientID    string
	GitHubClientSecret string
	GoogleClientID    string
	GoogleClientSecret string
	OAuthRedirectBase string
	ExpoPushToken     string
	Environment       string
}

// Load reads configuration from environment variables and validates it.
func Load() (*Config, error) {
	cfg := &Config{}
	var errs []string

	cfg.DatabaseURL = requireEnv("DATABASE_URL", &errs)
	cfg.RedisURL = requireEnv("REDIS_URL", &errs)

	privKeyB64 := requireEnv("JWT_PRIVATE_KEY", &errs)
	pubKeyB64 := requireEnv("JWT_PUBLIC_KEY", &errs)

	if privKeyB64 != "" && !strings.HasPrefix(privKeyB64, "<") {
		raw, err := base64.StdEncoding.DecodeString(privKeyB64)
		if err != nil {
			errs = append(errs, fmt.Sprintf("JWT_PRIVATE_KEY: invalid base64: %v", err))
		} else if len(raw) != 64 {
			errs = append(errs, fmt.Sprintf("JWT_PRIVATE_KEY: expected 64 bytes, got %d", len(raw)))
		} else {
			cfg.JWTPrivateKey = raw
		}
	}

	if pubKeyB64 != "" && !strings.HasPrefix(pubKeyB64, "<") {
		raw, err := base64.StdEncoding.DecodeString(pubKeyB64)
		if err != nil {
			errs = append(errs, fmt.Sprintf("JWT_PUBLIC_KEY: invalid base64: %v", err))
		} else if len(raw) != 32 {
			errs = append(errs, fmt.Sprintf("JWT_PUBLIC_KEY: expected 32 bytes, got %d", len(raw)))
		} else {
			cfg.JWTPublicKey = raw
		}
	}

	portStr := getEnvDefault("SERVER_PORT", "8080")
	port, err := strconv.Atoi(portStr)
	if err != nil {
		errs = append(errs, fmt.Sprintf("SERVER_PORT: must be integer, got %q", portStr))
	} else {
		cfg.ServerPort = port
	}

	cfg.GitHubClientID = os.Getenv("GITHUB_CLIENT_ID")
	cfg.GitHubClientSecret = os.Getenv("GITHUB_CLIENT_SECRET")
	cfg.GoogleClientID = os.Getenv("GOOGLE_CLIENT_ID")
	cfg.GoogleClientSecret = os.Getenv("GOOGLE_CLIENT_SECRET")
	cfg.OAuthRedirectBase = getEnvDefault("OAUTH_REDIRECT_BASE", "http://localhost:8080")
	cfg.ExpoPushToken = os.Getenv("EXPO_PUSH_TOKEN")
	cfg.Environment = getEnvDefault("ENVIRONMENT", "development")

	if len(errs) > 0 {
		return nil, errors.New("config errors: " + strings.Join(errs, "; "))
	}
	return cfg, nil
}

func requireEnv(key string, errs *[]string) string {
	v := os.Getenv(key)
	if v == "" {
		*errs = append(*errs, key+" is required")
	}
	return v
}

func getEnvDefault(key, def string) string {
	v := os.Getenv(key)
	if v == "" {
		return def
	}
	return v
}

// IsDevelopment returns true when running in development mode.
func (c *Config) IsDevelopment() bool {
	return c.Environment == "development"
}
