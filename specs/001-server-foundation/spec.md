# Feature Specification: Server Foundation

**Feature Branch**: `feature/001-server-foundation`

**Created**: 2026-09-07

**Status**: Draft

**Input**: User description: "Phase 1 Server Foundation from Roadmap: Standalone Go server, CLI, embedded SQLite, embedded migrations, message repository, HTTP API, authentication, and tests."

---

## User Scenarios & Testing

### User Story 1 - Self-Contained Server Execution & Auto-Setup (Priority: P1)

As a developer running automated tests or local AI workflows, I want to execute a single server binary on my machine without installing databases, Docker, or external runtimes, so that the relay server starts immediately and prepares its storage automatically.

**Why this priority**: Without a zero-friction, reliable server startup and automatic database initialization, the rest of the relay system cannot ingest or process messages.

**Independent Test**: Can be verified by executing `./sms-server` in an empty directory on a clean Linux or macOS environment, observing immediate HTTP server readiness on `127.0.0.1:8080`, and verifying the automatic creation of `./data/sms.db`.

**Acceptance Scenarios**:
1. **Given** no existing database directory or configuration files, **When** the user launches `./sms-server`, **Then** the server creates `./data/sms.db`, applies initial embedded migrations, and begins listening on `127.0.0.1:8080`.
2. **Given** an existing populated `./data/sms.db`, **When** the server starts, **Then** it skips already applied migrations and retains all previously stored records without data corruption.
3. **Given** the server is running, **When** the process receives a termination signal (`SIGINT` or `SIGTERM`), **Then** it closes active connections, finalizes database transactions, and exits cleanly within 5 seconds.

---

### User Story 2 - Authenticated Ingestion & Idempotent Storage (Priority: P1)

As an Android gateway or integration client, I want to submit incoming SMS messages over an authenticated HTTP endpoint, so that valid messages are securely persisted while unauthorized or duplicate submissions are rejected.

**Why this priority**: Ingestion is the primary communication bridge between the mobile phone and the server. It must guarantee security, data integrity, and duplicate prevention under erratic mobile network retries.

**Independent Test**: Send HTTP POST requests with a valid Bearer token, verify record insertion in the database, and verify that sending the same message ID multiple times returns success without creating duplicate records.

**Acceptance Scenarios**:
1. **Given** a registered device authorization token, **When** a client submits a valid message payload to `POST /api/v1/messages`, **Then** the server persists the message with status `RECEIVED` and returns HTTP 201 with the created message ID.
2. **Given** an unauthenticated client or invalid token, **When** attempting to submit a message to `POST /api/v1/messages`, **Then** the server rejects the request with HTTP 401 Unauthorized.
3. **Given** a message with `messageId="msg-101"` has already been stored, **When** a network retry submits the identical `messageId`, **Then** the server returns HTTP 200/201 and ensures only one logical message exists in storage.

---

### User Story 3 - Message Querying & Server Health Status (Priority: P2)

As an administrator or automation service, I want to query server health and inspect persisted messages with filters (by sender, device, timestamp, and keyword), so that I can monitor relay operations and retrieve relevant communication records.

**Why this priority**: Required for operational observability, diagnosing forwarding issues, and providing foundation endpoints for downstream MCP tool integrations.

**Independent Test**: Seed multiple messages with distinct senders and timestamps, query `GET /api/v1/messages` with query parameters, and assert that only matching records are returned in chronological order.

**Acceptance Scenarios**:
1. **Given** the server is running, **When** a client requests `GET /api/v1/health`, **Then** the server returns HTTP 200 with uptime, database status, and version information.
2. **Given** multiple stored messages from various senders, **When** querying `GET /api/v1/messages?sender=BANK&limit=10`, **Then** only messages matching sender `BANK` are returned, capped at 10 items, ordered from newest to oldest.
3. **Given** a specific message ID, **When** querying `GET /api/v1/messages/{id}`, **Then** the server returns the message details if found, or HTTP 404 if the ID does not exist.

---

### User Story 4 - Privacy-Preserving Diagnostic Logging (Priority: P3)

As a security-conscious developer, I want server diagnostic logs to record operational events and errors without exposing sensitive message bodies or verification codes, so that system observability does not compromise personal privacy.

