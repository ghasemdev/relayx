package domain

import "encoding/json"

// RPCRequest represents a standard JSON-RPC 2.0 request or notification.
type RPCRequest struct {
	JSONRPC string          `json:"jsonrpc"`
	ID      any             `json:"id,omitempty"`
	Method  string          `json:"method"`
	Params  json.RawMessage `json:"params,omitempty"`
}

// RPCResponse represents a standard JSON-RPC 2.0 response.
type RPCResponse struct {
	JSONRPC string    `json:"jsonrpc"`
	ID      any       `json:"id"`
	Result  any       `json:"result,omitempty"`
	Error   *RPCError `json:"error,omitempty"`
}

// RPCError represents standard JSON-RPC error payload.
type RPCError struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
	Data    any    `json:"data,omitempty"`
}

// Standard JSON-RPC 2.0 Error Codes
const (
	CodeParseError     = -32700
	CodeInvalidRequest = -32600
	CodeMethodNotFound = -32601
	CodeInvalidParams  = -32602
	CodeInternalError  = -32603
)

// MCP Protocol Version supported.
const MCPProtocolVersion = "2024-11-05"

// ClientInfo represents connecting MCP client information.
type ClientInfo struct {
	Name    string `json:"name"`
	Version string `json:"version"`
}

// InitializeParams represents parameters in initialize request.
type InitializeParams struct {
	ProtocolVersion string         `json:"protocolVersion"`
	Capabilities    map[string]any `json:"capabilities,omitempty"`
	ClientInfo      ClientInfo     `json:"clientInfo,omitempty"`
}

// ServerCapabilities defines features offered by the MCP server.
type ServerCapabilities struct {
	Tools *ToolsCapability `json:"tools,omitempty"`
}

// ToolsCapability indicates tool support.
type ToolsCapability struct {
	ListChanged bool `json:"listChanged"`
}

// ServerInfo defines the MCP server identity.
type ServerInfo struct {
	Name    string `json:"name"`
	Version string `json:"version"`
}

// InitializeResult represents the response to initialize.
type InitializeResult struct {
	ProtocolVersion string             `json:"protocolVersion"`
	Capabilities    ServerCapabilities `json:"capabilities"`
	ServerInfo      ServerInfo         `json:"serverInfo"`
}

// Tool represents a registered MCP tool definition.
type Tool struct {
	Name        string          `json:"name"`
	Description string          `json:"description"`
	InputSchema ToolInputSchema `json:"inputSchema"`
}

// ToolInputSchema defines the JSON Schema for tool parameters.
type ToolInputSchema struct {
	Type       string                    `json:"type"`
	Properties map[string]PropertySchema `json:"properties"`
	Required   []string                  `json:"required,omitempty"`
}

// PropertySchema defines parameter field properties.
type PropertySchema struct {
	Type        string `json:"type"`
	Description string `json:"description"`
}

// CallToolParams represents parameters for tools/call.
type CallToolParams struct {
	Name      string          `json:"name"`
	Arguments json.RawMessage `json:"arguments,omitempty"`
}

// CallToolResult represents the outcome of executing a tool.
type CallToolResult struct {
	Content []ContentItem `json:"content"`
	IsError bool          `json:"isError,omitempty"`
}

// ContentItem represents a content block returned by an MCP tool.
type ContentItem struct {
	Type string `json:"type"`
	Text string `json:"text"`
}

// Tool parameter structs
type GetLatestMessageArgs struct {
	Sender string `json:"sender,omitempty"`
}

type GetMessagesArgs struct {
	Sender   string `json:"sender,omitempty"`
	DeviceID string `json:"deviceId,omitempty"`
	After    int64  `json:"after,omitempty"`
	Limit    int    `json:"limit,omitempty"`
}

type SearchMessagesArgs struct {
	Query string `json:"query"`
	Limit int    `json:"limit,omitempty"`
}

type WaitForMessageArgs struct {
	Sender  string `json:"sender,omitempty"`
	After   int64  `json:"after,omitempty"`
	Timeout int    `json:"timeout,omitempty"` // seconds
}

type GetOTPArgs struct {
	Sender  string `json:"sender,omitempty"`
	Regex   string `json:"regex,omitempty"`
	Timeout int    `json:"timeout,omitempty"` // seconds
}

// Structured results
type WaitForMessageResult struct {
	Status  string          `json:"status"` // "success" or "timeout"
	Message *Message        `json:"message,omitempty"`
	Error   string          `json:"error,omitempty"`
}

type GetOTPResult struct {
	Status     string `json:"status"` // "success", "timeout", or "no_match"
	OTP        string `json:"otp,omitempty"`
	Sender     string `json:"sender,omitempty"`
	MessageID  string `json:"messageId,omitempty"`
	ReceivedAt int64  `json:"receivedAt,omitempty"`
	Error      string `json:"error,omitempty"`
}
