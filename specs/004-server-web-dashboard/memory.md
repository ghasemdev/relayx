# Feature Memory: Server Web Dashboard & Live Observability

**Feature**: `specs/004-server-web-dashboard`  
**Date**: 2026-09-13  
**Status**: Active  

---

## 1. Architectural Constraints & Boundaries
- **P-01 (Single Self-Contained Binary)**: The web dashboard must be embedded directly inside `relayx-server` via Go 1.16+ `go:embed` (static HTML, CSS, JavaScript). No external Node/npm runtime, Docker, or external web server is permitted.
- **P-01 (Zero-CDN Dependency)**: All frontend assets (icons, scripts, styles) must be completely self-contained in the embedded filesystem to ensure full offline functionality and avoid remote CDN dependencies.
- **P-02 (Security Domains)**: The web dashboard provides administrative observability (log streaming, SQLite browser, token management). Access must require administrative authorization (e.g. CLI flag `--admin-token` or dashboard session token) distinct from device write tokens.
- **P-03 & ADR-008 (Strict Data Minimization & Privacy)**:
  - Live log streaming ("Logcat") must strictly sanitize payloads and OTP verification codes before streaming over Server-Sent Events (SSE) or WebSockets.
  - Message browser table must mask message bodies and OTP tokens by default, matching the Android client privacy pattern.
- **ADR-007 (Pure-Go SQLite Concurrency)**: SQLite connection pool must respect single-writer WAL constraints. Database table queries from the web dashboard must be read-only transactions with pagination limits to avoid lock contention with active message ingestion.

---

## 2. Reused Decisions & Patterns
- **ADR-001**: Single Go binary with embedded SQLite (`./data/sms.db`).
- **ADR-004**: Event-driven push mechanics using Go channels (`chan`) and `sync.RWMutex` pub-sub broker, reused for live SSE log streaming and real-time message arrival notifications.
- **ADR-008**: Structured redaction handler wrapping `slog`.

---

## 3. Bug Patterns & Risks to Prevent
- **BUGS #1 (Sensitive Data Leakage)**: Ensure SSE log broadcaster attaches downstream of `RedactingHandler`, not upstream of raw logger calls.
- **BUGS #4 (Busy-Polling)**: SSE and WebSocket streaming must block on channel reads with heartbeat pings, never polling SQLite in loops.
