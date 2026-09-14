---
document_type: security-review
review_type: branch
assessment_date: 2026-09-15
codebase_analyzed: relayx
total_files_analyzed: 33
total_findings: 5
overall_risk: HIGH
critical_count: 0
high_count: 2
medium_count: 1
low_count: 2
informational_count: 0
owasp_categories: [A01:2025, A04:2025, A07:2025]
cwe_ids: [CWE-306, CWE-346, CWE-400, CWE-598, CWE-662, CWE-942]
field_summaries:
  document_type: "Always 'security-review'. Allows indexers to skip non-review documents."
  review_type: "Which command generated this document: audit, branch, staged, plan, tasks, or followup."
  assessment_date: "ISO 8601 date the review was performed (YYYY-MM-DD)."
  overall_risk: "Highest severity tier with active findings (CRITICAL, HIGH, MODERATE, LOW, INFORMATIONAL)."
  critical_count: "Number of Critical findings (CVSS 9.0-10.0)."
  high_count: "Number of High findings (CVSS 7.0-8.9)."
  medium_count: "Number of Medium findings (CVSS 4.0-6.9)."
  low_count: "Number of Low findings (CVSS 0.1-3.9)."
  informational_count: "Number of Informational findings."
  owasp_categories: "OWASP Top 10 2025 categories (A01-A10) that have at least one finding."
  cwe_ids: "CWE identifiers referenced in this document."
  finding_id: "Unique finding identifier (SEC-NNN) for cross-referencing and task linkage."
  location: "File path and line number of the vulnerable code (path/to/file.ext:line)."
  owasp_category: "OWASP Top 10 2025 category for this finding (AXX:2025-Name)."
  cwe: "Common Weakness Enumeration identifier with short name (CWE-NNN: Name)."
  cvss_score: "CVSS v3.1 base score (0.0-10.0). 9.0+=Critical, 7.0-8.9=High, 4.0-6.9=Medium, 0.1-3.9=Low."
  spec_kit_task: "Spec-Kit task ID for backlog tracking and remediation follow-up (TASK-SEC-NNN)."
---

# SECURITY REVIEW REPORT — BRANCH: feature/006-server-mcp vs develop

