package ws

import (
	"context"
	"time"

	"github.com/rs/zerolog/log"
	"nhooyr.io/websocket"
)

const (
	pingInterval = 30 * time.Second
	pongTimeout  = 5 * time.Second
)

// RunAgentHeartbeat sends periodic pings to an agent conn and closes it if
// no pong is received within pongTimeout.
func RunAgentHeartbeat(ctx context.Context, conn *Conn, onClose func()) {
	ticker := time.NewTicker(pingInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			onClose()
			return
		case <-ticker.C:
			ts := time.Now().UnixMilli()
			if err := conn.Send(ServerPongMsg{Type: "ping", Ts: ts, ServerTs: ts}); err != nil {
				log.Warn().Err(err).Str("conn_id", conn.ID()).Msg("agent heartbeat: send ping failed")
				return
			}
		}
	}
}

// RunMobileHeartbeat sends periodic pings to a mobile conn and closes it if
// no pong is received within pongTimeout.
func RunMobileHeartbeat(ctx context.Context, conn *Conn, onClose func()) {
	ticker := time.NewTicker(pingInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			onClose()
			return
		case <-ticker.C:
			if err := conn.Send(ServerMobilePingMsg{Type: "ping"}); err != nil {
				onClose()
				conn.CloseWithCode(context.Background(), websocket.StatusGoingAway, "heartbeat failed")
				return
			}
		}
	}
}

// RunHardwareHeartbeat sends periodic pings to a hardware device conn.
func RunHardwareHeartbeat(ctx context.Context, conn *Conn, onClose func()) {
	ticker := time.NewTicker(pingInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			onClose()
			return
		case <-ticker.C:
			if err := conn.Send(ServerHardwarePingMsg{Type: "ping"}); err != nil {
				onClose()
				conn.CloseWithCode(context.Background(), websocket.StatusGoingAway, "heartbeat failed")
				return
			}
		}
	}
}