**Why this priority**: Essential to protect personal authentication secrets, banking OTPs, and private messages from leaking into terminal scrollback or log aggregator files.

**Independent Test**: Process messages containing mock OTP codes with `--debug` enabled, inspect server standard output and error streams, and verify that the raw message body and secret codes never appear.

**Acceptance Scenarios**:
1. **Given** the server is processing a message with body `"Your verification code is 849201"`, **When** the server logs the HTTP request and database operation, **Then** logs display metadata (timestamp, sender, message ID, byte length) but strictly mask or omit the body and code.
2. **Given** an invalid or malformed request body, **When** an error is logged, **Then** error descriptions explain the validation failure without dumping sensitive request payloads.

---

### Edge Cases
- **Database File Locked**: How does the server behave if `./data/sms.db` is opened exclusively by another process? The server must log a clean error and exit with a non-zero code rather than crashing with an unhandled panic.
- **Malformed JSON Payload**: How does the server handle invalid JSON, empty sender names, or timestamps in the future? The server responds with HTTP 400 Bad Request with specific validation errors.
- **Disk Full / Read-Only Storage**: How does the server handle write failures when disk quota is exhausted? Returns HTTP 500 Internal Server Error, logs a critical storage warning, and preserves server runtime stability.
- **Concurrent Ingestion Race**: When multiple concurrent requests submit the same `messageId` simultaneously, the unique database constraint guarantees that exactly one insertion succeeds and neither request fails abruptly.

---

## Requirements

### Functional Requirements

- **FR-001**: The server MUST compile into a single static standalone binary without external runtime dependencies (no Docker, JVM, Node.js, Python, or external database servers).
- **FR-002**: The server MUST automatically create the database directory and initialize an embedded SQLite database (`./data/sms.db`) with embedded SQL schema migrations on initial startup.
- **FR-003**: The server CLI MUST support configurable flags for `--host` (default: `127.0.0.1`), `--port` (default: `8080`), `--data` (default: `./data`), and `--debug` (default: `false`).
- **FR-004**: The server MUST expose `GET /api/v1/health` returning system health, uptime, and database connectivity.
- **FR-005**: The server MUST authenticate incoming SMS ingestion requests on `POST /api/v1/messages` using Bearer device tokens.
- **FR-006**: Ingestion endpoints MUST be idempotent, deduplicating messages based on unique client-provided message identifiers.
- **FR-007**: The server MUST provide message retrieval endpoints supporting pagination and filtering by sender, device ID, timestamp range, and keyword search.
- **FR-008**: The server MUST handle OS shutdown signals (`SIGINT`, `SIGTERM`), draining active connections and safely closing database transactions before terminating.
- **FR-009**: Standard and debug logs MUST strictly omit and mask sensitive message bodies, verification codes, and personal content.

### Key Entities

- **Device**: Represents an authorized client device (such as an Android phone), containing an identifier, friendly name, hashed authorization token, creation timestamp, and last-seen timestamp.
- **Message**: Represents an incoming SMS record, containing a unique message identifier, device identifier, sender name/number, message body, carrier arrival timestamp, ingestion timestamp, processing status (`RECEIVED`, `FILTERED`, `FORWARDED`, `FAILED`), and structured metadata.

---

## Success Criteria

### Measurable Outcomes

- **SC-001**: Server binary initializes storage, applies migrations, and achieves listening state in under 1 second on standard hardware.
- **SC-002**: Ingestion latency for `POST /api/v1/messages` (validation, token auth, and SQLite commit) is under 20 milliseconds at p95.
- **SC-003**: Server handles 100 concurrent message ingestion requests with zero dropped records or database locks.
- **SC-004**: 100% of duplicate message retries with identical `messageId` return successful responses without creating redundant storage rows.
- **SC-005**: 0 instances of verification codes or raw SMS message bodies appear in standard or diagnostic server logs across all test suites.

---

## Assumptions

- The server is operated in personal automation and development environments; high-availability multi-node clustering is out of scope.
- Ingestion clients (Android phones) are capable of generating unique, stable message identifiers (UUID or device-scoped hash).
- Default binding to `127.0.0.1` is sufficient for local development; remote network access requires explicit configuration.
