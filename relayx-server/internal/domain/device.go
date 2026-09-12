package domain

import (
	"context"
	"time"
)

// Device represents an authorized client device (such as the Android gateway).
type Device struct {
	ID         string     `json:"id"`
	Name       string     `json:"name"`
	TokenHash  string     `json:"-"`
	CreatedAt  time.Time  `json:"created_at"`
	LastSeenAt *time.Time `json:"last_seen_at,omitempty"`
}

// DeviceRepository defines persistence operations for devices.
type DeviceRepository interface {
	GetByTokenHash(ctx context.Context, tokenHash string) (*Device, error)
	GetByID(ctx context.Context, id string) (*Device, error)
	Upsert(ctx context.Context, device *Device) error
	UpdateLastSeen(ctx context.Context, id string, lastSeen time.Time) error
	List(ctx context.Context) ([]Device, error)
	Delete(ctx context.Context, id string) error
}
