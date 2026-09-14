# Tasks: Model Context Protocol (MCP) Server

**Input**: Feature specification [`specs/006-server-mcp/spec.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/specs/006-server-mcp/spec.md) and Plan [`specs/006-server-mcp/plan.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/specs/006-server-mcp/plan.md)  
**Branch**: `feature/006-server-mcp`  
**Phase**: Phase 6 — Model Context Protocol (MCP) Server (`relayx-server`)  

---

## Phase 1: Setup (Configuration & Domain Model)

**Purpose**: Establish configuration flags, domain types, and JSON-RPC 2.0 / MCP wire models.

- [x] T001 [P] Define MCP domain types, JSON-RPC 2.0 wire models, and tool schema definitions in `relayx-server/internal/domain/mcp.go`
- [x] T002 [P] Add `--mcp-token` and `--mcp-stdio` configuration parameters to `relayx-server/internal/config/config.go`
- [x] T003 [P] Add unit tests for MCP configuration flags in `relayx-server/internal/config/config_test.go`

---

## Phase 2: Foundational (Event Broker & JSON-RPC Protocol Core)

**Purpose**: Core pub/sub broker and JSON-RPC dispatch infrastructure that all MCP tools depend upon.

- [x] T004 Implement in-memory pub/sub event broker (`EventBroker`) in `relayx-server/internal/mcp/broker.go`
- [x] T005 [P] Unit tests for `EventBroker` subscription, matching, and unsubscription in `relayx-server/internal/mcp/broker_test.go`
- [x] T006 [P] Implement JSON-RPC 2.0 request parsing, response formatting, and standard error helpers in `relayx-server/internal/mcp/protocol.go`
- [x] T007 [P] Implement MCP Server core registry with `initialize`, `tools/list`, `tools/call`, and `ping` in `relayx-server/internal/mcp/server.go`
- [x] T008 Integrate `EventBroker` publication inside `relayx-server/internal/service/message_service.go` on message creation

---

## Phase 3: User Story 1 - Event-Driven Message Awaiting (Priority: P1) 🎯 MVP

**Goal**: AI agents can invoke `wait_for_message` to block and wake immediately upon SMS receipt without database polling.

**Independent Test**: Connect an MCP client over stdio, invoke `wait_for_message(sender="BANK", timeout=5)`, post an SMS from "BANK" concurrently, and verify the tool returns the message within 50ms.

- [x] T009 [P] [US1] Unit test for `wait_for_message` instant wakeup on publish and timeout expiration in `relayx-server/internal/mcp/tools_test.go`
- [x] T010 [US1] Implement `wait_for_message` tool with pre-check, subscriber channel wait, and timeout in `relayx-server/internal/mcp/tools.go`
- [x] T011 [US1] Implement stdio transport runner in `relayx-server/internal/mcp/stdio.go`
- [x] T012 [US1] Wire `--mcp-stdio` flag and broker lifecycle in `relayx-server/cmd/server/main.go`

---

## Phase 4: User Story 2 - One-Step OTP Extraction (Priority: P2)

**Goal**: Provide a specialized `get_otp` tool extracting numeric verification codes using linear-time RE2 regex.

**Independent Test**: Call `get_otp(sender="GOOGLE", timeout=5)` with an incoming message `"Your Google verification code is 492019"` and verify return value `otp: "492019"`.

- [x] T013 [P] [US2] Unit test for `get_otp` default regex extraction, custom regex group, and failure fallback in `relayx-server/internal/mcp/tools_test.go`
- [x] T014 [US2] Implement `get_otp` tool with safe RE2 regex compilation and structured token result in `relayx-server/internal/mcp/tools.go`

---

## Phase 5: User Story 3 - Historical Message Discovery & Search (Priority: P3)

**Goal**: Enable agents to query past messages via `get_latest_message`, `get_messages`, and `search_messages`.

**Independent Test**: Query stored messages with various limit, sender, and keyword filters, asserting matching subsets are returned without side effects.

- [x] T015 [P] [US3] Unit tests for `get_latest_message`, `get_messages`, and `search_messages` in `relayx-server/internal/mcp/tools_test.go`
- [x] T016 [US3] Implement `get_latest_message`, `get_messages`, and `search_messages` tool handlers in `relayx-server/internal/mcp/tools.go`

---

## Phase 6: User Story 4 - Independent Agent Authorization & Privacy Boundary (Priority: P4)

**Goal**: Secure MCP endpoints with dedicated agent authentication (`--mcp-token`) and zero-sensitive logging redaction.

**Independent Test**: Verify that HTTP/SSE requests without `--mcp-token` or using the Android device write token are rejected with 401 Unauthorized, and verify that server logs never leak raw message bodies or OTP values.

