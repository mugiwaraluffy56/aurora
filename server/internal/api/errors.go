package api

import "fmt"

// APIError is a structured error that can be returned from handlers.
type APIError struct {
	Status  int
	Code    string
	Message string
}

// Error implements the error interface.
func (e *APIError) Error() string {
	return fmt.Sprintf("[%d] %s: %s", e.Status, e.Code, e.Message)
}

// NewNotFound returns a 404 APIError for the given resource.
func NewNotFound(resource string) *APIError {
	return &APIError{
		Status:  404,
		Code:    "not_found",
		Message: resource + " not found",
	}
}

// NewUnauthorized returns a 401 APIError.
func NewUnauthorized() *APIError {
	return &APIError{
		Status:  401,
		Code:    "unauthorized",
		Message: "authentication required",
	}
}

// NewForbidden returns a 403 APIError.
func NewForbidden() *APIError {
	return &APIError{
		Status:  403,
		Code:    "forbidden",
		Message: "insufficient permissions",
	}
}

// NewBadRequest returns a 400 APIError with the given message.
func NewBadRequest(msg string) *APIError {
	return &APIError{
		Status:  400,
		Code:    "bad_request",
		Message: msg,
	}
}

// NewConflict returns a 409 APIError with the given message.
func NewConflict(msg string) *APIError {
	return &APIError{
		Status:  409,
		Code:    "conflict",
		Message: msg,
	}
}

// NewInternal returns a 500 APIError.
func NewInternal() *APIError {
	return &APIError{
		Status:  500,
		Code:    "internal_error",
		Message: "an unexpected error occurred",
	}
}
