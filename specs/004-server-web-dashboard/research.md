# Technical Research: Server Web Dashboard & Live Observability

**Feature**: `specs/004-server-web-dashboard`  
**Date**: 2026-09-13  
**Status**: Completed  

---

## 1. Embedded Web Asset Distribution (Go 1.16+ `embed`)

### Decision
Embed all frontend assets (HTML, CSS, JavaScript, SVG icons) directly into the compiled Go binary using standard library `embed.FS`:
```go
//go:embed static/*
var staticFS embed.FS
```
Serve via `http.FileServer(http.FS(subFS))` under the `/dashboard/` HTTP route prefix.

### Rationale
- **Constitution Principle I (Zero-Infrastructure)**: Eliminates runtime filesystem dependencies, external web servers, or containerized sidecars. Running `./bin/relayx-server` immediately serves both REST APIs and the rich observability dashboard.
- **Air-Gapped & Offline Reliability**: No external CDN links (e.g. unpkg, cdnjs, Google Fonts). Works seamlessly on developer machines without active internet connections.
- **Binary Footprint**: Lightweight vanilla assets add less than 100 KB to the compiled executable.

### Alternatives Considered
- *External filesystem folder (e.g. `./static`)*: Fragile; breaks when running binary from different directories or after installation into `/usr/local/bin`.
- *Separate frontend build container (e.g. React/Vite + Nginx)*: Violates Constitution Principle I and Single-Binary ADR-001.

---

## 2. Real-Time Log Streaming Protocol: Server-Sent Events (SSE)

### Decision
Adopt Server-Sent Events (`text/event-stream`) over HTTP GET `/api/v1/dashboard/logs/stream` for live log streaming to browser clients.

### Rationale
- **Unidirectional Fit**: Logs flow strictly server $\to$ client. SSE is specifically designed for unidirectional real-time data streaming over standard HTTP/1.1 or HTTP/2.
- **Native Browser Support**: Uses standard browser `EventSource` API with automatic reconnection, heartbeat handling, and zero client-side library dependencies.
- **Simplicity**: Avoids WebSocket protocol upgrades, bidirectional state machines, or ping-pong frame maintenance.

### Alternatives Considered
- *WebSockets (`gorilla/websocket` or `nhooyr.io/websocket`)*: Overkill for unidirectional log streaming; introduces additional dependencies and complex connection lifecycle management.
- *Short Polling (`setInterval` fetch)*: Causes CPU spikes, redundant SQLite queries, and high latency (violates ADR-004 and BUGS #4).

---

## 3. Log Broadcaster & Backpressure Resilience

### Decision
Implement a concurrent `LogBroadcaster` within `internal/logging` that hooks into `RedactingHandler`:
- Maintains a thread-safe set of subscriber channels (`chan LogEntry`).
- Maintains an in-memory ring buffer of the last 200 log entries to immediately populate the browser logcat view upon connection.
- Implements non-blocking channel dispatch:
  ```go
  select {
  case ch <- entry:
  default:
      // Drop log for slow consumer to prevent blocking server message ingestion
  }
  ```

### Rationale
- **Constitution Principle III (Privacy Enforcement)**: The broadcaster receives log events *after* passing through `sanitizeAttr`, guaranteeing that raw message bodies, Bearer tokens, and OTP codes are permanently stripped before entering the broadcast pipeline.
- **Resilience Under Load**: Prevents slow, stalled, or backgrounded browser tabs from blocking `slog` logging calls or degrading server ingestion throughput.

---

## 4. SQLite Database Table Browser & Concurrency

### Decision
Provide read-only table browsing endpoints with strict allowlisting and parameterized pagination:
- Tables allowed: `messages`, `devices`, `schema_migrations`.
- Default page size: 25 (max: 100).
- Supported filters: `status`, `sender`, `search` (UUID substring or sender substring).
- File stats: Uses `os.Stat` on the database file and WAL file (`./data/sms.db` and `./data/sms.db-wal`) to report exact disk usage.

### Rationale
- **Safety**: Table names are validated against a strict hardcoded enum to prevent SQL injection.
- **WAL Concurrency**: SQLite WAL mode allows concurrent readers while a writer commits incoming SMS. Using standard read transactions prevents write lock contention.

---

## 5. UI Technology & Component Design

### Decision
Build the dashboard with semantic HTML5, modern vanilla CSS (CSS grid, flexbox, custom properties for dark/light themes), and vanilla ES6 JavaScript modules:
- **Views**:
  - `Overview`: Real-time system health, uptime, memory, storage stats, message counters.
  - `Live Logcat`: Real-time streaming logs with level filters, pause/resume, search, and clear.
  - `Database Browser`: Interactive table viewer for `messages`, `devices`, and migrations with status badges and detail modals.
  - `Device Manager`: List registered devices, inspect token hashes, generate tokens, and revoke access.
- **Privacy Masking**: Message bodies in table rows are masked by default (`••••••••••••`) with one-click eye toggle reveal.

---

## 6. Administrative Security Domain (Constitution Principle II)

### Decision
Support optional admin authentication:
- CLI flag `--admin-token` and env `RELAYX_ADMIN_TOKEN`.
- When set, dashboard endpoints require either:
  1. `Cookie: relayx_admin_token=<token>` (set via a clean login modal in the UI).
  2. `Authorization: Bearer <admin-token>`.
- When not set (default local dev), dashboard is freely accessible on loopback (`127.0.0.1`).
