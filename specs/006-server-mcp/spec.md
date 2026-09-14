# Feature Specification: Model Context Protocol (MCP) Server

**Feature Branch**: `feature/006-server-mcp`  
**Created**: 2026-09-15  
**Status**: Draft  
**Feature Directory**: `specs/006-server-mcp`  
**Phase**: Phase 6 — Model Context Protocol (MCP) Server (`relayx-server`)  

---

## 1. Overview & Business Value

RelayX serves as a personal SMS relay and AI automation gateway. AI coding agents and autonomous testing workflows (e.g. Antigravity, Claude Code, Cursor, CI/CD runners) frequently require one-time passwords (OTPs) or two-factor SMS tokens to authenticate or verify workflows.

Currently, RelayX server receives and stores filtered SMS messages from Android gateways, but AI agents have no standard interface to discover, query, or wait for verification codes without custom API integrations.

Phase 6 embeds a standard **Model Context Protocol (MCP)** server directly inside the standalone Go binary (`relayx-server`). This enables AI assistants to seamlessly interface with RelayX as a native tool provider with zero busy-polling, strict privacy controls, and independent agent authorization.

---

## 2. User Scenarios & Testing

### User Story 1 - Event-Driven Message Awaiting for Fast OTP Ingestion (Priority: P1)

As an AI coding agent running an automated verification workflow, I want to call `wait_for_message` with an expected sender and a timeout so that I am immediately notified the millisecond an SMS arrives, without wasteful busy-polling.

**Why this priority**:
Event-driven push is the cornerstone capability of the RelayX MCP integration (Constitution Principle VI). Automated tests and agents should not poll the database or API every second, creating artificial load and latency.

**Independent Test**:
Start an MCP client session, invoke `wait_for_message(sender="MYBANK", timeout=10)`. Concurrently post a message with sender `"MYBANK"`. Verify the tool unblocks instantly with the incoming message payload and does not poll SQLite.

**Acceptance Scenarios**:
1. **Given** an agent is connected and waiting on `wait_for_message(sender="GITHUB", timeout=30)`, **When** a matching SMS from `"GITHUB"` is ingested, **Then** the MCP tool call completes immediately returning the received message details.
2. **Given** an agent calls `wait_for_message` with a timestamp parameter (`after`), **When** an SMS arrived prior to that timestamp, **Then** the tool ignores it and continues waiting until a newer matching message arrives or timeout triggers.
3. **Given** an agent calls `wait_for_message` with `timeout=5`, **When** no matching message arrives within 5 seconds, **Then** the tool unblocks and returns a structured timeout result without erroring or crashing.

---

### User Story 2 - One-Step OTP Extraction via Tool (Priority: P2)

As an AI developer or automation agent, I want a specialized `get_otp` tool that combines waiting for a message and extracting numeric verification codes via regex, so that my prompt context receives only the clean token needed for authentication.

**Why this priority**:
Minimizes token usage and reduces hallucination risk by parsing OTP codes server-side before feeding them into the model.

**Independent Test**:
Call `get_otp(sender="GOOGLE", timeout=15)`. Post an SMS `"G-492019 is your Google verification code"`. Verify the tool returns `otp: "492019"` along with sender and arrival metadata.

**Acceptance Scenarios**:
1. **Given** an incoming message `"Your login code is 849201"`, **When** `get_otp` is invoked with default settings, **Then** it returns code `"849201"`.
2. **Given** an agent provides a custom regex `code:\s*([A-Z0-9]{6})`, **When** a matching alphanumeric code arrives, **Then** the captured group is returned as the OTP.
3. **Given** a message arrives from the expected sender but contains no recognizable OTP code, **Then** `get_otp` returns the message metadata with an explicit failure indicator for OTP extraction.

---

### User Story 3 - Historical Message Discovery & Search (Priority: P3)

As an AI assistant auditing past alerts or verifying delivery, I want tools `get_latest_message`, `get_messages`, and `search_messages` to query stored messages by sender, timestamp, and keyword.

**Why this priority**:
Allows agents to inspect previous messages if they were received before the agent began waiting or to verify multi-step sequences.

**Independent Test**:
Store 5 test messages. Call `get_latest_message`, `get_messages(limit=2)`, and `search_messages(query="invoice")`. Verify all return correct subsets without database corruption.

**Acceptance Scenarios**:
1. **Given** multiple messages in storage, **When** `get_latest_message` is called without parameters, **Then** the single most recent message across all senders is returned.
2. **Given** messages from multiple senders, **When** `get_latest_message(sender="BANK")` is called, **Then** only the latest message from `"BANK"` is returned.
3. **Given** stored messages, **When** `search_messages(query="password")` is called, **Then** matching messages containing `"password"` are returned up to the requested limit.

---

### User Story 4 - Independent Agent Authorization & Privacy Boundary (Priority: P4)

