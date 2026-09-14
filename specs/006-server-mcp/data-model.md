# Data Model: Model Context Protocol (MCP) Server

**Feature**: `specs/006-server-mcp`  
**Date**: 2026-09-15  
**Status**: Completed  

---

## 1. JSON-RPC 2.0 & MCP Wire Types

```go
// RPCRequest represents a standard JSON-RPC 2.0 incoming request or notification.
type RPCRequest struct {
    JSONRPC string          `json:"jsonrpc"`           // Must be "2.0"
    ID      any             `json:"id,omitempty"`     // String, Number, or null (absent for notifications)
    Method  string          `json:"method"`           // e.g. "initialize", "tools/list", "tools/call"
    Params  json.RawMessage `json:"params,omitempty"` // Method parameters
}

// RPCResponse represents a standard JSON-RPC 2.0 response.
type RPCResponse struct {
    JSONRPC string    `json:"jsonrpc"`           // "2.0"
    ID      any       `json:"id"`                // Matches request ID
    Result  any       `json:"result,omitempty"`  // Success payload
    Error   *RPCError `json:"error,omitempty"`   // Error payload
}

// RPCError represents standard JSON-RPC error details.
type RPCError struct {
    Code    int    `json:"code"`
    Message string `json:"message"`
    Data    any    `json:"data,omitempty"`
}

// Standard Error Codes
const (
    CodeParseError     = -32700
    CodeInvalidRequest = -32600
    CodeMethodNotFound = -32601
    CodeInvalidParams  = -32602
    CodeInternalError  = -32603
)
```

---

## 2. MCP Protocol Entities

### Initialize Handshake

```json
{
  "protocolVersion": "2024-11-05",
  "capabilities": {
    "tools": {
      "listChanged": false
    }
  },
  "serverInfo": {
    "name": "relayx-server",
    "version": "1.0.0"
  }
}
```

### Tool Definition Schema

```go
type Tool struct {
    Name        string                 `json:"name"`
    Description string                 `json:"description"`
    InputSchema ToolInputSchema        `json:"inputSchema"`
}

type ToolInputSchema struct {
    Type       string                     `json:"type"`       // "object"
    Properties map[string]PropertySchema `json:"properties"`
    Required   []string                   `json:"required,omitempty"`
}

type PropertySchema struct {
    Type        string `json:"type"`
    Description string `json:"description"`
}
```

### Tool Call Content Output

```go
type CallToolResult struct {
    Content []ContentItem `json:"content"`
    IsError bool          `json:"isError,omitempty"`
}

type ContentItem struct {
    Type string `json:"type"` // "text"
    Text string `json:"text"` // JSON-stringified result or error message
}
```

---

## 3. Internal Event Broker Entities

```go
// MessageSubscription represents an active waiting listener in the server.
type MessageSubscription struct {
    ID        string
    Sender    string                   // Optional filter: empty string matches any sender
    After     time.Time                // Only match messages received after this timestamp
    Channel   chan *domain.Message     // Buffered delivery channel (cap 1)
    Done      chan struct{}            // Closed on unsubscription
}

// EventBroker maintains active subscriptions and dispatches ingested messages.
type EventBroker interface {
    Subscribe(sender string, after time.Time) *MessageSubscription
    Unsubscribe(sub *MessageSubscription)
    Publish(msg *domain.Message)
}
```

---

## 4. Tool Domain Outputs

### `wait_for_message` / `get_latest_message` Output
```json
{
  "id": "msg_01J8ABC...",
  "sender": "MYBANK",
  "body": "Your verification code is 482910",
  "receivedAt": 1726358400000,
  "deviceId": "gateway-phone-01"
}
```

### `wait_for_message` Timeout Output
```json
{
  "status": "timeout",
  "message": "No matching message received within 30 seconds",
  "sender": "MYBANK"
}
```

### `get_otp` Output
```json
{
  "status": "success",
  "otp": "482910",
  "sender": "MYBANK",
  "messageId": "msg_01J8ABC...",
  "receivedAt": 1726358400000
}
```
