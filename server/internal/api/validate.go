package api

import (
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-playground/validator/v10"
)

var globalValidator = validator.New()

// DecodeJSON decodes and validates the JSON body of a request into v.
// v must be a pointer to a struct with validate tags.
func DecodeJSON(r *http.Request, v any) error {
	if err := json.NewDecoder(r.Body).Decode(v); err != nil {
		return fmt.Errorf("invalid JSON: %w", err)
	}
	if err := globalValidator.Struct(v); err != nil {
		return fmt.Errorf("validation failed: %w", err)
	}
	return nil
}

// RequireParam retrieves a URL parameter from the request context by key.
// Returns an error if the parameter is missing or empty.
func RequireParam(r *http.Request, key string) (string, error) {
	v := chi.URLParam(r, key)
	if v == "" {
		return "", fmt.Errorf("missing required parameter: %s", key)
	}
	return v, nil
}
