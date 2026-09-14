package mcp

import (
	"context"
	"encoding/json"
	"fmt"
	"regexp"
	"strings"
	"time"

	"relayx-server/internal/domain"
	"relayx-server/internal/service"
)

const (
	DefaultTimeoutSeconds = 30
	MaxTimeoutSeconds     = 300
	DefaultPageLimit      = 20
	MaxPageLimit          = 100
	DefaultOTPRegex       = `\b\d{4,8}\b`
)

// RegisterAllTools wires all 5 standard MCP tools into the server dispatcher.
func RegisterAllTools(s *Server, msgService *service.MessageService, broker EventBroker) {
	tm := &ToolManager{
		msgService: msgService,
		broker:     broker,
	}

	// 1. wait_for_message
	s.RegisterTool(domain.Tool{
		Name:        "wait_for_message",
		Description: "Event-driven blocking call that waits for a matching incoming SMS to arrive and unblocks immediately. Does not busy-poll SQLite.",
		InputSchema: domain.ToolInputSchema{
			Type: "object",
			Properties: map[string]domain.PropertySchema{
				"sender": {
					Type:        "string",
					Description: "Optional sender address or phone number to filter by (e.g. 'BANK')",
				},
				"after": {
					Type:        "integer",
					Description: "Epoch timestamp in milliseconds; only messages received after this timestamp match",
				},
				"timeout": {
					Type:        "integer",
					Description: "Maximum duration to wait in seconds (default: 30, max: 300)",
				},
			},
		},
	}, tm.WaitForMessage)

	// 2. get_otp
	s.RegisterTool(domain.Tool{
		Name:        "get_otp",
		Description: "Waits for an incoming verification SMS and extracts the numeric/alphanumeric OTP code via regex (default: \\b\\d{4,8}\\b).",
		InputSchema: domain.ToolInputSchema{
			Type: "object",
			Properties: map[string]domain.PropertySchema{
				"sender": {
					Type:        "string",
					Description: "Optional sender address to wait for (e.g. 'GOOGLE', 'CHASE')",
				},
				"regex": {
					Type:        "string",
					Description: "Optional regex pattern with capture group for OTP extraction (default: \\b\\d{4,8}\\b)",
				},
				"timeout": {
					Type:        "integer",
					Description: "Maximum duration to wait in seconds (default: 30, max: 300)",
				},
			},
		},
	}, tm.GetOTP)

	// 3. get_latest_message
	s.RegisterTool(domain.Tool{
		Name:        "get_latest_message",
		Description: "Fetches the single most recent stored SMS, optionally filtered by sender.",
		InputSchema: domain.ToolInputSchema{
			Type: "object",
			Properties: map[string]domain.PropertySchema{
				"sender": {
					Type:        "string",
					Description: "Optional sender address or phone number to filter by",
				},
			},
		},
	}, tm.GetLatestMessage)

	// 4. get_messages
	s.RegisterTool(domain.Tool{
		Name:        "get_messages",
		Description: "Queries stored SMS messages with sender, device ID, timestamp, and pagination limit filters.",
		InputSchema: domain.ToolInputSchema{
			Type: "object",
			Properties: map[string]domain.PropertySchema{
				"sender": {
					Type:        "string",
					Description: "Filter by sender address",
				},
				"deviceId": {
					Type:        "string",
					Description: "Filter by originating Android device ID",
				},
				"after": {
					Type:        "integer",
					Description: "Epoch timestamp in milliseconds; returns messages received after this timestamp",
				},
				"limit": {
					Type:        "integer",
					Description: "Maximum messages to return (default: 20, max: 100)",
				},
			},
		},
	}, tm.GetMessages)

	// 5. search_messages
	s.RegisterTool(domain.Tool{
		Name:        "search_messages",
		Description: "Performs safe substring search across stored message bodies and sender addresses.",
		InputSchema: domain.ToolInputSchema{
			Type:     "object",
			Required: []string{"query"},
			Properties: map[string]domain.PropertySchema{
				"query": {
					Type:        "string",
					Description: "Keyword or text to search for",
				},
				"limit": {
					Type:        "integer",
					Description: "Maximum search results to return (default: 20, max: 100)",
				},
			},
		},
	}, tm.SearchMessages)
}

