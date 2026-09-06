---
document_type: security-review
review_type: followup
assessment_date: 2026-09-07
codebase_analyzed: relayx
total_files_analyzed: 30
total_findings: 3
overall_risk: MODERATE
critical_count: 0
high_count: 0
medium_count: 1
low_count: 2
informational_count: 0
owasp_categories: [A04:2025, A05:2025, A07:2025]
cwe_ids: [CWE-400, CWE-525, CWE-307]
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

# Security Follow-Up Plan: Server Foundation (`feature/001-server-foundation`)

## Executive Summary

This follow-up plan resolves the 3 security findings discovered during the branch review of `feature/001-server-foundation`:
- **Immediate Remediation (2 items)**:
  - `TASK-SEC-001` (Medium): Bound HTTP request body reader with `http.MaxBytesReader` (1 MB limit) to prevent memory exhaustion DoS.
  - `TASK-SEC-002` (Low): Add HTTP security headers middleware (`Cache-Control: no-store`, `X-Content-Type-Options: nosniff`) to safeguard private SMS & OTP responses.
- **Technical Debt Backlog (1 item)**:
  - `TASK-SEC-003` (Low): Rate-limiting for gateway ingestion endpoints. Safe to defer during Phase 1 localhost operation; targeted for Phase 4 / remote LAN pairing.

---

## Inputs Reviewed

- Security Review Report: [`docs/security-reviews/2026-09-07-feature-001-server-foundation.md`](2026-09-07-feature-001-server-foundation.md)
- Constitution & Security Guidelines: [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md)
- Feature Specification & Architecture: [`specs/001-server-foundation/spec.md`](../../specs/001-server-foundation/spec.md), [`plan.md`](../../specs/001-server-foundation/plan.md)
- Memory Index: [`docs/memory/INDEX.md`](../INDEX.md)

---

## Resolution Decisions

| Finding ID | Title | Severity | Decision | Rationale |
| :--- | :--- | :--- | :--- | :--- |
| **SEC-001** | Unbounded HTTP Request Body Parsing | Medium | **Implement now** | Low complexity, directly mitigates Denial of Service / OOM vulnerability on write endpoints. |
| **SEC-002** | Missing Cache-Control & Anti-Sniffing Headers | Low | **Implement now** | Minimal overhead, ensures compliance with Principle III (Data Minimization & Privacy). |
| **SEC-003** | Lack of Ingestion Rate Limiting | Low | **Track as technical debt** | Server binds to `127.0.0.1:8080` by default. Rate limiting will be implemented when remote / LAN binding features are introduced in Phase 4. |

---

## Backlog-Ready Tasks

| Task ID | Title | Severity | Type | Source Finding | Depends On | Acceptance Criteria |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TASK-SEC-001** | Enforce 1 MB request body limit on `POST /api/v1/messages` | Medium | Implement | SEC-001 | None | Requests exceeding 1 MB return HTTP 413 or 400 without crashing server; unit test asserts boundary. |
| **TASK-SEC-002** | Add security & anti-caching headers middleware | Low | Implement | SEC-002 | None | Responses to `/api/v1/*` include `Cache-Control: no-store, no-cache, must-revalidate` and `X-Content-Type-Options: nosniff`. |
| **TASK-SEC-003** | Token-bucket rate limiting for gateway authentication | Low | Technical Debt | SEC-003 | TASK-SEC-001 | Rate-limiting middleware active when `--host` is non-localhost; revisit trigger: Phase 4 remote LAN pairing. |

---

## Immediate Remediation Details

### TASK-SEC-001: Enforce 1 MB Request Body Limit
- **File**: `relayx-server/internal/api/messages.go`
- **Change**: In `IngestMessage`, wrap `r.Body = http.MaxBytesReader(w, r.Body, 1048576)` before calling `json.NewDecoder`.
- **Test**: Add test sending payload > 1 MB in `relayx-server/internal/api/messages_test.go` asserting clean rejection.

### TASK-SEC-002: Security & Anti-Caching Headers Middleware
- **File**: `relayx-server/internal/api/middleware.go`
- **Change**: Add `SecurityHeaders(next http.Handler) http.Handler` setting `X-Content-Type-Options: nosniff` and `Cache-Control: no-store, no-cache, must-revalidate`.
- **Test**: Assert headers present in `relayx-server/internal/api/middleware_test.go`.

---

## Technical Debt Backlog

### TASK-SEC-003: Rate Limiting on Ingestion & Auth
- **Safe to Defer**: RelayX defaults to binding to `127.0.0.1:8080`. External network traffic cannot reach the port unless explicitly exposed.
- **Remaining Risk**: If an operator manually runs `--host 0.0.0.0` without a reverse proxy, unauthenticated clients on LAN could attempt rapid token guessing.
- **Revisit Trigger**: Implementation of LAN configuration or remote phone pairing (Roadmap Phase 4).

---

## Confirmed Secure Patterns

1. **Pure Parameterized SQL**: `modernc.org/sqlite` with zero string concatenation.
2. **Device Write Domain Isolation**: `Bearer <token>` verified via SHA-256 hash lookup (`devices.token_hash`) and restricted to `POST /api/v1/messages`.
3. **Structured Log Masking**: `RedactingHandler` systematically suppresses SMS body and OTP tokens from standard and debug loggers.
4. **Graceful Panic Recovery**: `api.Recovery` prevents process crashes and stops internal stack leaks.
