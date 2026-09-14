# Technical Research & Architectural Decisions: Model Context Protocol (MCP) Server

**Feature**: `specs/006-server-mcp`  
**Date**: 2026-09-15  
**Status**: Completed  

---

## 1. MCP Transport & Protocol Architecture

### Decision
Implement a native, zero-external-dependency Model Context Protocol (MCP) engine in Go within `internal/mcp/`, supporting:
1. **Standard I/O (`stdio`)**: Reads line-delimited JSON-RPC 2.0 requests from `os.Stdin` and writes newline-delimited JSON-RPC 2.0 responses to `os.Stdout`. Activated via CLI flag `--mcp-stdio` or subcommand `mcp`.
2. **Server-Sent Events (`SSE`) & HTTP POST**: Exposed on `/mcp/sse` (event stream) and `/mcp/messages` (JSON-RPC POST) for remote or networked AI agents.

### Rationale
- **Zero External Dependencies**: Standard Go (`encoding/json`, `net/http`, `bufio`) keeps the executable lightweight, statically cross-compilable, and free of dependency vulnerabilities (Constitution Principle I & VIII).
- **Universal Agent Compatibility**: Local agent tools (Antigravity, Cursor, Claude Code) connect natively over `stdio`, while networked CI/CD agents or Dockerized harnesses connect over HTTP/SSE.
- **Protocol Compliance**: Implements the standard JSON-RPC 2.0 lifecycle:
  - `initialize` -> Handshake exchanging protocol version (`2024-11-05`), server capabilities (`tools`), and server info (`name: "relayx-server"`, `version: "1.0.0"`).
  - `notifications/initialized` -> Client initialization acknowledgement.
  - `tools/list` -> Returns schemas for the 5 exposed tools.
  - `tools/call` -> Executes requested tool and returns structured content responses or errors.
  - `ping` -> Protocol liveness probe.

### Alternatives Considered
- **Third-party SDK (`mark3labs/mcp-go`)**: Evaluated, but introduces additional transitive dependencies and opinionated routing abstractions. A native implementation gives RelayX total control over redaction, event broker lifecycle, and embedded routing.
- **REST-only polling**: Violates Constitution Principle VI ("Push Notifications Over Polling").

---

## 2. Event-Driven Push Broker for `wait_for_message`

### Decision
Implement an in-memory pub/sub broker (`internal/mcp/broker.go`) utilizing Go channels and `sync.RWMutex` to wake waiting callers instantly upon message ingestion.

### Architecture & Lifecycle
1. **Subscriber Registration**:
   ```go
   sub := broker.Subscribe(filterCriteria)
   defer broker.Unsubscribe(sub)
   ```
2. **Pre-Check**: Before blocking, the handler checks SQLite for any existing message matching the filter received after the specified `after` timestamp. If found, returns immediately.
3. **Non-Blocking Channel Notification**: When `MessageService.Ingest` commits a new message, it triggers `broker.Publish(message)`. The broker iterates active subscriptions and dispatches matching message pointers into buffered channels (`chan *domain.Message`).
4. **Structured Multiplexing (`select`)**:
   ```go
   select {
   case msg := <-sub.Channel():
       return formatToolResult(msg)
   case <-time.After(timeoutDuration):
       return formatTimeoutResult(timeoutDuration)
   case <-ctx.Done():
       return nil, ctx.Err()
   }
   ```

### Rationale
- Zero database queries while waiting (0% CPU busy-polling).
- Sub-millisecond latency from ingestion to MCP agent return.
- `defer broker.Unsubscribe(sub)` guarantees zero goroutine or memory leaks on client disconnect or timeout.

---

## 3. Dual Security Domains & Token Isolation

### Decision
Introduce independent configuration parameter `--mcp-token` (`RELAYX_MCP_TOKEN`):
- Android Write Token (`--token`): Only authorizes `POST /api/v1/messages`. Forbidden from querying MCP tools or reading SMS.
- MCP Read Token (`--mcp-token`): Authorizes MCP connections over HTTP/SSE (`Authorization: Bearer <mcp-token>` or `?token=<mcp-token>`).
- Stdio Transport: Governed by the local host OS process execution boundary. Can optionally enforce `--mcp-token` if provided.

### Rationale
- Enforces Principle II of the RelayX Constitution: Compromise of an Android gateway write credential cannot be leveraged to read stored SMS history or intercept OTPs via MCP.

---

## 4. Regex Safety & Extraction Engine for `get_otp`

### Decision
- Standard Go `regexp` package for pattern compilation and extraction.
- Default pattern: `\b\d{4,8}\b` (matches 4 to 8 contiguous digits).
- Custom pattern support: Allows agents to supply specific capture groups (e.g. `(?:code|pin|is)\s*:?\s*([0-9A-Z]{4,8})`).

### Rationale
- Go's `regexp` engine uses the Thompson NFA algorithm (RE2), which guarantees $O(n)$ linear-time matching against input length. It is mathematically immune to catastrophic backtracking (ReDoS), protecting the server against malicious regex attacks without requiring external sandboxing.
