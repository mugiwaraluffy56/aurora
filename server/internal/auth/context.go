package auth

import "context"

type contextKey string

const (
	ctxKeyUser    contextKey = "user"
	ctxKeyMachine contextKey = "machine"
)

// WithUser stores the UserClaims in the context.
func WithUser(ctx context.Context, claims *UserClaims) context.Context {
	return context.WithValue(ctx, ctxKeyUser, claims)
}

// UserFromContext retrieves the UserClaims from context. Returns nil if absent.
func UserFromContext(ctx context.Context) *UserClaims {
	v, _ := ctx.Value(ctxKeyUser).(*UserClaims)
	return v
}

// WithMachine stores the MachineTokenClaims in the context.
func WithMachine(ctx context.Context, claims *MachineTokenClaims) context.Context {
	return context.WithValue(ctx, ctxKeyMachine, claims)
}

// MachineFromContext retrieves the MachineTokenClaims from context.
func MachineFromContext(ctx context.Context) *MachineTokenClaims {
	v, _ := ctx.Value(ctxKeyMachine).(*MachineTokenClaims)
	return v
}