type ToolManager struct {
	msgService *service.MessageService
	broker     EventBroker
}

// WaitForMessage implements wait_for_message tool.
func (m *ToolManager) WaitForMessage(ctx context.Context, args json.RawMessage) (domain.CallToolResult, error) {
	var params domain.WaitForMessageArgs
	if len(args) > 0 {
		if err := json.Unmarshal(args, &params); err != nil {
			return NewErrorToolResult(fmt.Sprintf("invalid arguments: %v", err)), nil
		}
	}

	timeoutSec := params.Timeout
	if timeoutSec <= 0 {
		timeoutSec = DefaultTimeoutSeconds
	} else if timeoutSec > MaxTimeoutSeconds {
		timeoutSec = MaxTimeoutSeconds
	}

	var afterTime time.Time
	if params.After > 0 {
		afterTime = time.UnixMilli(params.After).UTC()
		// Pre-check SQLite if caller specifically asked for messages after a known past timestamp
		filter := domain.MessageFilter{
			Sender: params.Sender,
			Limit:  1,
		}
		existing, _, err := m.msgService.List(ctx, filter)
		if err == nil && len(existing) > 0 {
			if existing[0].ReceivedAt.After(afterTime) {
				return NewJSONToolResult(domain.WaitForMessageResult{
					Status:  "success",
					Message: &existing[0],
				})
			}
		}
	} else {
		// Default to messages arriving from 2 seconds ago onward to prevent ingestion race conditions
		afterTime = time.Now().UTC().Add(-2 * time.Second)
	}

	sub := m.broker.Subscribe(params.Sender, afterTime)
	defer m.broker.Unsubscribe(sub)

	timer := time.NewTimer(time.Duration(timeoutSec) * time.Second)
	defer timer.Stop()

	select {
	case msg := <-sub.Channel():
		return NewJSONToolResult(domain.WaitForMessageResult{
			Status:  "success",
			Message: msg,
		})

	case <-timer.C:
		return NewJSONToolResult(domain.WaitForMessageResult{
			Status: "timeout",
			Error:  fmt.Sprintf("no message received from '%s' within %d seconds", params.Sender, timeoutSec),
		})

	case <-ctx.Done():
		return domain.CallToolResult{}, ctx.Err()
	}
}

// GetOTP implements get_otp tool.
func (m *ToolManager) GetOTP(ctx context.Context, args json.RawMessage) (domain.CallToolResult, error) {
	var params domain.GetOTPArgs
	if len(args) > 0 {
		if err := json.Unmarshal(args, &params); err != nil {
			return NewErrorToolResult(fmt.Sprintf("invalid arguments: %v", err)), nil
		}
	}

	pattern := strings.TrimSpace(params.Regex)
	if pattern == "" {
		pattern = DefaultOTPRegex
	}

	re, err := regexp.Compile(pattern)
	if err != nil {
		return NewErrorToolResult(fmt.Sprintf("invalid regex pattern: %v", err)), nil
	}

	waitArgs, _ := json.Marshal(domain.WaitForMessageArgs{
		Sender:  params.Sender,
		Timeout: params.Timeout,
	})

	toolRes, err := m.WaitForMessage(ctx, waitArgs)
	if err != nil {
		return toolRes, err
	}
	if toolRes.IsError || len(toolRes.Content) == 0 {
		return toolRes, nil
	}

	var waitResult domain.WaitForMessageResult
	if err := json.Unmarshal([]byte(toolRes.Content[0].Text), &waitResult); err != nil {
		return toolRes, nil
	}

	if waitResult.Status != "success" || waitResult.Message == nil {
		return NewJSONToolResult(domain.GetOTPResult{
			Status: "timeout",
			Error:  waitResult.Error,
			Sender: params.Sender,
		})
	}

	msg := waitResult.Message
	matches := re.FindStringSubmatch(msg.Body)
	if len(matches) == 0 {
		return NewJSONToolResult(domain.GetOTPResult{
			Status:     "no_match",
			Sender:     msg.Sender,
			MessageID:  msg.ID,
			ReceivedAt: msg.ReceivedAt.UnixMilli(),
			Error:      fmt.Sprintf("message received but no OTP matched pattern '%s'", pattern),
		})
	}

	otp := matches[0]
	if len(matches) > 1 && matches[1] != "" {
		otp = matches[1]
	}

	return NewJSONToolResult(domain.GetOTPResult{
		Status:     "success",
		OTP:        otp,
		Sender:     msg.Sender,
		MessageID:  msg.ID,
		ReceivedAt: msg.ReceivedAt.UnixMilli(),
	})
}

