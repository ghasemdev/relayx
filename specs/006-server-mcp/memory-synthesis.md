# Memory Synthesis: Model Context Protocol (MCP) Server

**Feature**: `specs/006-server-mcp`  
**Date**: 2026-09-15  
**Synthesized For**: Spec & Plan Generation  

---

## 1. System Scope & Objective

Phase 6 implements the Model Context Protocol (MCP) Server directly embedded inside the standalone Go binary (`relayx-server`). It bridges local AI Agents (such as Antigravity, Claude Code, Cursor, and custom automation scripts) to the SMS gateway:
1. **Embedded Server Engine**: Zero-dependency Go implementation supporting standard MCP protocol (JSON-RPC 2.0) over `stdio` and/or HTTP/SSE transport.
2. **Dual-Domain Security**: Independent agent read token (`--mcp-token`) distinct from the Android write token (`--token`).
3. **Event-Driven Push Broker**: `wait_for_message` uses an internal pub/sub event channel to immediately wake callers on SMS ingestion without busy-polling SQLite.
4. **Comprehensive Tool Suite**:
   - `get_latest_message`: Returns latest message, optionally filtered by sender.
   - `get_messages`: Paginated list of messages with sender, device ID, and timestamp criteria.
   - `search_messages`: Substring/keyword search over stored message metadata and content.
   - `wait_for_message`: Blocking wait for incoming message matching criteria with structured timeout.
   - `get_otp`: High-level convenience tool extracting numeric verification codes using regex.
5. **Zero-Sensitive Logging**: Strict redaction of SMS bodies and extracted OTPs from server console and streaming telemetry.

---

## 2. Hard Governance Gates

1. **Constitution P-02 & ADR-002**: Device ingestion token MUST NOT grant MCP query access.
2. **Constitution P-03 & ADR-008**: Logs must never reveal SMS text or OTP codes during tool invocation.
3. **Constitution P-06 & ADR-004**: No busy-polling loops in `wait_for_message`. Must use Go event notification.
4. **Constitution P-08**: Clean Go layering inside `internal/mcp/` without breaking existing HTTP `/api/v1` routes or dashboard.