## Executive Summary
A comprehensive security review of branch `feature/006-server-mcp` relative to `develop` was performed against the project constitution ([`constitution.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/.specify/memory/constitution.md)), durable memory guidelines, and OWASP Top 10 2025 standards. The scope covers 33 files including the embedded Model Context Protocol (MCP) server, event broker, SSE/stdio transports, tool definitions, configuration options, and supporting Android UI/stats refinements.

Overall risk is assessed as **HIGH** due to two high-severity access control and transport vulnerabilities:
1. Permissive wildcard CORS (`Access-Control-Allow-Origin: *`) on `/mcp/sse` combined with unauthenticated defaults allowing browser-based drive-by exfiltration of SMS messages and OTPs.
2. Missing authentication enforcement for the MCP interface when the server binds to non-localhost (`0.0.0.0` or LAN), violating Constitution Principle II.

Additionally, one Medium reliability/concurrency flaw (short-lived context cancellation in asynchronous SSE requests aborting blocking tool calls) and two Low-severity hygiene items were identified.

---

## Branch Diff Reviewed
- **Target Branch**: `feature/006-server-mcp`
- **Base Branch**: `develop` (merge base commit `2a7b699`)
- **Primary Source Files**:
  - `relayx-server/cmd/server/main.go`
  - `relayx-server/internal/config/config.go`
  - `relayx-server/internal/config/config_test.go`
  - `relayx-server/internal/domain/mcp.go`
  - `relayx-server/internal/domain/message.go`
  - `relayx-server/internal/logging/logger.go`
  - `relayx-server/internal/mcp/broker.go`
  - `relayx-server/internal/mcp/broker_test.go`
  - `relayx-server/internal/mcp/mcp_test.go`
  - `relayx-server/internal/mcp/protocol.go`
  - `relayx-server/internal/mcp/server.go`
  - `relayx-server/internal/mcp/sse.go`
  - `relayx-server/internal/mcp/sse_test.go`
  - `relayx-server/internal/mcp/stdio.go`
  - `relayx-server/internal/mcp/tools.go`
  - `relayx-server/internal/mcp/tools_test.go`
  - `relayx-server/internal/service/message_service.go`
  - `relayx-server/internal/storage/message_repo.go`
  - `relayx-android/src/main/java/com/parsomash/relayx/domain/usecase/DashboardUseCases.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/ui/rules/RuleEditScreen.kt`
  - `relayx-android/src/test/java/com/parsomash/relayx/viewmodel/DashboardViewModelTest.kt`

---

## Vulnerability Findings

### [HIGH] SEC-013: Cross-Origin Drive-By Message & OTP Exfiltration via Permissive CORS on SSE Transport
- **Finding ID**: SEC-013
- **Location**: [`relayx-server/internal/mcp/sse.go:111`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/sse.go#L111)
- **OWASP Category**: A01:2025 - Broken Access Control / A05:2025 - Security Misconfiguration
- **CWE**: CWE-346 (Origin Validation Error), CWE-942 (Permissive Cross-Domain Policy with Untrusted Domains)
- **CVSS Score**: 8.2 (High) `CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:H/I:L/A:N`
- **Description**: The SSE endpoint explicitly sends `w.Header().Set("Access-Control-Allow-Origin", "*")` on `GET /mcp/sse`. When RelayX server runs with the default configuration without `--mcp-token`, `Authenticate` permits unauthenticated access. An attacker hosting a malicious webpage visited by the user in any standard browser can open a cross-origin `EventSource("http://127.0.0.1:8080/mcp/sse")`, obtain the active `sessionId` from the `endpoint` event, post JSON-RPC commands (such as `wait_for_message` or `get_otp`) to `/mcp/messages`, and receive streamed responses over the SSE channel. This enables arbitrary cross-origin exfiltration of incoming SMS messages and two-factor authentication OTPs without user interaction or approval.
- **Remediation**:
  1. Remove the unconditional `Access-Control-Allow-Origin: *` header from `HandleSSE`.
  2. If web-based MCP inspection tools are required, validate the incoming `Origin` header against an explicit allowlist or restrict access to same-origin / localhost.
  3. Enforce token authentication by default so unauthenticated connections are denied immediately.
- **Spec-Kit Task**: TASK-SEC-013

---

### [HIGH] SEC-014: Unauthenticated MCP Agent Interface Exposure on Non-Localhost / LAN Bindings
- **Location**: [`relayx-server/internal/mcp/sse.go:63-65`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/sse.go#L63-L65) & [`relayx-server/cmd/server/main.go:131`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/cmd/server/main.go#L131)
- **Finding ID**: SEC-014
- **OWASP Category**: A01:2025 - Broken Access Control
- **CWE**: CWE-306 (Missing Authentication for Critical Function)
- **CVSS Score**: 7.5 (High) `CVSS:3.1/AV:A/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N`
- **Description**: Constitution Principle II.2 explicitly specifies: *"Default Binding: The server must default to localhost (127.0.0.1:8080). Remote binding (--host 0.0.0.0 or --lan) must explicitly require authentication."* While device write operations automatically generate and enforce a device token if none is specified, `MCPToken` defaults to empty string `""`. In `sse.go`, `Authenticate` treats an empty `mcpToken` as a bypass: `if h.mcpToken == "" { return true }`. If a user runs RelayX on `--host 0.0.0.0` or `--lan`, any host on the local network or Wi-Fi can connect to `/mcp/sse` and `/mcp/messages` with zero authentication, granting full read access to all stored messages and incoming OTPs.
- **Remediation**:
  1. Auto-generate a secure random MCP Agent Bearer token at server startup if `cfg.MCPToken` is empty (mirroring the device token initialization pattern in `main.go`), and display it prominently on the console.
  2. Refuse server startup or refuse remote MCP connections if remote binding is enabled without an explicit or auto-generated token.
  3. Update `Authenticate` to deny access when `mcpToken == ""` unless an explicit `--insecure-mcp` development flag is deliberately passed.
- **Spec-Kit Task**: TASK-SEC-014

---

### [MEDIUM] SEC-015: Immediate Context Cancellation of Asynchronous Tool Calls in SSE Message Handler
- **Location**: [`relayx-server/internal/mcp/sse.go:176`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/sse.go#L176)
- **Finding ID**: SEC-015
- **OWASP Category**: A04:2025 - Insecure Design
- **CWE**: CWE-662 (Improper Synchronization) / CWE-674 (Uncontrolled Recursion / Context Cancellation)
- **CVSS Score**: 5.3 (Medium) `CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:L`
- **Description**: In `HandleMessages`, the incoming HTTP POST request context `r.Context()` is passed directly to the asynchronous goroutine: `go func(rpcReq *domain.RPCRequest) { resp := h.server.HandleRequest(r.Context(), rpcReq) ... }`. Immediately after spawning the goroutine, `HandleMessages` executes `w.WriteHeader(http.StatusAccepted)` and returns. In Go's `net/http` implementation, returning from a handler automatically cancels `r.Context()`. Consequently, blocking MCP tools (`wait_for_message` and `get_otp`) immediately encounter `<-ctx.Done()` and abort with `context.Canceled`, preventing event-driven message waiting from operating over HTTP/SSE.
- **Remediation**:
  Bind asynchronous MCP tool execution context to the lifecycle of the `SSESession` (e.g. `session.done`) rather than the ephemeral POST request `r.Context()`. Allow the tool's internal timeout timer to govern operation cancellation.
- **Spec-Kit Task**: TASK-SEC-015

---

### [LOW] SEC-016: Sensitive MCP Bearer Token Acceptance in URL Query Parameters
- **Location**: [`relayx-server/internal/mcp/sse.go:72`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/sse.go#L72)
- **Finding ID**: SEC-016
- **OWASP Category**: A07:2025 - Identification and Authentication Failures
- **CWE**: CWE-598 (Use of GET Request with Sensitive Data in Query String)
- **CVSS Score**: 3.7 (Low) `CVSS:3.1/AV:N/AC:H/PR:N/UI:R/S:U/C:L/I:N/A:N`
- **Description**: `Authenticate` accepts authentication credentials via `r.URL.Query().Get("token")`. Sensitive tokens in URL query strings are captured in reverse proxy access logs, browser history, intermediary caches, and `Referer` headers, increasing exposure risks.
- **Remediation**:
  Mandate standard `Authorization: Bearer <token>` headers for all MCP endpoints. If query parameter support must be retained for constrained EventSource browser environments, ensure query parameters are scrubbed from access logs and document security limitations.
- **Spec-Kit Task**: TASK-SEC-016

---

### [LOW] SEC-017: Unbounded Goroutine Allocation and Missing Session Concurrency Limits on SSE Endpoints
- **Location**: [`relayx-server/internal/mcp/sse.go:97, 175`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/sse.go#L97)
- **Finding ID**: SEC-017
- **OWASP Category**: A04:2025 - Insecure Design
- **CWE**: CWE-400 (Uncontrolled Resource Consumption) / CWE-770 (Allocation of Resources Without Limits or Throttling)
- **CVSS Score**: 3.3 (Low) `CVSS:3.1/AV:L/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:L`
- **Description**: `HandleSSE` creates new sessions without enforcing a maximum active session threshold. In addition, `HandleMessages` spawns an asynchronous goroutine for every POST request without rate-limiting or worker pool bounds. A flood of SSE connections or POST messages could exhaust memory and goroutine resources.
- **Remediation**:
  Enforce a configurable `maxActiveSessions` limit (e.g., 10 concurrent sessions) and bound concurrent request execution per session.
- **Spec-Kit Task**: TASK-SEC-017

---

## Confirmed Secure Patterns

1. **Strict Parameterized Queries for Message Filtering**:
   In [`relayx-server/internal/storage/message_repo.go:121-136`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/storage/message_repo.go#L121-L136), all query conditions (`device_id = ?`, `sender = ?`, `body LIKE ? OR sender LIKE ?`) use standard SQL placeholders (`?`), completely preventing SQL injection.
2. **ReDoS Immunity in Regex Evaluation**:
   In [`relayx-server/internal/mcp/tools.go:220`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/tools.go#L220), OTP extraction uses Go's standard `regexp` library (RE2 engine with linear runtime guarantee), eliminating catastrophic backtracking vulnerabilities.
3. **Channel-Based Event-Driven Pub/Sub (Constitution Principle VI Compliance)**:
   [`relayx-server/internal/mcp/broker.go:110-114`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/broker.go#L110-L114) delivers messages to subscribers using non-blocking channel sends (`select { case sub.ch <- msg: default: }`), ensuring that waiting MCP clients never block or slow down the message ingestion pipeline in `MessageService.Ingest`.
4. **Subscription Cleanup & Leak Prevention**:
   In [`relayx-server/internal/mcp/tools.go:183-186`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/tools.go#L183-L186), `defer m.broker.Unsubscribe(sub)` and `defer timer.Stop()` guarantee that subscription channels and timers are released upon message receipt, timeout, or cancellation.
5. **Separation of Stdio vs Logging Channels**:
   In [`relayx-server/cmd/server/main.go:34-36`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/cmd/server/main.go#L34-L36), when `cfg.MCPStdio` is enabled, structured server logs are directed to `os.Stderr` via `logging.InitLoggerTo(os.Stderr, cfg.Debug)`, preventing diagnostic log entries from corrupting JSON-RPC communication on `os.Stdout`.
6. **Data Minimization & Redaction in Structured Logging**:
   The `RedactingHandler` systematically redacts sensitive keys (`body`, `otp`, `code`, `token`, `authorization`) across all emitted server logs.

---

## Prioritized Action Plan

| Priority | Finding ID | Remediation Summary | Target File |
|:---|:---|:---|:---|
| **P1** | **SEC-013** | Remove wildcard CORS (`*`) from SSE, validate origins, and require authentication. | [`relayx-server/internal/mcp/sse.go`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/sse.go) |
| **P1** | **SEC-014** | Auto-generate default MCP token on startup and require authentication for remote/LAN bindings. | [`relayx-server/cmd/server/main.go`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/cmd/server/main.go), [`relayx-server/internal/mcp/sse.go`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/sse.go) |
| **P2** | **SEC-015** | Decouple async MCP tool execution context from ephemeral POST request context to prevent immediate `context.Canceled`. | [`relayx-server/internal/mcp/sse.go`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/sse.go) |
| **P3** | **SEC-016** | Mandate Bearer header authentication and deprecate query string tokens. | [`relayx-server/internal/mcp/sse.go`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/sse.go) |
| **P3** | **SEC-017** | Implement maximum active session limit and request concurrency bounds in SSE handler. | [`relayx-server/internal/mcp/sse.go`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/sse.go) |

---

## Memory Hub INDEX.md Row

```text
| [2026-09-15-feature-006-server-mcp.md](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/security-reviews/2026-09-15-feature-006-server-mcp.md) | branch | 2026-09-15 | HIGH | C:0 H:2 M:1 L:2 | A01,A04,A07 |
```
