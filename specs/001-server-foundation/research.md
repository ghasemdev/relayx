# Research & Technical Decisions: Server Foundation

**Feature**: 001-server-foundation  
**Date**: 2026-09-07  
**Status**: Completed  

---

## 1. SQLite Driver & Embedded Database

### Decision
Use a pure-Go SQLite driver (`modernc.org/sqlite` or `github.com/glebarez/go-sqlite`) rather than the standard CGO-based `github.com/mattn/go-sqlite3`.

### Rationale
- **CGO-Free Cross-Compilation**: RelayX requires building single standalone binaries for Linux AMD64, Linux ARM64, macOS ARM64, and macOS AMD64 from a single developer machine. Using CGO requires cross-compiler toolchains (like `x86_64-linux-musl-gcc`), which introduces complex external build dependencies.
- **Embedded Operation**: Pure-Go SQLite embeds cleanly into the executable with `CGO_ENABLED=0`, producing truly static, portable binaries with zero host library requirements.
- **Performance**: For personal SMS ingestion and local queries (< 1,000 req/min), pure-Go SQLite provides sub-millisecond query performance, easily satisfying the p95 < 20ms requirement.

### Alternatives Considered
- `github.com/mattn/go-sqlite3`: Standard CGO driver. Faster under heavy multithreaded benchmarks, but requires CGO and external C compilers for cross-platform builds, directly violating Principle I.
- `database/sql` file storage (JSON/BoltDB): Lacks SQL indexing, relation constraints, and standard query interfaces required for message querying and future MCP expansion.

---

## 2. Embedded Migration Engine

### Decision
Implement an internal lightweight migration runner utilizing Go standard library `embed.FS` (`go:embed migrations/*.sql`) and a minimal tracking table `schema_migrations`.

### Rationale
- **Zero External Dependencies**: Heavy migration tools (like `golang-migrate/migrate` CLI or Goose binary) require extra runtime setup or large dependency trees.
- **Simplicity & Predictability**: Reading `.sql` files from the embedded filesystem, sorting them alphabetically (`001_initial.sql`, `002_...`), and executing unapplied migrations inside a single database transaction guarantees atomic schema evolution on first startup without external scripts.

### Alternatives Considered
- External migration CLI tools (`goose`, `migrate`): Require installing separate binaries or maintaining external configuration files.
- Manual hardcoded Go schema strings: Difficult to test, lacks version tracking, and hard to inspect compared to standalone `.sql` files.

---

## 3. HTTP Server & Routing Architecture

### Decision
Use Go standard library `net/http` with the enhanced pattern-matching router (`http.ServeMux`) available in modern Go (Go 1.22+).

### Rationale
- **Built-in Enhanced Routing**: Go's native router supports method-based and path-parameter routing directly (e.g. `GET /api/v1/messages/{id}`, `POST /api/v1/messages`).
- **Zero External Frameworks**: Avoids pulling in third-party web frameworks like Gin, Fiber, or Echo, keeping the dependency footprint minimal and the binary size compact.
- **Clean Middleware Pipeline**: Standard `http.Handler` chaining for authentication, recovery, and privacy-preserving logging.

### Alternatives Considered
- `gin-gonic/gin`: Popular, but introduces dozens of transitive dependencies and unnecessary complexity for a simple personal REST API.
- `go-chi/chi`: Lightweight, but native `http.ServeMux` in Go 1.22+ handles path parameters and methods natively without extra dependencies.

---

## 4. Sensitive Log Redaction & Privacy

### Decision
Build a custom structured `slog.Handler` (or middleware wrapper around `log/slog`) that explicitly strips sensitive fields (`body`, `otp`, `code`, `token`) from all log records.

### Rationale
- **Privacy Compliance**: Principle III and the constitution strictly mandate that message bodies and OTP codes must NEVER appear in stdout/stderr, even in `--debug` mode.
- **Centralized Enforcement**: Implementing redaction at the logger/middleware boundary ensures that no individual handler or repository print statement can accidentally leak private message content.
- **Auditability**: Logs record event metadata (`event=message_ingested`, `device_id=...`, `sender=BANK`, `body_bytes=34`, `duration_ms=4.2`) without exposing the payload text.

### Alternatives Considered
- Relying on developer discipline: High risk of accidental leakage during error prints or debug logging.
- Disabling logs completely: Destroys observability and troubleshooting capability.

---

## 5. Ingestion Idempotency & Concurrency

### Decision
Enforce a composite unique constraint on `(device_id, message_id)` in SQLite, and handle duplicates gracefully with `ON CONFLICT(device_id, message_id) DO NOTHING` or duplicate inspection returning HTTP 200 with existing message metadata.

### Rationale
- **Mobile Network Retries**: Android networks regularly retry POST requests when acknowledgments are delayed.
- **Data Integrity**: Idempotent deduplication ensures that retried requests succeed cleanly from the phone's perspective while keeping database records unique.

### Alternatives Considered
- Client timestamp deduplication: Fragile due to device clock skew and network jitter.
- Application-level in-memory cache: Loses state on server restart, allowing duplicates to slip through after reboot.
