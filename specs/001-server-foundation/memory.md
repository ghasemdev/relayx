# Feature Memory: 001-server-foundation

## Scope & Objective
Establish the foundational standalone Go server (`relayx-server`) that provides:
1. Self-contained CLI with graceful shutdown and configurable flags (`--host`, `--port`, `--data`, `--debug`).
2. Embedded SQLite database engine with auto-initialization (`./data/sms.db`) and embedded migrations (`go:embed`).
3. Message, Device, Rule, and Pairing domain entities and repositories with database indexes.
4. Secure, versioned REST API (`/api/v1`) with `/health` and authenticated `POST /messages` ingestion.
5. Zero-sensitive logging middleware masking message bodies and OTP values.

## Architectural Constraints & Rules
- **No Heavy Runtimes**: Zero dependencies on JVM, Docker, Postgres, Redis, Node, or Python. Single binary distribution.
- **Dual Security Domain**: Device write token (`Bearer <token>`) authenticates ingestion; does not grant MCP access.
- **Idempotency**: Duplicate POST requests with the same `messageId` must return HTTP 200/201 without creating duplicate database rows.
- **Data Minimization**: Standard and debug logs must NEVER log SMS bodies or OTP values.

## Watchpoints & Risks
- **CGO Dependency vs Cross-Compilation**: Prefer a pure-Go SQLite driver (e.g. `modernc.org/sqlite`) so cross-compiling to Linux/macOS AMD64 and ARM64 does not require a cross-C compiler toolchain.
- **Signal Handling**: Ensure SIGINT and SIGTERM trigger clean database connection closing and HTTP server shutdown.
