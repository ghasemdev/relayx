package mcp

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"sync"

	"relayx-server/internal/domain"
)

// ToolHandler defines the function signature for executing an MCP tool.
type ToolHandler func(ctx context.Context, args json.RawMessage) (domain.CallToolResult, error)

// Server implements the MCP server protocol dispatcher.
type Server struct {
	mu       sync.RWMutex
	tools    []domain.Tool
	handlers map[string]ToolHandler
	logger   *slog.Logger
}

// NewServer creates a new MCP server dispatcher instance.
func NewServer(logger *slog.Logger) *Server {
	if logger == nil {
		logger = slog.Default()
	}
	return &Server{
		tools:    make([]domain.Tool, 0),
		handlers: make(map[string]ToolHandler),
		logger:   logger,
	}
}

// RegisterTool adds a tool schema and execution handler to the server registry.
func (s *Server) RegisterTool(tool domain.Tool, handler ToolHandler) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.tools = append(s.tools, tool)
	s.handlers[tool.Name] = handler
}

// HandleRequest processes an incoming JSON-RPC 2.0 request and produces an RPCResponse.
func (s *Server) HandleRequest(ctx context.Context, req *domain.RPCRequest) *domain.RPCResponse {
	if req == nil {
		return NewErrorResponse(nil, domain.CodeInvalidRequest, "nil request", nil)
	}

	switch req.Method {
	case "initialize":
		s.logger.Info("mcp client connected and initialized")
		res := domain.InitializeResult{
			ProtocolVersion: domain.MCPProtocolVersion,
			Capabilities: domain.ServerCapabilities{
				Tools: &domain.ToolsCapability{ListChanged: false},
			},
			ServerInfo: domain.ServerInfo{
				Name:    "relayx-server",
				Version: "1.0.0",
			},
		}
		return NewSuccessResponse(req.ID, res)

	case "notifications/initialized":
		// Notification per MCP spec; no response needed
		return nil

	case "ping":
		return NewSuccessResponse(req.ID, map[string]any{})

	case "tools/list":
		s.mu.RLock()
		toolsCopy := make([]domain.Tool, len(s.tools))
		copy(toolsCopy, s.tools)
		s.mu.RUnlock()
		return NewSuccessResponse(req.ID, map[string]any{
			"tools": toolsCopy,
		})

	case "tools/call":
		var params domain.CallToolParams
		if len(req.Params) > 0 {
			if err := json.Unmarshal(req.Params, &params); err != nil {
				return NewErrorResponse(req.ID, domain.CodeInvalidParams, "invalid tools/call params", err.Error())
			}
		}

		if params.Name == "" {
			return NewErrorResponse(req.ID, domain.CodeInvalidParams, "tool name is required", nil)
		}

		s.mu.RLock()
		handler, exists := s.handlers[params.Name]
		s.mu.RUnlock()

		if !exists {
			return NewErrorResponse(req.ID, domain.CodeMethodNotFound, fmt.Sprintf("tool '%s' not found", params.Name), nil)
		}

		s.logger.Info("executing mcp tool", "tool", params.Name)

		result, err := handler(ctx, params.Arguments)
		if err != nil {
			s.logger.Error("mcp tool execution error", "tool", params.Name, "error", err)
			return NewSuccessResponse(req.ID, NewErrorToolResult(err.Error()))
		}

		return NewSuccessResponse(req.ID, result)

	default:
		return NewErrorResponse(req.ID, domain.CodeMethodNotFound, fmt.Sprintf("method '%s' not found", req.Method), nil)
	}
}