- [x] T017 [P] [US4] Implement HTTP/SSE transport (`/mcp/sse`, `/mcp/messages`) with Bearer token authentication in `relayx-server/internal/mcp/sse.go`
- [x] T018 [P] [US4] Add authentication middleware for MCP endpoints in `relayx-server/internal/api/middleware.go`
- [x] T019 [US4] Register `/mcp/sse` and `/mcp/messages` routes in `relayx-server/cmd/server/main.go` and `relayx-server/internal/api/server.go`
- [x] T020 [US4] Implement zero-sensitive redaction for MCP tool logging in `relayx-server/internal/mcp/server.go`
- [x] T021 [P] [US4] Unit test for MCP authentication enforcement (reject invalid token, reject device write token) in `relayx-server/internal/mcp/sse_test.go`

---

## Phase 7: Polish & Verification

**Purpose**: Cross-cutting integration tests, performance validation, and documentation.

- [x] T022 [P] End-to-end integration test verifying full stdio and SSE MCP lifecycles in `relayx-server/internal/mcp/mcp_test.go`
- [x] T023 Documentation updates in `relayx-server/README.md` and root `README.md`

---

## Phase 8: Security Remediation & Hardening (from Security Follow-Up)

**Purpose**: Remediate findings identified in `docs/security-reviews/2026-09-15-feature-006-server-mcp-followup.md`.

- [x] TASK-SEC-013 [HIGH] [A01:2025 / CWE-346, CWE-942] Remove wildcard CORS (`*`) from SSE transport, restrict Origin to localhost or configured allowed origins in `relayx-server/internal/mcp/sse.go`, and add regression tests in `relayx-server/internal/mcp/sse_test.go`
- [x] TASK-SEC-014 [HIGH] [A01:2025 / CWE-306] Auto-generate default MCP Agent Bearer token (`rx-mcp-...`) at startup if unset, print on console, and enforce token authentication on all network interfaces in `relayx-server/cmd/server/main.go` and `relayx-server/internal/mcp/sse.go`
- [x] TASK-SEC-015 [MEDIUM] [A04:2025 / CWE-662, CWE-674] Bind asynchronous tool execution context in `HandleMessages` to long-lived SSE session context (`session.done`) in `relayx-server/internal/mcp/sse.go` so blocking calls (`wait_for_message`, `get_otp`) survive HTTP 202 handler return
- [ ] TASK-SEC-016 [DEFERRED - Tech Debt] [LOW] [A07:2025 / CWE-598] Deprecate URL query parameter token authentication in favor of standard Bearer headers (Revisit trigger: Phase 8 TLS & Hardening)
- [ ] TASK-SEC-017 [DEFERRED - Tech Debt] [LOW] [A04:2025 / CWE-400] Enforce maximum active SSE sessions and worker concurrency limits (Revisit trigger: Phase 8 multi-client stress testing)

---

## Dependencies & Execution Order

### Phase Dependencies
- **Phase 1 (Setup)**: Can start immediately.
- **Phase 2 (Foundational)**: Depends on Phase 1 completion — BLOCKS all user stories.
- **Phase 3 (US1 - MVP)**: Depends on Phase 2 completion.
- **Phase 4 (US2)**: Depends on Phase 3 (extends `tools.go` with OTP extraction).
- **Phase 5 (US3)**: Depends on Phase 2 & 3 (extends `tools.go` with search/history).
- **Phase 6 (US4)**: Depends on Phase 3 (adds HTTP/SSE transport and token enforcement).
- **Phase 7 (Polish)**: Depends on all user stories completed.
- **Phase 8 (Security Remediation)**: Depends on Phase 6 and 7 completion (TASK-SEC-013, TASK-SEC-014, TASK-SEC-015).

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Complete Phase 1 (Setup: domain models, config).
2. Complete Phase 2 (Foundational: broker, JSON-RPC engine).
3. Complete Phase 3 (User Story 1: `wait_for_message` + stdio transport).
4. **VALIDATE MVP**: Verify local agent connection via `relayx-server --mcp-stdio`.

### Incremental Delivery
1. Add User Story 2 (`get_otp`) -> Test OTP regex parsing.
2. Add User Story 3 (`get_latest_message`, `get_messages`, `search_messages`) -> Test historical search.
3. Add User Story 4 (HTTP/SSE transport + `--mcp-token` dual-domain security).
4. Run integration suite and update documentation.
5. Execute Phase 8 security remediations (TASK-SEC-013, TASK-SEC-014, TASK-SEC-015) and verify with tests.
