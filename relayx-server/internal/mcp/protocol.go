package mcp

import (
	"encoding/json"
	"fmt"

	"relayx-server/internal/domain"
)

// ParseRequest unmarshals JSON-RPC 2.0 bytes into an RPCRequest struct.
func ParseRequest(data []byte) (*domain.RPCRequest, *domain.RPCResponse) {
	var req domain.RPCRequest
	if err := json.Unmarshal(data, &req); err != nil {
		return nil, NewErrorResponse(nil, domain.CodeParseError, "Parse error: invalid JSON", err.Error())
	}

	if req.JSONRPC != "2.0" {
		return nil, NewErrorResponse(req.ID, domain.CodeInvalidRequest, "Invalid Request: jsonrpc must be '2.0'", nil)
	}

	if req.Method == "" {
		return nil, NewErrorResponse(req.ID, domain.CodeInvalidRequest, "Invalid Request: method is required", nil)
	}

	return &req, nil
}

// NewSuccessResponse formats a successful JSON-RPC response.
func NewSuccessResponse(id any, result any) *domain.RPCResponse {
	return &domain.RPCResponse{
		JSONRPC: "2.0",
		ID:      id,
		Result:  result,
	}
}

// NewErrorResponse formats a standard JSON-RPC error response.
func NewErrorResponse(id any, code int, message string, data any) *domain.RPCResponse {
	return &domain.RPCResponse{
		JSONRPC: "2.0",
		ID:      id,
		Error: &domain.RPCError{
			Code:    code,
			Message: message,
			Data:    data,
		},
	}
}

// NewTextToolResult wraps a plain string as a tool execution result.
func NewTextToolResult(text string) domain.CallToolResult {
	return domain.CallToolResult{
		Content: []domain.ContentItem{
			{
				Type: "text",
				Text: text,
			},
		},
		IsError: false,
	}
}

// NewJSONToolResult serializes any Go struct to JSON text inside a tool result.
func NewJSONToolResult(v any) (domain.CallToolResult, error) {
	b, err := json.Marshal(v)
	if err != nil {
		return domain.CallToolResult{}, fmt.Errorf("marshaling tool result: %w", err)
	}
	return domain.CallToolResult{
		Content: []domain.ContentItem{
			{
				Type: "text",
				Text: string(b),
			},
		},
		IsError: false,
	}, nil
}

// NewErrorToolResult wraps an error description as an isError tool result.
func NewErrorToolResult(errText string) domain.CallToolResult {
	return domain.CallToolResult{
		Content: []domain.ContentItem{
			{
				Type: "text",
				Text: errText,
			},
		},
		IsError: true,
	}
}
