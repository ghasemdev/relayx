# Memory Synthesis: 001-server-foundation

## Core Directives & Hard Constraints
- **Self-Contained Binary**: The server must compile to a single static binary (`sms-server`). No external database daemon or runtime required.
- **Embedded Database**: SQLite auto-creates `./data/sms.db` on startup; migrations are embedded via `go:embed`.
- **Default Localhost**: Default binding is `127.0.0.1:8080`. `--lan` / `--host 0.0.0.0` requires explicit authentication.
- **Zero Sensitive Logging**: Logging SMS bodies or OTP values is strictly forbidden.
- **Idempotent Ingestion**: `POST /api/v1/messages` must deduplicate based on `(device_id, message_id)`.

## Conflict Analysis
- No conflicts with existing durable memory (`docs/memory/INDEX.md`, `ARCHITECTURE.md`, `DECISIONS.md`).
- Fully complies with Principle I, II, III, and V of `.specify/memory/constitution.md`.