As a security-conscious administrator, I want AI Agents connecting via MCP to use a separate authorization token distinct from the Android write token, and I want all sensitive payloads masked in server diagnostic logs.

**Why this priority**:
Enforces Principle II and Principle III of the RelayX Constitution: an agent must not possess device write capabilities, and device write tokens must not grant agent read access.

**Independent Test**:
Attempt an MCP connection using an invalid token or the Android device write token. Verify rejection with unauthorized status. Inspect server console logs during successful tool calls to confirm message body and OTP redaction.

**Acceptance Scenarios**:
1. **Given** the server configured with `--token "device-secret"` and `--mcp-token "agent-secret"`, **When** an agent attempts connection using `"device-secret"`, **Then** the request is rejected with authorization failure.
2. **Given** a valid agent connection, **When** `get_otp` or `wait_for_message` returns a code, **Then** server logcat and stdout record the tool call event but mask the OTP code and message content.

---

### Edge Cases

- **Concurrent Agent Waits**: Multiple agents waiting simultaneously on the same or different senders wake up reliably without race conditions or dropped notifications.
- **Client Disconnection During Wait**: If an agent disconnects or cancels its request while `wait_for_message` is blocked, internal listeners are immediately garbage collected to prevent goroutine/memory leaks.
- **Clock Skew / Past Timestamps**: If `wait_for_message` receives an `after` timestamp far in the future or past, it handles it safely without infinite blocking.
- **Malformed Regex in `get_otp`**: If an agent supplies an invalid regex, the server returns a clean tool error rather than panicking or executing a ReDoS attack.

---

## 3. Requirements

### Functional Requirements

- **FR-001**: Server MUST embed an MCP-compliant server directly within the standalone binary executable, supporting JSON-RPC 2.0.
- **FR-002**: Server MUST support stdio transport for local agent process execution (e.g. `relayx-server mcp` or `--mcp-stdio`) and HTTP/SSE transport for network-connected agents.
- **FR-003**: Server MUST expose the following five standard MCP tools:
  1. `get_latest_message`: parameters `sender` (optional, string).
  2. `get_messages`: parameters `sender` (optional, string), `device_id` (optional, string), `after` (optional, integer epoch ms), `limit` (optional, integer, default 20, max 100).
  3. `search_messages`: parameters `query` (required, string), `limit` (optional, integer, default 20, max 100).
  4. `wait_for_message`: parameters `sender` (optional, string), `after` (optional, integer epoch ms), `timeout` (optional, integer seconds, default 30, max 300).
  5. `get_otp`: parameters `sender` (optional, string), `regex` (optional, string), `timeout` (optional, integer seconds, default 30, max 300).
- **FR-004**: `wait_for_message` and `get_otp` MUST use an event pub/sub mechanism to wake waiting goroutines upon message persistence, with zero database polling.
- **FR-005**: `wait_for_message` and `get_otp` MUST return a structured response indicating timeout expiration when no matching message arrives within the specified duration.
- **FR-006**: Server MUST enforce an independent agent authentication domain via `--mcp-token` CLI flag / environment variable. Connections lacking the valid MCP token MUST be rejected.
- **FR-007**: Server structured logging (`slog`) MUST redact SMS message bodies and OTP codes during MCP tool handling.
- **FR-008**: Safe regex compilation MUST be enforced on `get_otp` with timeouts or length limits to protect against Regular Expression Denial of Service (ReDoS).

### Key Entities

- **MCP Tool Definition**: Describes tool name, description, and JSON Schema for input parameters.
- **Message Event**: Internal pub/sub payload emitted upon successful message persistence, carrying `id`, `sender`, `body`, `received_at`, and `device_id`.
- **OTP Extraction Result**: Structured tool output containing `otp`, `sender`, `message_id`, and `extracted_at`.
- **Wait Subscription**: Internal channel registered with the event broker, containing filter criteria and cancellation context.

---

## 4. Success Criteria

### Measurable Outcomes

- **SC-001**: `wait_for_message` wakes and returns the matching message to the client within 50 milliseconds of database commit.
- **SC-002**: CPU usage remains near 0% while waiting for messages (zero busy-polling cycles).
- **SC-003**: 100% of tool invocations execute without logging sensitive SMS content or OTP numbers in server output.
- **SC-004**: System supports at least 20 concurrent waiting MCP clients without resource leakage or deadlocks.
- **SC-005**: Full integration test suite verifies MCP tools and timeout mechanics with zero flaky failures.

---

## 5. Assumptions

- Standard Model Context Protocol specification (2024-11-05 or latest compatible schema) is used for JSON-RPC message formatting.
- Default numeric OTP extraction regex `\b\d{4,8}\b` covers over 95% of standard SMS verification codes.
- Local stdio transport is primarily utilized for local AI tooling (Antigravity, Cursor, Claude Code), while SSE transport enables remote or containerized AI agents.
