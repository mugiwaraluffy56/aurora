package ws

import (
	"context"
	"encoding/json"
	"fmt"
	"sync"

	"nhooyr.io/websocket"
)

const sendBufSize = 200

// Conn represents an active WebSocket connection with a buffered outbound channel.
type Conn struct {
	id      string
	ws      *websocket.Conn
	sendBuf chan []byte
	mu      sync.Mutex
	closed  bool
}

// NewConn wraps a websocket connection.
func NewConn(id string, ws *websocket.Conn) *Conn {
	c := &Conn{
		id:      id,
		ws:      ws,
		sendBuf: make(chan []byte, sendBufSize),
	}
	return c
}

// ID returns the connection identifier.
func (c *Conn) ID() string { return c.id }

// Send marshals msg and enqueues it to the outbound channel.
// Returns an error if the buffer is full (client too slow) or the connection is closed.
func (c *Conn) Send(msg any) error {
	raw, err := json.Marshal(msg)
	if err != nil {
		return fmt.Errorf("marshal ws message: %w", err)
	}

	c.mu.Lock()
	closed := c.closed
	c.mu.Unlock()

	if closed {
		return fmt.Errorf("connection closed")
	}

	select {
	case c.sendBuf <- raw:
		return nil
	default:
		// Buffer full — close the connection to avoid blocking forever.
		c.Close()
		return fmt.Errorf("send buffer full, closing connection %s", c.id)
	}
}

// WritePump drains the send buffer and writes frames to the WebSocket.
// It runs until the send channel is closed or the connection errors.
func (c *Conn) WritePump(ctx context.Context) {
	for {
		select {
		case <-ctx.Done():
			return
		case msg, ok := <-c.sendBuf:
			if !ok {
				return
			}
			if err := c.ws.Write(ctx, websocket.MessageText, msg); err != nil {
				return
			}
		}
	}
}

// Close signals the write pump to stop.
func (c *Conn) Close() {
	c.mu.Lock()
	defer c.mu.Unlock()
	if !c.closed {
		c.closed = true
		close(c.sendBuf)
	}
}

// CloseWithCode closes the WebSocket with a status code.
func (c *Conn) CloseWithCode(ctx context.Context, code websocket.StatusCode, reason string) {
	c.Close()
	_ = c.ws.Close(code, reason)
}
