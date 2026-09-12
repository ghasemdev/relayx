# Memory Synthesis: Server Web Dashboard & Live Observability

**Feature**: `specs/004-server-web-dashboard`  
**Date**: 2026-09-13  
**Synthesized For**: Spec & Plan Generation  

---

## 1. System Scope & Objective
Phase 4 expands `relayx-server` from a headless background daemon into a self-observant, developer-friendly relay control center. It adds an embedded Web Dashboard served directly on the server's HTTP port (e.g. `http://127.0.0.1:8080/dashboard/`) providing:
1. **Live Logcat Stream**: Real-time structured log streaming over SSE with level filters and zero-leakage redaction.
2. **SQLite Database Table Browser**: Paginated, sortable inspection of `messages`, `devices`, and `schema_migrations`.
3. **Throughput Metrics & Timeline**: Visual metrics for received, forwarded, filtered, and failed messages.
4. **Device & Token Management**: Inspection of authorized devices and token hashes.

---

## 2. Hard Governance Gates
1. **Constitution P-01**: Must embed all web assets via `go:embed`. Zero runtime node/npm/container dependencies. Zero external CDNs (self-contained CSS/JS).
2. **Constitution P-02**: Must enforce admin authentication domain.
3. **Constitution P-03 & ADR-008**: Logs streamed to browser must never contain raw OTP codes or unmasked message payloads. Messages table must show masked bodies by default with user-initiated reveal.
4. **ADR-004 & BUGS #4**: SSE log stream and live event updates must use non-blocking Go channels and pub-sub broker, preventing busy-polling and file lock contention.
5. **ADR-007**: Database browsing queries must use read-only semantics with query limits.
