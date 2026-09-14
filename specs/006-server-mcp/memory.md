# Feature Memory: Model Context Protocol (MCP) Server

**Feature**: `specs/006-server-mcp`  
**Date**: 2026-09-15  
**Status**: Active  

---

## 1. Architectural Constraints & Boundaries

- **P-02 & ADR-002 (Separate Security & Authorization Domains)**:
  - The Android device write token (`Bearer <device-token>`) strictly allows message ingestion and MUST NOT grant MCP read access.
  - The AI Agent must authenticate using independent MCP credentials (e.g., `--mcp-token` or dedicated agent authentication header/handshake).
  - Default bind address remains `127.0.0.1`.
- **P-03 & ADR-008 (Strict Data Minimization & Zero-Sensitive Logging)**:
  - Structured server logs (`slog`) must NEVER log raw SMS content, verification codes, or extracted OTP values during MCP tool calls or event dispatch.
  - Debug logs may only record tool invocation names, sender filter criteria, message UUIDs, and timing metrics.
- **P-06 & ADR-004 (Event-Driven Agent MCP Interface)**:
  - `wait_for_message` MUST utilize an internal Go event broker (channels / sync.Cond broadcast / subscriber listeners) to wake immediately upon message ingestion, NEVER polling SQLite in a loop.
  - Structured timeouts: Must respect caller-provided timeout parameter (with safe server defaults and upper bounds) and return a clean structured timeout indicator if no message arrives.
  - Safe tool surface: Expose five core tools:
    1. `get_latest_message`: Fetch the most recent eligible SMS (optional sender filter).
    2. `get_messages`: Query messages with sender, device_id, timestamp (`after`), and limit filters.
    3. `search_messages`: Safe text search on authorized messages with limit.
    4. `wait_for_message`: Event-driven blocking wait with timeout and optional sender filter.
    5. `get_otp`: High-level convenience tool that waits for a message and extracts a numeric verification code via regex (default `\b\d{4,8}\b` or custom pattern).
- **P-08 (Clean Go Layering & Embedded Execution)**:
  - Embed MCP Server directly inside the standalone Go binary (`relayx-server`).
  - Layering: `internal/mcp/` -> `internal/service/` -> `internal/domain/` -> `internal/storage/`.
  - Zero external daemon dependencies (no separate Node/Python MCP bridge).
  - Transport support: Standard JSON-RPC 2.0 via standard I/O (`stdio` mode for CLI/local IDEs) and/or HTTP/SSE transport (`/mcp` endpoint) for networked agents.

---

## 2. Reused Decisions & Prior Patterns

- **ADR-001**: Standalone Go binary with embedded assets and migrations.
- **ADR-002**: Dual security domains separating device write from agent read.
- **ADR-004**: Event-driven push notification broker using Go channels for zero-polling concurrency.
- **ADR-007**: Pure-Go SQLite (`modernc.org/sqlite`) with WAL mode and concurrent reads.

---

## 3. Bug Patterns & Risks to Prevent

- **BUGS #1 (Sensitive Data Leakage)**: Ensure MCP request/response logging redacts message bodies and extracted OTPs in server console and SSE logcat.
- **BUGS #4 (SQLite Concurrency)**: Ensure concurrent MCP reads do not block device HTTP ingestion transactions; leverage WAL mode read pools.
- **BUGS #9 (Goroutine Leaks on Timeout/Disconnect)**: Subscriber channels in `wait_for_message` must always be unregistered and cleaned up on timeout, client disconnect, or context cancellation.
