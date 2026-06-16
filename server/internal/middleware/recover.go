package middleware

import (
	"net/http"
	"runtime/debug"

	"github.com/rs/zerolog"
)

// Recover catches panics and returns a 500 Internal Server Error response.
func Recover(log zerolog.Logger) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			defer func() {
				if rec := recover(); rec != nil {
					log.Error().
						Interface("panic", rec).
						Bytes("stack", debug.Stack()).
						Str("request_id", r.Header.Get(RequestIDHeader)).
						Msg("recovered from panic")
					http.Error(w, `{"code":"internal_error","message":"internal server error"}`, http.StatusInternalServerError)
				}
			}()
			next.ServeHTTP(w, r)
		})
	}
}
