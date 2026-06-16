package respond

import (
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-playground/validator/v10"
	"github.com/rs/zerolog"
)

var globalValidator = validator.New()

type errorBody struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

func JSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func Error(w http.ResponseWriter, status int, code, message string) {
	JSON(w, status, errorBody{Code: code, Message: message})
}

func NotFound(w http.ResponseWriter) {
	Error(w, http.StatusNotFound, "not_found", "resource not found")
}

func Unauthorized(w http.ResponseWriter) {
	Error(w, http.StatusUnauthorized, "unauthorized", "authentication required")
}

func Forbidden(w http.ResponseWriter) {
	Error(w, http.StatusForbidden, "forbidden", "insufficient permissions")
}

func BadRequest(w http.ResponseWriter, message string) {
	Error(w, http.StatusBadRequest, "bad_request", message)
}

func InternalError(w http.ResponseWriter, err error, logger zerolog.Logger) {
	logger.Error().Err(err).Msg("internal server error")
	Error(w, http.StatusInternalServerError, "internal_error", "an unexpected error occurred")
}

// APIError is a structured error returnable from handlers.
type APIError struct {
	Status  int
	Code    string
	Message string
}

func (e *APIError) Error() string {
	return fmt.Sprintf("[%d] %s: %s", e.Status, e.Code, e.Message)
}

func NewNotFound(resource string) *APIError {
	return &APIError{Status: 404, Code: "not_found", Message: resource + " not found"}
}

func NewUnauthorized() *APIError {
	return &APIError{Status: 401, Code: "unauthorized", Message: "authentication required"}
}

func NewForbidden() *APIError {
	return &APIError{Status: 403, Code: "forbidden", Message: "insufficient permissions"}
}

func NewBadRequest(msg string) *APIError {
	return &APIError{Status: 400, Code: "bad_request", Message: msg}
}

func NewConflict(msg string) *APIError {
	return &APIError{Status: 409, Code: "conflict", Message: msg}
}

func NewInternal() *APIError {
	return &APIError{Status: 500, Code: "internal_error", Message: "an unexpected error occurred"}
}

func DecodeJSON(r *http.Request, v any) error {
	if err := json.NewDecoder(r.Body).Decode(v); err != nil {
		return fmt.Errorf("invalid JSON: %w", err)
	}
	if err := globalValidator.Struct(v); err != nil {
		return fmt.Errorf("validation failed: %w", err)
	}
	return nil
}

func RequireParam(r *http.Request, key string) (string, error) {
	v := chi.URLParam(r, key)
	if v == "" {
		return "", fmt.Errorf("missing required parameter: %s", key)
	}
	return v, nil
}

func WriteAPIError(w http.ResponseWriter, err *APIError) {
	Error(w, err.Status, err.Code, err.Message)
}
