# Implementation Plan: Server Web Dashboard & Live Observability

**Branch**: `feature/004-server-web-dashboard` | **Date**: 2026-09-13 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/004-server-web-dashboard/spec.md`

---

## Summary

Expand `relayx-server` with an embedded, zero-dependency Web Dashboard and real-time observability suite:
1. **Live Log Streaming ("Web Logcat")**: Stream structured `slog` server events in real time over Server-Sent Events (SSE) with level filtering, keyword search, auto-scroll, and guaranteed payload/OTP redaction (Constitution Principle III).
2. **SQLite Database Table Browser**: Provide an interactive browser for `messages`, `devices`, and `schema_migrations` with pagination, sorting, status/sender filters, and default-masked payload viewing.
3. **Throughput Metrics & Health**: Visual metrics for received, forwarded, filtered, and failed messages, alongside server uptime, memory allocation, and SQLite WAL status.
4. **Device Management & Security View**: View authorized device credentials, inspect token hashes, issue new tokens, and revoke existing devices.
5. **Zero-Infrastructure Embedded Assets**: Embed all HTML5, CSS, and vanilla ES6 JavaScript assets inside the Go executable using `go:embed` without node/npm or external CDN dependencies.

---

## Technical Context

**Language/Version**: Go 1.24+ (Standard Library `net/http`, `embed`, `log/slog`, `sync`)

**Primary Dependencies**:
- Web & Server: Go standard library (`net/http`, `embed`)
- Logging: Go standard library `log/slog` + existing `RedactingHandler`
- Storage: `modernc.org/sqlite` (Pure-Go SQLite driver, WAL mode)
- Frontend: Vanilla HTML5, CSS custom properties, modern ES6 JavaScript (`fetch`, `EventSource`, DOM APIs)

**Storage**: Local embedded SQLite database (`./data/sms.db`, tables: `messages`, `devices`, `schema_migrations`).

**Testing**: Go standard testing package (`testing`, `httptest`), `rtk go test ./...`.

**Target Platform**: Single self-contained static binary cross-compiling to Linux (AMD64/ARM64) and macOS (ARM64/AMD64).

**Project Type**: Web Service & Server Daemon (`relayx-server`).

**Performance Goals**:
- Web Dashboard loads in browser in < 200ms from cold start.
- Real-time SSE log latency < 50ms from `slog` event emission to browser rendering.
- Database table queries return paginated rows in < 100ms for up to 50,000 records.

**Constraints**:
- Single-binary distribution with `go:embed` (zero external frontend build step or runtime container).
- Zero remote CDN dependencies (100% air-gapped/offline operable).
- Strict privacy: zero unmasked message bodies or OTP codes streamed or displayed by default.
- Non-blocking log broadcast to prevent slow browser consumers from degrading server throughput.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evaluation & Mitigation |
|:---|:---|:---|
| **P-01: Local-First & Zero-Infrastructure** | **PASSED** | Dashboard is embedded via `go:embed` within the single Go binary. Zero Node/npm, Docker, or external web server dependencies. Zero remote CDNs. |
| **P-02: Separate Security Domains** | **PASSED** | Admin dashboard access is decoupled from device write tokens. Optional `--admin-token` protects administrative operations. |
| **P-03: Strict Data Minimization & Privacy** | **PASSED** | SSE log stream hooks downstream of `RedactingHandler`. SQLite browser masks message bodies by default with explicit eye toggle to reveal. |
| **P-04: Device-Side Pre-Filtering** | **PASSED** | Visualizes filtered messages in table browser and overview throughput metrics. |
| **P-05: Durable Delivery & Idempotency** | **PASSED** | Read-only inspection does not alter message UUIDs or state. |
| **P-07: Developer Ergonomics & Zero-Friction Setup** | **PASSED** | Navigating to `http://127.0.0.1:8080/dashboard/` immediately opens the rich UI without any secondary processes. |
| **P-08: Clean Architecture & Boring Tech** | **PASSED** | Native Go `net/http` handlers, vanilla HTML/CSS/JS frontend, standard SSE `EventSource`. |

---

## Project Structure

### Documentation (this feature)

```text
specs/004-server-web-dashboard/
├── spec.md              # Feature specification
├── plan.md              # This file (implementation plan)
├── research.md          # Phase 0 technical research & decisions
├── data-model.md        # Phase 1 data entities and validation rules
├── quickstart.md        # Phase 1 setup and manual verification guide
├── contracts/
│   └── dashboard-api.md # REST and SSE endpoint contract
└── checklists/
    └── requirements.md  # Specification quality checklist
```

### Source Code (`relayx-server`)

```text
relayx-server/
├── cmd/server/
│   └── main.go                         # CLI flags (--admin-token) & dashboard route wiring
├── internal/
│   ├── api/
│   │   ├── dashboard_handler.go        # HTTP & SSE handlers for dashboard
│   │   ├── dashboard_handler_test.go   # Unit tests for dashboard API & SSE stream
│   │   ├── middleware.go               # Admin auth middleware & security headers
│   │   └── server.go                   # Route registration (/dashboard/, /api/v1/dashboard/*)
│   ├── domain/
│   │   └── dashboard.go                # Domain types (SystemMetrics, LogEntry, TablePage)
│   ├── logging/
│   │   ├── broadcaster.go              # Thread-safe fan-out log broadcaster with ring buffer
│   │   ├── broadcaster_test.go         # Tests for log broadcast and non-blocking backpressure
│   │   └── logger.go                   # Hook broadcaster into RedactingHandler
│   ├── service/
│   │   ├── dashboard_service.go        # Metrics calculation, table schema inspection, paging
│   │   └── dashboard_service_test.go   # Unit tests for dashboard service
│   ├── storage/
│   │   └── table_browser.go            # Read-only parameterized table queries & disk stats
│   └── web/
│       ├── embed.go                    # //go:embed static/* filesystem declaration
│       └── static/
│           ├── index.html              # Single-page dashboard application
│           ├── style.css               # Modern dark/light responsive layout
│           └── app.js                  # Vanilla JS (SSE client, table pager, metrics)
```

**Structure Decision**: Standard Go package organization following existing `relayx-server` architecture. Web assets are isolated in `internal/web/static/` and embedded via `embed.FS`.

---

## Complexity Tracking

*No violations. Clean architecture using standard Go library capabilities.*
