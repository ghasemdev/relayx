# Implementation Plan: Server Foundation

**Branch**: `feature/001-server-foundation` | **Date**: 2026-09-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-server-foundation/spec.md`

---

## Summary

Build the foundational, standalone Go server (`relayx-server`) that provides zero-infrastructure startup, an embedded SQLite storage engine with `go:embed` migrations, an authenticated versioned HTTP REST API (`/api/v1`), and privacy-preserving logging. The server serves as the durable ingestion hub for mobile SMS messages while maintaining strict separation of security domains.

---

## Technical Context

**Language/Version**: Go 1.22+ (verified installed: Go 1.27.1 darwin/arm64)

**Primary Dependencies**:
- Standard library: `net/http` (enhanced pattern-matching router in Go 1.22+), `log/slog`, `embed`, `database/sql`
- Pure-Go SQLite Driver: `modernc.org/sqlite` (enables CGO-free static cross-compilation)
- UUID Generation: `github.com/google/uuid`

**Storage**: Embedded SQLite database (`./data/sms.db`) with embedded SQL migrations (`migrations/*.sql`)

**Testing**: Standard library `testing` package with `httptest` for REST handlers and temporary in-memory/file-backed SQLite databases for repository integration tests (`go test -v ./...`)

**Target Platforms**:
- Linux AMD64 (`sms-server-linux-amd64`)
- Linux ARM64 (`sms-server-linux-arm64`)
- macOS ARM64 (`sms-server-darwin-arm64`)
- macOS AMD64 (`sms-server-darwin-amd64`)

**Project Type**: Standalone CLI daemon / HTTP Web Service

**Performance Goals**:
- Startup & migration completion in < 1.0s
- Ingestion latency < 20ms at p95
- Sustained throughput > 100 concurrent requests without database locks (using WAL mode)

**Constraints**:
- Single self-contained binary distribution (`CGO_ENABLED=0`)
- Strictly NO external database server, Docker, JVM, Node, or Python runtime dependencies
- Zero sensitive logging: Raw message bodies and verification codes must NEVER appear in stdout/stderr
- Default binding to `127.0.0.1:8080`; non-localhost `--lan` requires explicit device token authorization
- Bounded HTTP payload limit: 1 MB max request body size to prevent memory exhaustion DoS (`TASK-SEC-001`)
- Strict HTTP caching policy: All `/api/v1/` responses emit `Cache-Control: no-store` and `X-Content-Type-Options: nosniff` (`TASK-SEC-002`)

**Scale/Scope**: Personal SMS relay and automation gateway; < 1,000 messages/day.

---

## Constitution Check

*GATE: All principles from `.specify/memory/constitution.md` evaluated.*

| Principle | Check | Status | Evaluation & Mitigation |
|---|---|---|---|
| **P-01: Local-First & Zero-Infrastructure** | Single static binary, embedded SQLite, no Docker/JVM/Postgres. | **PASSED** | Built with pure-Go driver (`modernc.org/sqlite`) with CGO disabled; SQLite database auto-created at `./data/sms.db`. |
| **P-02: Separate Security Domains** | Device write token != Agent MCP read token; default 127.0.0.1. | **PASSED** | Device Bearer token only authenticates `POST /api/v1/messages`. Default binding is `127.0.0.1`. |
| **P-03: Strict Data Minimization & Privacy** | Never log SMS bodies or OTP values; purge mechanism. | **PASSED** | Custom `slog` redaction middleware strictly masks payload bodies and codes. Standard and debug logs omit text. |
| **P-04: Device-Side Pre-Filtering** | Filter on device; default drop. | **PASSED** | Server schema models message status flags (`RECEIVED`, `FILTERED`) and expects pre-filtered device payloads. |
| **P-05: Durable Delivery & Idempotency** | Idempotency on `message_id`; survive retries. | **PASSED** | Unique constraint `UNIQUE(device_id, message_id)` enforces single insertion and returns HTTP 200 on retry. |
| **P-06: Event-Driven Agent MCP** | Event push over polling; timeout safety. | **PASSED** | Foundation includes in-memory pub-sub channel hub in `service/` to power Phase 4 MCP `wait_for_message`. |
| **P-07: Complete Mock SMS Parity** | Same pipeline for test and real SMS. | **PASSED** | Server treats all incoming POST messages through the exact same ingestion and storage path. |
| **P-08: Clean Architecture & Boring Tech** | Simple Go packages, explicit code. | **PASSED** | Layered package structure (`cmd/`, `internal/api/`, `internal/domain/`, `internal/service/`, `internal/storage/`). |
| **P-09: Strict Non-Goals** | Personal relay only; no spam/bypass. | **PASSED** | Focused personal API; no outbound mass SMS or security bypass logic. |

---

## Project Structure

### Documentation (this feature)

```text
specs/001-server-foundation/
├── spec.md              # Feature specification
├── plan.md              # This file (implementation plan)
├── research.md          # Technical research & driver decisions
├── data-model.md        # Database schema & entity definitions
├── quickstart.md        # Build, run, and manual verification steps
├── contracts/
│   └── http-api.yaml    # OpenAPI 3.0 specification for /api/v1
└── checklists/
    └── requirements.md  # Specification quality checklist
```

### Source Code (repository root)

```text
relayx/
├── relayx-server/                   # Standalone Go server module
│   ├── cmd/
│   │   └── server/
│   │       └── main.go              # CLI flags, signal handling, wiring
│   ├── internal/
│   │   ├── api/
│   │   │   ├── handler.go           # Router setup, /api/v1 registration
│   │   │   ├── health.go            # /health handler
│   │   │   ├── messages.go          # /messages ingestion & query handlers
│   │   │   └── middleware.go        # Auth & privacy log masking middleware
│   │   ├── domain/
│   │   │   ├── device.go            # Device entity & repository interface
│   │   │   └── message.go           # Message entity & repository interface
│   │   ├── service/
│   │   │   ├── message_service.go   # Business logic & event broker
│   │   │   └── device_service.go    # Device authentication logic
│   │   ├── storage/
│   │   │   ├── sqlite.go            # Database connection & WAL mode config
│   │   │   ├── migrator.go          # Embedded migration executor
│   │   │   ├── message_repo.go      # SQLite implementation of MessageRepository
│   │   │   └── device_repo.go       # SQLite implementation of DeviceRepository
│   │   └── config/
│   │       └── config.go            # CLI flag parsing & default config
│   ├── migrations/
│   │   └── 001_initial.sql          # Embedded SQL schema (devices, messages, indexes)
│   ├── go.mod                       # Go module definition
│   └── Makefile                     # Cross-compilation targets
└── relayx-android/                  # Android gateway module (SDK 37)
```

---

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| *None* | No constitutional violations identified. | Architecture follows boring, local-first principles. |
