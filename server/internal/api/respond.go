package api

import (
	"encoding/json"
	"net/http"

	"github.com/rs/zerolog"
)

// JSON writes a JSON-encoded response with the given status code.
func JSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(v); err != nil {
		// Nothing useful we can do at this point — headers are already written.
		_ = err
	}
}

type errorBody struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

// Error writes a structured JSON error response.
func Error(w http.ResponseWriter, status int, code, message string) {
	JSON(w, status, errorBody{Code: code, Message: message})
}

// NotFound writes a 404 not found response.
func NotFound(w http.ResponseWriter) {
	Error(w, http.StatusNotFound, "not_found", "resource not found")
}

// Unauthorized writes a 401 unauthorized response.
func Unauthorized(w http.ResponseWriter) {
	Error(w, http.StatusUnauthorized, "unauthorized", "authentication required")
}

// Forbidden writes a 403 forbidden response.
func Forbidden(w http.ResponseWriter) {
	Error(w, http.StatusForbidden, "forbidden", "insufficient permissions")
}

// BadRequest writes a 400 bad request response with the given message.
func BadRequest(w http.ResponseWriter, message string) {
	Error(w, http.StatusBadRequest, "bad_request", message)
}

// InternalError logs the error and writes a 500 response.
func InternalError(w http.ResponseWriter, err error, logger zerolog.Logger) {
	logger.Error().Err(err).Msg("internal server error")
	Error(w, http.StatusInternalServerError, "internal_error", "an unexpected error occurred")
}
