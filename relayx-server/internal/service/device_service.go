package service

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"relayx-server/internal/domain"
)

var (
	ErrUnauthorized = errors.New("unauthorized device token")
)

type DeviceService struct {
	repo domain.DeviceRepository
}

func NewDeviceService(repo domain.DeviceRepository) *DeviceService {
	return &DeviceService{repo: repo}
}

// HashToken calculates the SHA-256 hex digest of a raw token.
func HashToken(rawToken string) string {
	sum := sha256.Sum256([]byte(rawToken))
	return hex.EncodeToString(sum[:])
}

// Authenticate validates a raw bearer token and updates the device's last-seen timestamp.
func (s *DeviceService) Authenticate(ctx context.Context, rawToken string) (*domain.Device, error) {
	if rawToken == "" {
		return nil, ErrUnauthorized
	}

	tokenHash := HashToken(rawToken)
	device, err := s.repo.GetByTokenHash(ctx, tokenHash)
	if err != nil {
		return nil, fmt.Errorf("retrieving device: %w", err)
	}

	if device == nil {
		return nil, ErrUnauthorized
	}

	now := time.Now().UTC()
	_ = s.repo.UpdateLastSeen(ctx, device.ID, now)
	device.LastSeenAt = &now

	return device, nil
}

// RegisterDevice creates or updates an authorized device with a raw token.
func (s *DeviceService) RegisterDevice(ctx context.Context, name, rawToken string) (*domain.Device, error) {
	tokenHash := HashToken(rawToken)

	// Check if already registered
	existing, err := s.repo.GetByTokenHash(ctx, tokenHash)
	if err != nil {
		return nil, fmt.Errorf("checking existing device: %w", err)
	}
	if existing != nil {
		return existing, nil
	}

	device := &domain.Device{
		ID:        uuid.NewString(),
		Name:      name,
		TokenHash: tokenHash,
		CreatedAt: time.Now().UTC(),
	}

	if err := s.repo.Upsert(ctx, device); err != nil {
		return nil, fmt.Errorf("storing registered device: %w", err)
	}

	return device, nil
}
