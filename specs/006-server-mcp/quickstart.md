# Quickstart: Testing RelayX Model Context Protocol (MCP) Server

**Feature**: `specs/006-server-mcp`  
**Date**: 2026-09-15  

---

## 1. Running in Stdio Mode (CLI / IDE Integration)

Launch the server in stdio mode:

```bash
cd relayx-server
go run ./cmd/server --mcp-stdio --db ./data/sms.db
```

Send JSON-RPC requests via stdin:

### Initialize Handshake
```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0.0"}}}
```

### List Available Tools
```json
{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
```

### Call `wait_for_message`
```json
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"wait_for_message","arguments":{"sender":"BANK","timeout":10}}}
```

---

## 2. Running in HTTP / SSE Mode

Start the server normally with an authorized MCP token:

```bash
cd relayx-server
./bin/relayx-server --port 8080 --mcp-token "agent-secret-token"
```

### Connecting with an MCP Client
Add to your Claude / Antigravity / Cursor MCP configuration:

```json
{
  "mcpServers": {
    "relayx": {
      "command": "/path/to/relayx-server",
      "args": ["--mcp-stdio", "--db", "/path/to/sms.db"]
    }
  }
}
```

Or via SSE:

```json
{
  "mcpServers": {
    "relayx": {
      "url": "http://127.0.0.1:8080/mcp/sse",
      "headers": {
        "Authorization": "Bearer agent-secret-token"
      }
    }
  }
}
```

---

## 3. End-to-End Verification Flow

1. In Terminal 1, call `wait_for_message`:
   ```bash
   # Waiting for OTP...
   ```
2. In Terminal 2, simulate SMS ingestion from Android:
   ```bash
   curl -X POST http://127.0.0.1:8080/api/v1/messages \
     -H "Authorization: Bearer <device-token>" \
     -H "Content-Type: application/json" \
     -d '{"messageId":"test-1","sender":"BANK","body":"Your OTP is 928374"}'
   ```
3. Terminal 1 immediately returns the message object with `< 50ms` latency.
