---
document_type: security-review
review_type: branch
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

# SECURITY REVIEW REPORT — BRANCH: feature/001-server-foundation vs develop

## Executive Summary
A comprehensive security review of branch `feature/001-server-foundation` was performed against the project constitution (`.specify/memory/constitution.md`) and OWASP Top 10 2025 standards. The review analyzed 30 modified files comprising the new standalone Go server module (`relayx-server/`), storage engine, HTTP API layer, and authentication middleware.

The architecture demonstrates strong adherence to core security principles:
- **Zero SQL Injection**: 100% parameterized queries via `modernc.org/sqlite`.
- **Domain Separation**: Device write authentication is isolated to `POST /api/v1/messages` and stored as SHA-256 hashes.
- **Privacy Preservation**: SMS message bodies and OTP tokens are systematically redacted from structured logs.
- **Idempotency**: Strict SQLite `UNIQUE(device_id, message_id)` guarantees duplicate suppression.

Three remediable findings were identified: one **Medium** (unbounded HTTP request body reader) and two **Low** (missing HTTP security/cache headers, lack of rate limiting).

---

## Branch Diff Reviewed
- **Target**: `feature/001-server-foundation`
- **Base**: `develop`
- **Primary Source Files**:
  - `relayx-server/cmd/server/main.go`
  - `relayx-server/internal/api/server.go`
  - `relayx-server/internal/api/middleware.go`
  - `relayx-server/internal/api/messages.go`
  - `relayx-server/internal/api/health.go`
  - `relayx-server/internal/domain/device.go`
  - `relayx-server/internal/domain/message.go`
  - `relayx-server/internal/service/device_service.go`
  - `relayx-server/internal/service/message_service.go`
  - `relayx-server/internal/storage/sqlite.go`
  - `relayx-server/internal/storage/migrator.go`
  - `relayx-server/internal/storage/device_repo.go`
  - `relayx-server/internal/storage/message_repo.go`
  - `relayx-server/internal/logging/logger.go`

---

## Vulnerability Findings

### [MEDIUM] SEC-001: Unbounded HTTP Request Body Parsing (Denial of Service)
- **Finding ID**: SEC-001
- **Location**: `relayx-server/internal/api/messages.go:30`
- **OWASP Category**: A05:2025 - Security Misconfiguration / A04:2025 - Insecure Design
- **CWE**: CWE-400 (Uncontrolled Resource Consumption) / CWE-770 (Allocation of Resources Without Limits or Throttling)
- **CVSS Score**: 5.3 (Medium) `CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:L`
- **Description**: `r.Body` is decoded directly via `json.NewDecoder(r.Body).Decode(&input)` without wrapping with `http.MaxBytesReader`. A malicious client or malfunctioning gateway could stream an unbounded payload (e.g., hundreds of megabytes), causing server memory exhaustion and OOM termination.
- **Remediation**: Wrap `r.Body` with `http.MaxBytesReader(w, r.Body, 1048576)` (1 MB limit) prior to decoding JSON in `IngestMessage`.
- **Spec-Kit Task**: TASK-SEC-001

---

### [LOW] SEC-002: Missing Security and Cache-Control Headers on Message Endpoints
- **Finding ID**: SEC-002
- **Location**: `relayx-server/internal/api/middleware.go:88`
- **OWASP Category**: A05:2025 - Security Misconfiguration
- **CWE**: CWE-525 (Use of Web Browser Cache Containing Sensitive Information)
- **CVSS Score**: 3.1 (Low) `CVSS:3.1/AV:N/AC:H/PR:N/UI:R/S:U/C:L/I:N/A:N`
- **Description**: HTTP responses returning SMS messages and health status do not emit protective HTTP security headers such as `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`. Intermediary proxies or browser caches could store confidential OTP verification messages.
- **Remediation**: Add a security headers middleware that attaches `Cache-Control: no-store, no-cache, must-revalidate` and `X-Content-Type-Options: nosniff` to all `/api/v1/` responses.
- **Spec-Kit Task**: TASK-SEC-002

---

### [LOW] SEC-003: Absence of Ingestion Rate Limiting
- **Finding ID**: SEC-003
- **Location**: `relayx-server/internal/api/middleware.go:30`
- **OWASP Category**: A07:2025 - Identification and Authentication Failures
- **CWE**: CWE-307 (Improper Restriction of Excessive Authentication Attempts)
- **CVSS Score**: 3.7 (Low) `CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:L/I:N/A:N`
- **Description**: The server currently processes requests without rate limiting. If the server is configured to bind to a non-localhost interface (e.g. LAN Wi-Fi `--host 0.0.0.0`), an unauthenticated party could execute brute-force token guessing or overwhelm the SQLite write queue.
- **Remediation**: Introduce a lightweight token-bucket rate limiter middleware on `/api/v1/messages` (e.g. 30 requests/minute per IP) to deter flood attacks.
- **Spec-Kit Task**: TASK-SEC-003

---

## Confirmed Secure Patterns

1. **SQL Injection Defense**: All database operations in `internal/storage/` use parameterized SQL queries (`?`) with `database/sql` and `modernc.org/sqlite`. Zero dynamic query concatenation.
2. **Credential Isolation**: Gateway Bearer tokens are never stored in plaintext; only SHA-256 digests (`devices.token_hash`) are persisted and queried.
3. **Log Redaction Engine**: `internal/logging/logger.go` wraps `slog` with `RedactingHandler`, masking `body`, `otp`, `code`, `token`, and `authorization` keys. Automated test coverage (`logger_test.go`) enforces zero leakage.
4. **Strict Idempotency**: `messages` table enforces `UNIQUE(device_id, message_id)` and SQLite `ON CONFLICT DO NOTHING`. Retried submissions return HTTP 200 with identical IDs and create no duplicate records.
5. **Panic Recovery**: `api.Recovery` catches panics in any route and returns clean HTTP 500 JSON without exposing stack traces.

---

## Prioritized Action Plan

| Priority | Task ID | Description | Complexity |
| :--- | :--- | :--- | :--- |
| **P1** | `TASK-SEC-001` | Add `http.MaxBytesReader(w, r.Body, 1<<20)` in `IngestMessage` handler | Low (5 lines) |
| **P2** | `TASK-SEC-002` | Add security headers middleware (`Cache-Control: no-store`, `X-Content-Type-Options: nosniff`) | Low (10 lines) |
| **P3** | `TASK-SEC-003` | Add rate limiter middleware for LAN exposures | Medium |