// GetLatestMessage implements get_latest_message tool.
func (m *ToolManager) GetLatestMessage(ctx context.Context, args json.RawMessage) (domain.CallToolResult, error) {
	var params domain.GetLatestMessageArgs
	if len(args) > 0 {
		_ = json.Unmarshal(args, &params)
	}

	if params.Sender != "" {
		messages, _, err := m.msgService.List(ctx, domain.MessageFilter{
			Sender: params.Sender,
			Limit:  1,
		})
		if err != nil {
			return NewErrorToolResult(err.Error()), nil
		}
		if len(messages) == 0 {
			return NewJSONToolResult(nil)
		}
		return NewJSONToolResult(messages[0])
	}

	msg, err := m.msgService.GetLatest(ctx)
	if err != nil {
		return NewErrorToolResult(err.Error()), nil
	}
	return NewJSONToolResult(msg)
}

// GetMessages implements get_messages tool.
func (m *ToolManager) GetMessages(ctx context.Context, args json.RawMessage) (domain.CallToolResult, error) {
	var params domain.GetMessagesArgs
	if len(args) > 0 {
		_ = json.Unmarshal(args, &params)
	}

	limit := params.Limit
	if limit <= 0 {
		limit = DefaultPageLimit
	} else if limit > MaxPageLimit {
		limit = MaxPageLimit
	}

	filter := domain.MessageFilter{
		Sender:   params.Sender,
		DeviceID: params.DeviceID,
		Limit:    limit,
	}

	messages, total, err := m.msgService.List(ctx, filter)
	if err != nil {
		return NewErrorToolResult(err.Error()), nil
	}

	// Filter by after timestamp if provided
	if params.After > 0 {
		filtered := make([]domain.Message, 0, len(messages))
		afterTime := time.UnixMilli(params.After).UTC()
		for _, msg := range messages {
			if msg.ReceivedAt.After(afterTime) {
				filtered = append(filtered, msg)
			}
		}
		messages = filtered
	}

	return NewJSONToolResult(map[string]any{
		"messages": messages,
		"total":    total,
		"limit":    limit,
	})
}

// SearchMessages implements search_messages tool.
func (m *ToolManager) SearchMessages(ctx context.Context, args json.RawMessage) (domain.CallToolResult, error) {
	var params domain.SearchMessagesArgs
	if len(args) > 0 {
		_ = json.Unmarshal(args, &params)
	}

	if strings.TrimSpace(params.Query) == "" {
		return NewErrorToolResult("query parameter is required"), nil
	}

	limit := params.Limit
	if limit <= 0 {
		limit = DefaultPageLimit
	} else if limit > MaxPageLimit {
		limit = MaxPageLimit
	}

	filter := domain.MessageFilter{
		Query: params.Query,
		Limit: limit,
	}

	messages, total, err := m.msgService.List(ctx, filter)
	if err != nil {
		return NewErrorToolResult(err.Error()), nil
	}

	return NewJSONToolResult(map[string]any{
		"messages": messages,
		"total":    total,
		"limit":    limit,
		"query":    params.Query,
	})
}
