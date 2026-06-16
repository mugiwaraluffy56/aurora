package notifications

import "context"

// ApprovalRequestNotif is the data for a push notification asking for approval.
type ApprovalRequestNotif struct {
	RequestID   string
	SessionID   string
	MachineID   string
	MachineName string
	Tool        string
	Description string
	ExpiresAt   string
}

// NotificationService sends push notifications.
type NotificationService interface {
	SendApprovalRequest(ctx context.Context, req *ApprovalRequestNotif, deviceTokens []string) error
}
