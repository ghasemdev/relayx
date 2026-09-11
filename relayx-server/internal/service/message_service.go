package service

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"relayx-server/internal/domain"
	"relayx-server/internal/hook"
)

var (
	ErrInvalidMessage = errors.New("invalid message payload")
)

type IngestInput struct {
	MessageID  string         `json:"messageId"`
	DeviceID   string         `json:"deviceId,omitempty"`
	Sender     string         `json:"sender"`
	Body       string         `json:"body"`
	ReceivedAt int64          `json:"receivedAt"` // Unix timestamp in milliseconds
	Metadata   map[string]any `json:"metadata,omitempty"`
}

type MessageService struct {
	repo domain.MessageRepository
	hook hook.Hook
}

func NewMessageService(repo domain.MessageRepository) *MessageService {
	return &MessageService{repo: repo}
}

func (s *MessageService) SetHook(h hook.Hook) {
	s.hook = h
}

// Ingest handles validation, server metadata assignment, and idempotent persistence.
func (s *MessageService) Ingest(ctx context.Context, authenticatedDeviceID string, in IngestInput) (*domain.Message, bool, error) {
	if in.MessageID == "" || in.Sender == "" || in.Body == "" {
		return nil, false, fmt.Errorf("%w: messageId, sender, and body are required", ErrInvalidMessage)
	}

	targetDeviceID := authenticatedDeviceID
	if targetDeviceID == "" {
		targetDeviceID = in.DeviceID
	}
	if targetDeviceID == "" {
		return nil, false, fmt.Errorf("%w: deviceId could not be determined", ErrInvalidMessage)
	}

	var receivedTime time.Time
	if in.ReceivedAt > 0 {
		receivedTime = time.UnixMilli(in.ReceivedAt).UTC()
	} else {
		receivedTime = time.Now().UTC()
	}

	msg := &domain.Message{
		ID:         uuid.NewString(),
		DeviceID:   targetDeviceID,
		MessageID:  in.MessageID,
		Sender:     in.Sender,
		Body:       in.Body,
		ReceivedAt: receivedTime,
		CreatedAt:  time.Now().UTC(),
		Status:     domain.StatusReceived,
		Metadata:   in.Metadata,
	}

	persisted, isCreated, err := s.repo.Create(ctx, msg)
	if err != nil {
		return nil, false, fmt.Errorf("persisting message: %w", err)
	}

	if isCreated && s.hook != nil {
		s.hook.Trigger(ctx, persisted)
	}

	return persisted, isCreated, nil
}

func (s *MessageService) GetByID(ctx context.Context, id string) (*domain.Message, error) {
	return s.repo.GetByID(ctx, id)
}

func (s *MessageService) GetLatest(ctx context.Context) (*domain.Message, error) {
	return s.repo.GetLatest(ctx)
}

func (s *MessageService) List(ctx context.Context, filter domain.MessageFilter) ([]domain.Message, int, error) {
	return s.repo.List(ctx, filter)
}
