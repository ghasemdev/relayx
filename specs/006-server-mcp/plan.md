# Implementation Plan: Model Context Protocol (MCP) Server

**Branch**: `feature/006-server-mcp` | **Date**: 2026-09-15 | **Spec**: [spec.md](file:///Volumes/ADATASD810/Projects/Android/relayx/specs/006-server-mcp/spec.md)

**Input**: Feature specification from [`specs/006-server-mcp/spec.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/specs/006-server-mcp/spec.md)

---

## Summary

Embed an official-compliant Model Context Protocol (MCP) server directly inside the standalone Go binary (`relayx-server`). Implements JSON-RPC 2.0 protocol over standard input/output (`stdio`) and HTTP Server-Sent Events (`SSE`). Delivers five core tools (`get_latest_message`, `get_messages`, `search_messages`, `wait_for_message`, `get_otp`) backed by an in-memory event pub/sub broker to enable zero-busy-polling instant push notifications for waiting AI agents. Enforces separate authentication domains (`--mcp-token` vs `--token`), zero-sensitive logging redaction, and linear-time regex extraction.

---

## Technical Context

**Language/Version**: Go 1.27+  
**Primary Dependencies**: Standard library (`net/http`, `bufio`, `encoding/json`, `sync`, `regexp`, `context`), `github.com/google/uuid`  
**Storage**: Pure-Go SQLite (`modernc.org/sqlite` via `internal/storage/`)  
**Testing**: Go standard testing package (`testing`), `httptest`  
**Target Platform**: Linux (amd64, arm64), macOS Darwin (amd64, arm64), Windows  
**Project Type**: Standalone Go Server / Embedded Protocol Engine (`relayx-server`)  
**Performance Goals**: Event-driven notification wakeup < 10ms upon message persistence; 0% CPU consumption while waiting; support 20+ concurrent waiting agents  
**Constraints**: Zero sensitive logging (no message body or OTP exposed in console/telemetry); zero external daemons or node bridges; independent agent read authorization  
**Scale/Scope**: 5 MCP tools; 2 transports (`stdio`, `SSE`); embedded in single binary  

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status | Notes |
|:---|:---|:---|:---|
| **P-01 (Zero-Infrastructure Server)** | Single self-contained Go binary; no Docker/Node/Python dependencies | **PASS** | Native Go JSON-RPC implementation without third-party runtimes. |
| **P-02 (Separate Security Domains)** | Android write token != MCP read token; default bind `127.0.0.1` | **PASS** | Independent `--mcp-token` flag; default auto-generated token on startup (`TASK-SEC-014`); CORS restricted to localhost/allowed origins (`TASK-SEC-013`). |
| **P-03 (Strict Data Minimization)** | Standard and debug logs never record SMS bodies or OTP values | **PASS** | Redacting logger applied to all MCP requests and responses. |
| **P-04 (Device-Side Pre-Filtering)** | Client rules evaluated on Android before server transmission | **PASS** | Server only holds pre-filtered SMS. |
| **P-05 (Durable Delivery)** | Deduplication and SQLite WAL transactions preserved | **PASS** | Ingestion pipeline unchanged; broker taps into committed events. |
| **P-06 (Event-Driven MCP Interface)** | `wait_for_message` uses internal Go channels/sync, NO busy-polling | **PASS** | Core architectural foundation: `internal/mcp/broker.go`. |
| **P-07 (Complete Mock SMS Parity)** | Mock SMS routes to SQLite and wakes waiting MCP agents identically | **PASS** | Shared server ingestion path triggers event broker. |
| **P-08 (Clean Architecture)** | Clean Go layering: `cmd/` -> `internal/mcp/` -> `internal/service/` | **PASS** | Follows standard package structure. |
| **P-09 (Safety Limits)** | No arbitrary command execution or raw SQL execution via MCP | **PASS** | Only the 5 authorized, typed tools exposed. |

---

## Project Structure

### Documentation (this feature)

```text
specs/006-server-mcp/
├── memory.md            # Active feature context & governance constraints
├── memory-synthesis.md  # Synthesized AI guidance
├── spec.md              # Feature specification & user stories (P1-P4)
├── plan.md              # This implementation plan
├── research.md          # Phase 0 protocol, transport, and broker architecture decisions
├── data-model.md        # Phase 1 wire types, schemas, and broker entities
├── quickstart.md        # Phase 1 developer verification guide
├── contracts/           # Phase 1 interface contracts
│   └── mcp-contracts.md
└── tasks.md             # Phase 2 actionable task breakdown (via /speckit-tasks)
```

### Source Code (repository root)

```text
relayx-server/
├── cmd/server/
│   └── main.go                         # Add --mcp-stdio flag, init MCP server, wire event broker
├── internal/
│   ├── config/
│   │   ├── config.go                   # Add MCPToken and MCPStdio fields & flags
│   │   └── config_test.go              # Test MCP config flags
│   ├── domain/
│   │   └── mcp.go                      # Domain types for MCP tools and subscriptions
│   ├── mcp/
│   │   ├── broker.go                   # In-memory pub/sub event broker with Go channels
│   │   ├── broker_test.go              # Concurrency & timeout unit tests for broker
│   │   ├── protocol.go                 # JSON-RPC 2.0 request/response parsing and errors
│   │   ├── server.go                   # MCP server core: tool registry, dispatch, session manager
│   │   ├── stdio.go                    # Stdio transport runner (reads stdin, writes stdout)
│   │   ├── sse.go                      # HTTP/SSE transport handler (/mcp/sse, /mcp/messages)
│   │   ├── tools.go                    # Implementation of 5 tools (wait_for_message, get_otp, etc.)
│   │   └── tools_test.go               # Unit & integration tests for all 5 tools
│   ├── service/
│   │   └── message_service.go          # Trigger event broker publish on successful message ingestion
│   └── api/
│       └── server.go                   # Register /mcp/sse and /mcp/messages routes with MCP auth
```

---

## Complexity Tracking

*No violations. Clean standard-library implementation with zero external runtime dependencies.*

---

## Security Hardening (Post-Review Decisions)

- **CORS & Origin Isolation (`TASK-SEC-013`)**: Remove `Access-Control-Allow-Origin: *` on `/mcp/sse`; validate that `Origin` originates from localhost or configured client origin.
- **Default Authentication Enforcement (`TASK-SEC-014`)**: Automatically generate `rx-mcp-<uuid>` if `--mcp-token` is unspecified at startup to guarantee remote/LAN bindings are authenticated by default.
- **Session-Scoped Async Tool Execution (`TASK-SEC-015`)**: Ensure tool execution context inside asynchronous POST `/mcp/messages` goroutines is tied to the long-lived SSE session lifecycle rather than ephemeral HTTP request context.
