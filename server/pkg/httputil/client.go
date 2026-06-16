package httputil

import (
	"net/http"
	"time"
)

// NewClient creates an http.Client with the given timeout.
func NewClient(timeout time.Duration) *http.Client {
	return &http.Client{Timeout: timeout}
}
