# MCP Interface Contracts: RelayX Server

**Feature**: `specs/006-server-mcp`  
**Date**: 2026-09-15  
**Status**: Approved  

---

## 1. Protocol Transports

### A. Standard I/O (`stdio`)
- Executed via: `./bin/relayx-server --mcp-stdio` or `relayx-server mcp`
- Standard input (`os.Stdin`): JSON-RPC 2.0 requests delimited by newlines `\n`.
- Standard output (`os.Stdout`): JSON-RPC 2.0 responses delimited by newlines `\n`.
- Diagnostic logs (`slog`): Directed strictly to `os.Stderr` so stdout remains clean JSON-RPC.

### B. HTTP / Server-Sent Events (SSE)
- **SSE Stream**: `GET /mcp/sse`
  - Headers: `Accept: text/event-stream`, `Authorization: Bearer <mcp-token>` (or `?token=<mcp-token>`)
  - Emits: `event: endpoint\ndata: /mcp/messages?sessionId=<uuid>\n\n`
- **RPC Message Endpoint**: `POST /mcp/messages?sessionId=<uuid>`
  - Headers: `Content-Type: application/json`, `Authorization: Bearer <mcp-token>`
  - Body: Standard JSON-RPC request.

---

## 2. Tool Contracts

### Tool 1: `get_latest_message`
Returns the single most recent SMS matching the optional sender.

- **Parameters**:
  ```json
  {
    "type": "object",
    "properties": {
      "sender": {
        "type": "string",
        "description": "Optional sender address or phone number to filter by"
      }
    }
  }
  ```
- **Response**: JSON object of message metadata, or null if no message exists.

---

### Tool 2: `get_messages`
Returns a paginated list of stored SMS messages.

- **Parameters**:
  ```json
  {
    "type": "object",
    "properties": {
      "sender": {
        "type": "string",
        "description": "Filter by sender address"
      },
      "deviceId": {
        "type": "string",
        "description": "Filter by originating Android device ID"
      },
      "after": {
        "type": "integer",
        "description": "Filter messages received after epoch timestamp in milliseconds"
      },
      "limit": {
        "type": "integer",
        "description": "Maximum number of messages to return (default: 20, max: 100)"
      }
    }
  }
  ```

---

### Tool 3: `search_messages`
Searches stored messages by text query (safe substring/full-text query).

- **Parameters**:
  ```json
  {
    "type": "object",
    "required": ["query"],
    "properties": {
      "query": {
        "type": "string",
        "description": "Text or keyword to search for in message body or sender"
      },
      "limit": {
        "type": "integer",
        "description": "Maximum results to return (default: 20, max: 100)"
      }
    }
  }
  ```

---

### Tool 4: `wait_for_message`
Event-driven blocking call that waits for an incoming SMS to arrive. Wakes immediately on commit.

- **Parameters**:
  ```json
  {
    "type": "object",
    "properties": {
      "sender": {
        "type": "string",
        "description": "Optional sender name or shortcode to wait for"
      },
      "after": {
        "type": "integer",
        "description": "Only match messages received after this epoch ms timestamp (default: current server time minus 5s)"
      },
      "timeout": {
        "type": "integer",
        "description": "Maximum duration to wait in seconds (default: 30, max: 300)"
      }
    }
  }
  ```
- **Return (Success)**: Message object with `id`, `sender`, `body`, `receivedAt`.
- **Return (Timeout)**: `{"status": "timeout", "message": "No matching message received within 30s", "sender": "..."}`.

---

### Tool 5: `get_otp`
High-level tool that waits for an incoming SMS and extracts a numeric verification code.

- **Parameters**:
  ```json
  {
    "type": "object",
    "properties": {
      "sender": {
        "type": "string",
        "description": "Optional expected sender name or shortcode"
      },
      "regex": {
        "type": "string",
        "description": "Optional custom regex pattern with capture group (default: \\b\\d{4,8}\\b)"
      },
      "timeout": {
        "type": "integer",
        "description": "Maximum duration to wait in seconds (default: 30, max: 300)"
      }
    }
  }
  ```
- **Return (Success)**:
  ```json
  {
    "status": "success",
    "otp": "839201",
    "sender": "GOOGLE",
    "messageId": "msg-001",
    "receivedAt": 1726358400000
  }
  ```
