package domain

import (
	"context"
	"time"
)

// MessageStatus represents the lifecycle state of an ingested SMS.
type MessageStatus string

const (
	StatusReceived  MessageStatus = "RECEIVED"
	StatusFiltered  MessageStatus = "FILTERED"
	StatusForwarded MessageStatus = "FORWARDED"
	StatusFailed    MessageStatus = "FAILED"
)

// Message represents an SMS message record.
type Message struct {
	ID          string         `json:"id"`
	DeviceID    string         `json:"device_id"`
	MessageID   string         `json:"message_id"`
	Sender      string         `json:"sender"`
	Body        string         `json:"body"`
	ReceivedAt  time.Time      `json:"received_at"`
	CreatedAt   time.Time      `json:"created_at"`
	ForwardedAt *time.Time     `json:"forwarded_at,omitempty"`
	Status      MessageStatus  `json:"status"`
	Metadata    map[string]any `json:"metadata,omitempty"`
}

// MessageFilter defines criteria for querying stored SMS messages.
type MessageFilter struct {
	Sender string
	Status MessageStatus
	Limit  int
	Offset int
}

// MessageRepository defines persistence operations for messages.
type MessageRepository interface {
	// Create stores a message. Returns (msg, isCreated, err). If duplicate, returns (existingMsg, false, nil).
	Create(ctx context.Context, msg *Message) (*Message, bool, error)
	GetByID(ctx context.Context, id string) (*Message, error)
	GetLatest(ctx context.Context) (*Message, error)
	List(ctx context.Context, filter MessageFilter) ([]Message, int, error)
}
