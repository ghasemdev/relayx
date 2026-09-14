---
document_type: security-review
review_type: followup
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

# Security Follow-Up Plan: Model Context Protocol Server (`feature/006-server-mcp`)

## Executive Summary

This follow-up plan addresses the 5 security findings identified during the branch security review of `feature/006-server-mcp` ([`2026-09-15-feature-006-server-mcp.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/security-reviews/2026-09-15-feature-006-server-mcp.md)):
- **Immediate Remediation (3 items)**:
  - `TASK-SEC-013` (High): Restrict permissive CORS policy on `/mcp/sse` and validate Origin headers to eliminate browser drive-by exfiltration risks.
  - `TASK-SEC-014` (High): Auto-generate default MCP Agent Bearer token on startup and require authentication across all network interfaces, enforcing Constitution Principle II.
  - `TASK-SEC-015` (Medium): Decouple asynchronous tool execution context from ephemeral HTTP POST request context to prevent immediate premature cancellation of `wait_for_message` and `get_otp`.
- **Technical Debt Backlog (2 items)**:
  - `TASK-SEC-016` (Low): Deprecate URL query parameter token authentication in favor of standard Bearer headers.
  - `TASK-SEC-017` (Low): Implement active session bounds and request concurrency limits for SSE transport.

---

## Inputs Reviewed

- Security Review Report: [`docs/security-reviews/2026-09-15-feature-006-server-mcp.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/security-reviews/2026-09-15-feature-006-server-mcp.md)
- Feature Specification & Plan: [`specs/006-server-mcp/spec.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/specs/006-server-mcp/spec.md), [`plan.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/specs/006-server-mcp/plan.md), [`tasks.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/specs/006-server-mcp/tasks.md)
- Constitution & Security Governance: [`.specify/memory/constitution.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/.specify/memory/constitution.md)
- Memory Index: [`docs/memory/INDEX.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/memory/INDEX.md)

---

## Resolution Decisions

| Finding ID | Title | Severity | Decision | Rationale |
| :--- | :--- | :--- | :--- | :--- |
| **SEC-013** | Permissive Wildcard CORS on SSE Transport | High | **Implement now** | Direct threat of cross-origin browser drive-by exfiltration of SMS & 2FA OTP codes. Low fix complexity. |
| **SEC-014** | Unauthenticated MCP Agent Interface Exposure | High | **Implement now** | Direct violation of Constitution Principle II (Separate Security Domains & Protected Bindings). Prevents unauthenticated access on LAN/remote. |
| **SEC-015** | Context Cancellation of Asynchronous Tool Executions | Medium | **Implement now** | Core functional defect where event-driven waiting over SSE immediately fails with `context.Canceled`. |
| **SEC-016** | MCP Bearer Token in URL Query Parameters | Low | **Track as technical debt** | Standard browser `EventSource` lacks header support; token in query string provides compatibility while system is local-first. |
| **SEC-017** | Missing Session Concurrency Limits | Low | **Track as technical debt** | Current usage is single-user personal relay; resource exhaustion risk is low on localhost. |

---

## Backlog-Ready Tasks

| Task ID | Title | Severity | Type | Source Finding | Depends On | Acceptance Criteria |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TASK-SEC-013** | Remove wildcard CORS and restrict MCP endpoint origins | High | Implement | SEC-013 | None | Remove `Access-Control-Allow-Origin: *` from `HandleSSE`; validate `Origin` header; cross-origin browser requests blocked. |
| **TASK-SEC-014** | Auto-generate default MCP token & enforce auth on all interfaces | High | Implement | SEC-014 | None | Auto-generate `mcp-...` token if none provided, print at startup; reject unauthenticated requests on all interfaces unless `--insecure-mcp` passed. |
| **TASK-SEC-015** | Bind async MCP tool execution to SSE session lifecycle context | Medium | Implement | SEC-015 | None | Use `session.done` context or derived background context for tool dispatch in `HandleMessages` so `wait_for_message` survives POST completion. |
| **TASK-SEC-016** | Deprecate query parameter token auth and enforce Bearer headers | Low | Technical Debt | SEC-016 | TASK-SEC-014 | Standardize on `Authorization: Bearer` across client adapters; add deprecation warnings when `?token=` is supplied. |
| **TASK-SEC-017** | Enforce maximum active SSE sessions and worker concurrency | Low | Technical Debt | SEC-017 | TASK-SEC-013 | Reject new SSE connections when `maxSessions` (e.g. 10) is exceeded; limit concurrent goroutines per session. |

---

## Immediate Remediation Details

### TASK-SEC-013: Restrict CORS and Enforce Origin Validation on MCP SSE Transport
- **File**: [`relayx-server/internal/mcp/sse.go`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/sse.go)
- **Change**:
  1. In [`HandleSSE`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/sse.go#L111), remove `w.Header().Set("Access-Control-Allow-Origin", "*")`.
  2. Add origin checking logic: if an `Origin` header is present, verify that it matches localhost (`http://localhost:*`, `http://127.0.0.1:*`) or a configured allowed origin list. Disallow arbitrary external web domains.
- **Test**: Add unit tests in [`relayx-server/internal/mcp/sse_test.go`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/sse_test.go) verifying that cross-origin requests from `http://evil.com` are denied or omit permissive CORS headers.

### TASK-SEC-014: Auto-Generate Default MCP Token & Enforce Remote Authentication
- **Files**: [`relayx-server/cmd/server/main.go`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/cmd/server/main.go), [`relayx-server/internal/mcp/sse.go`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/sse.go)
- **Change**:
  1. In `main.go`, if `cfg.MCPToken == ""` and not running in pure stdio mode, generate a secure random token `mcpToken := "rx-mcp-" + uuid.NewString()`, assign to `cfg.MCPToken`, and print the token in the startup banner for the operator.
  2. In `sse.go`, update `Authenticate(r)` so that an empty token does NOT allow access by default unless explicitly permitted.
- **Test**: Add test in [`relayx-server/internal/config/config_test.go`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/config/config_test.go) and [`relayx-server/internal/mcp/sse_test.go`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/sse_test.go) asserting that requests without valid tokens are rejected with HTTP 401.

### TASK-SEC-015: Bind Async MCP Tool Execution Context to SSE Session Lifecycle
- **File**: [`relayx-server/internal/mcp/sse.go`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/sse.go)
- **Change**:
  In [`HandleMessages`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/sse.go#L175-L186), do not pass `r.Context()` into `h.server.HandleRequest`. Instead, derive a context from `context.Background()` bounded by the session's termination (`session.done`) or use a session-scoped context that lives until client disconnects or tool times out.
- **Test**: Add test in [`relayx-server/internal/mcp/sse_test.go`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/sse_test.go) invoking `wait_for_message` over SSE and asserting it does NOT immediately abort with `context.Canceled` upon POST response completion.

---

## Technical Debt Backlog

### TASK-SEC-016: Deprecate Query Parameter Token Authentication
- **Safe to Defer**: Browser EventSource historically lacked HTTP header support. With localhost execution and origin restrictions (TASK-SEC-013), exposure is mitigated.
- **Remaining Risk**: Token could be logged in proxy access logs or browser history if accessed via browser address bar.
- **Revisit Trigger**: Phase 8 (TLS & Hardening) or when browser SSE adapters transition to header-supporting fetch-based SSE streams.

### TASK-SEC-017: SSE Active Session and Worker Concurrency Limits
- **Safe to Defer**: RelayX is designed as a personal single-user gateway. Server runs locally or within trusted environments.
- **Remaining Risk**: Intentional flood of connections could exhaust server memory.
- **Revisit Trigger**: Phase 8 multi-client stress testing or public LAN multi-device deployments.

---

## Confirmed Secure Patterns

1. **SQL Injection Prevention**: Parameterized queries throughout [`relayx-server/internal/storage/message_repo.go`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/storage/message_repo.go).
2. **ReDoS Immunity**: Linear-time RE2 regex engine in [`relayx-server/internal/mcp/tools.go`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/tools.go).
3. **Non-Blocking Ingestion**: Channels prevent slow subscribers from stalling message ingestion in [`broker.go`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/mcp/broker.go).
4. **Log Redaction**: Automatic scrubbing of sensitive keys in [`logger.go`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-server/internal/logging/logger.go).

---

## Memory Hub INDEX.md Row

```text
| [2026-09-15-feature-006-server-mcp-followup.md](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/security-reviews/2026-09-15-feature-006-server-mcp-followup.md) | followup | 2026-09-15 | HIGH | C:0 H:2 M:1 L:2 | A01,A04,A07 |
```
