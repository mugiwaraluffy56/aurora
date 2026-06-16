package middleware

import (
	"net/http"

	"github.com/google/uuid"
)

const RequestIDHeader = "X-Request-ID"

// RequestID injects a unique request ID into each request and response.
func RequestID(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		id := r.Header.Get(RequestIDHeader)
		if id == "" {
			id = uuid.NewString()
		}
		r.Header.Set(RequestIDHeader, id)
		w.Header().Set(RequestIDHeader, id)
		next.ServeHTTP(w, r)
	})
}
