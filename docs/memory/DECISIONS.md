# Architecture Decision Records (ADRs)

Last reviewed: 2026-09-07

## ADR Index

| ADR ID | Decision Title | Status | Date |
|---|---|---|---|
| [ADR-001](#adr-001-single-self-contained-go-binary-with-embedded-sqlite) | Single Self-Contained Go Binary with Embedded SQLite | Accepted | 2026-09-07 |
| [ADR-002](#adr-002-dual-security-and-authorization-domains) | Dual Security and Authorization Domains | Accepted | 2026-09-07 |
| [ADR-003](#adr-003-client-side-pre-filtering-with-default-drop-policy) | Client-Side Pre-Filtering with Default-DROP Policy | Accepted | 2026-09-07 |
| [ADR-004](#adr-004-event-driven-push-model-for-mcp-wait_for_message) | Event-Driven Push Model for MCP `wait_for_message` | Accepted | 2026-09-07 |
| [ADR-005](#adr-005-durable-room-queue-and-exponential-backoff) | Durable Room Queue and Exponential Backoff | Accepted | 2026-09-07 |
| [ADR-006](#adr-006-unified-pipeline-execution-for-mock-sms) | Unified Pipeline Execution for Mock SMS | Accepted | 2026-09-07 |
| [ADR-007](#adr-007-pure-go-sqlite-driver-for-cgo-free-cross-compilation) | Pure-Go SQLite Driver for CGO-Free Cross-Compilation | Accepted | 2026-09-07 |
| [ADR-008](#adr-008-structured-redaction-handler-for-zero-leakage-privacy) | Structured Redaction Handler for Zero-Leakage Privacy | Accepted | 2026-09-07 |
| [ADR-009](#adr-009-server-side-ingestion-hooks-for-emulator-and-simulator-relay) | Server-Side Ingestion Hooks for Emulator and Simulator Relay | Accepted | 2026-09-11 |

---

### ADR-001: Single Self-Contained Go Binary with Embedded SQLite
- **Context**: The relay server must run on personal developer machines (Linux, macOS) with zero friction and zero setup.
- **Decision**: Build the server entirely in Go, embedding SQLite and migrations (`go:embed migrations/*.sql`). Reject JVM/Ktor, Docker, Postgres, Redis, Python, or Node runtimes.
- **Consequences**:
  - Positive: Running `./sms-server` immediately starts without container daemons or dependency management.
  - Positive: Cross-compilation to Linux (AMD64/ARM64) and macOS (ARM64/AMD64) is trivial.
  - Tradeoff: CGO-free or modern pure-Go SQLite driver (such as `modernc.org/sqlite`) preferred to maintain static binary cross-compilation simplicity.

### ADR-002: Dual Security and Authorization Domains
- **Context**: An Android phone sends SMS data to the server, and an AI Agent reads SMS data via MCP. A compromised phone token must not allow reading all stored SMS.
- **Decision**: Strictly separate ingestion credentials from MCP query credentials. The device write token only permits `POST /api/v1/messages`. Reading requires explicit MCP credentials.
- **Consequences**:
  - Positive: Protects stored SMS and OTP history from unauthorized exposure.
  - Positive: Allows individual device revoking without invalidating AI Agent setups.

### ADR-003: Client-Side Pre-Filtering with Default-DROP Policy
- **Context**: Personal smartphones receive diverse messages (personal chats, spam, banking). Forwarding every message exposes unnecessary personal data.
- **Decision**: Evaluate rules locally on the Android device prior to network dispatch. The default action is `DROP`. Only explicitly matched messages are forwarded. If `FORWARD_TRANSFORMED` is active, the raw body can be stripped.
- **Consequences**:
  - Positive: High privacy; personal SMS never leaves the smartphone.
  - Tradeoff: Android client requires local rule management UI and engine.

### ADR-004: Event-Driven Push Model for MCP `wait_for_message`
- **Context**: When an AI Agent needs an OTP, it needs to wait for the incoming SMS. Constant polling wastes CPU and creates latency.
- **Decision**: Implement an internal Go event broker (channels / conditional broadcast) that notifies waiting MCP requests immediately when a message is committed.
- **Consequences**:
  - Positive: Sub-millisecond response latency once SMS is ingested. Zero CPU overhead while waiting.
  - Positive: Robust timeout handling built into Go `select` blocks.

### ADR-005: Durable Room Queue and Exponential Backoff
- **Context**: Mobile connectivity is erratic (Wi-Fi drops, sleep modes, reboots). SMS cannot be lost.
- **Decision**: Persist incoming allowed SMS into a local Room database queue before attempting network delivery. Use Android WorkManager / Foreground Service with exponential backoff retries.
- **Consequences**:
  - Positive: Survives device reboots (`BOOT_COMPLETED`), network changes, and server restarts.
  - Positive: Idempotent message IDs prevent duplication on the server.

### ADR-006: Unified Pipeline Execution for Mock SMS
- **Context**: Developers need to build, test, and verify MCP automation without access to a live SIM card.
- **Decision**: Mock SMS inputs in developer settings must pass through the exact same receiver, filter engine, Room queue, network client, and server pipeline as live SMS.
- **Consequences**:
  - Positive: Total test fidelity. If the mock pipeline succeeds, live SIM traffic is guaranteed to work.
  - Positive: End-to-end integration tests can run reliably in CI environments.

### ADR-007: Pure-Go SQLite Driver for CGO-Free Cross-Compilation
- **Context**: RelayX requires single-command cross-compilation across Linux (AMD64, ARM64) and macOS (ARM64, AMD64) without requiring external C compilers, Docker buildx, or sysroots.
- **Decision**: Adopt pure-Go SQLite driver `modernc.org/sqlite` instead of CGO-dependent `mattn/go-sqlite3`. Configure WAL mode, `busy_timeout=5000`, and single open connection pool (`SetMaxOpenConns(1)`).
- **Consequences**:
  - Positive: Complete elimination of CGO (`CGO_ENABLED=0`) across all target platforms.
  - Positive: Server auto-creates `./data/sms.db` and applies embedded migrations seamlessly on macOS and Linux hosts.
  - Tradeoff: Concurrent write operations require careful coordination via `SetMaxOpenConns(1)` to avoid file lock contention.

### ADR-008: Structured Redaction Handler for Zero-Leakage Privacy
- **Context**: RelayX handles sensitive authentication OTP codes and confidential SMS messages. Operational observability and debug logging must never leak secrets into logs or stdout.
- **Decision**: Wrap standard Go `slog` with a custom `RedactingHandler` that intercepts and scrubs sensitive keys (`body`, `otp`, `code`, `token`, `authorization`) and bearer auth tokens before outputting structured JSON logs.
- **Consequences**:
  - Positive: Enforces Constitution Principle III across all existing and future server packages automatically.
  - Positive: Validated with automated assertions in `logger_test.go` ensuring zero leakage even in `--debug` mode.
  - Tradeoff: Diagnostic logs must rely strictly on metadata (sender, message ID, timestamp, byte length) rather than inspecting raw payloads.

### ADR-009: Server-Side Ingestion Hooks for Emulator and Simulator Relay
- **Context**: In real-world automated testing, a physical smartphone with a live cellular carrier SIM receives 2FA/bank verification SMS. Testing scripts and UI automation frequently run inside virtual devices (Android Emulator or iOS Simulator) without SIM capabilities.
- **Decision**: Provide configurable server-side ingestion hooks in `relayx-server`:
  1. `--adb-port <port>` (e.g. `5554`): Automatically executes `adb -s emulator-<port> emu sms send "<sender>" "<body/code>"` whenever a new SMS is ingested.
  2. `--exec-hook <path>`: Runs a custom executable or script with message metadata and payload passed via arguments and environment variables (`RELAYX_SENDER`, `RELAYX_MESSAGE_ID`, `RELAYX_DEVICE_ID`, `RELAYX_BODY`) to support iOS Simulator (`xcrun simctl push` / SMS injection) or third-party webhooks.
  3. Execution is asynchronous and decoupled from HTTP response latency.
  4. Logging respects Constitution Principle III by redacting sensitive payload bodies.
- **Consequences**:
  - Positive: Enables seamless Physical SIM Phone -> RelayX Server -> Emulator/Simulator automation flow.
  - Positive: Test suites can receive real verification codes directly inside running emulators without manual code copying.
  - Tradeoff: Requires ADB or simulator tools to be available in the server runtime environment if hooks are enabled.

