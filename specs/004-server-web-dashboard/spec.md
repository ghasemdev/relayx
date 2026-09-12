# Feature Specification: Server Web Dashboard & Live Observability

**Feature Branch**: `feature/004-server-web-dashboard`  
**Created**: 2026-09-13  
**Status**: Draft  
**Input**: User description: "start phase 4: Server Web Dashboard & Live Observability (relayx-server)"

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Real-Time Live Log Streaming ("Web Logcat") (Priority: P1) 🎯 MVP

As an automation developer or gateway operator, I want to view a real-time stream of server logs directly in my browser at `http://127.0.0.1:8080/dashboard/logs`, so that I can observe message ingestion, rule evaluation, and dispatch events without running terminal commands or inspecting flat log files.

**Why this priority**: Instant live observability of server events is the most critical diagnostic tool during development, test runs, and live relay debugging.

**Independent Test**: Can be tested by opening the Web Dashboard logs view in a browser, sending a message to `POST /api/v1/messages`, and verifying that the log entry immediately appears in the browser console via Server-Sent Events (SSE) with level color coding.

**Acceptance Scenarios**:
1. **Given** the server daemon is running, **When** the user navigates to `/dashboard/logs`, **Then** the browser establishes a persistent SSE connection and displays recent and incoming structured log events in real time.
2. **Given** the live log viewer is connected, **When** log events arrive, **Then** each entry displays: ISO timestamp, log level badge (`INFO`, `WARN`, `ERROR`, `DEBUG`), component name, message summary, and structured metadata attributes.
3. **Given** the user selects a log level filter (e.g. `WARN` or `ERROR`), **When** filtered, **Then** only log entries matching or exceeding that severity are visible.
4. **Given** active text search, **When** the user types a query (e.g. a device ID or sender address), **Then** the log list dynamically filters to matching lines.
5. **Given** auto-scroll is enabled, **When** new logs arrive, **Then** the log window automatically scrolls to the bottom; if the user scrolls up, auto-scroll pauses and a "Resume Auto-scroll" button appears.
6. **Given** Constitution Principle III (Zero Sensitive Data Leakage), **When** logs are streamed to the browser, **Then** all message bodies and OTP tokens are strictly redacted prior to SSE transmission.

---

### User Story 2 - SQLite Database Table Browser & Record Inspection (Priority: P1)

As a developer maintaining RelayX, I want an interactive, paginated database browser in the web dashboard to inspect the contents of SQLite tables (`messages`, `devices`, `schema_migrations`), so that I can audit stored records, verify schemas, and troubleshoot message states without needing an external SQLite GUI tool.

**Why this priority**: Eliminates third-party tool dependencies (DB Browser for SQLite, DBeaver) and allows immediate inspection of database state directly from the running server.

**Independent Test**: Can be tested by navigating to `/dashboard/database`, selecting the `messages` table, verifying paginated records, and clicking a row to view its full metadata.

**Acceptance Scenarios**:
1. **Given** the user is on `/dashboard/database`, **When** loaded, **Then** the UI displays table selection tabs (`messages`, `devices`, `schema_migrations`) with total record counts and database file stats (DB file size, WAL size).
2. **Given** the `messages` table is selected, **When** rendered, **Then** the UI shows a paginated table displaying: Message UUID, Device ID, Sender, Status badge (`RECEIVED`, `FILTERED`, `FORWARDED`, `FAILED`), and Received At timestamp.
3. **Given** Constitution Principle III, **When** viewing messages in the table, **Then** message body content is masked by default (`••••••••••••`), and an eye toggle icon allows revealing plaintext for individual records.
4. **Given** the user clicks on any message row, **When** clicked, **Then** a modal opens showing full details: raw metadata JSON, delivery attempts, error messages, and exact timestamps.
5. **Given** sorting or filtering controls (e.g. filter by status or sender), **When** applied, **Then** the server executes a paginated SQL query returning matching rows within 100ms.

---

### User Story 3 - Message Throughput & System Health Overview (Priority: P2)

As a system operator, I want an overview dashboard displaying real-time message counters, throughput charts, and server uptime, so that I can quickly assess the operational health of the relay service at a glance.

**Why this priority**: High-level health and throughput awareness prevents silent failures and helps operators monitor relay volume.

**Independent Test**: Can be tested by opening `/dashboard/` and observing live metric cards updating reactively when new messages are ingested.

**Acceptance Scenarios**:
1. **Given** the user navigates to `/dashboard/`, **When** rendered, **Then** the overview screen displays:
   - System Uptime & Memory Usage
   - SQLite Database Status & WAL metrics
   - Message Counters: Total Received, Forwarded, Filtered, Failed
   - Success Rate Percentage (Forwarded / Total Eligible)
2. **Given** new messages arrive, **When** committed to SQLite, **Then** the dashboard counters update dynamically without requiring a manual page refresh.

---

### User Story 4 - Device Management & Token Fingerprint Inspector (Priority: P2)

As an administrator, I want to view registered Android devices, inspect their token fingerprints, and generate or revoke device ingestion tokens from the web dashboard, so that I can easily configure new phones and secure compromised devices.

**Why this priority**: Simplifies gateway onboarding and operational security management.

**Independent Test**: Can be tested by navigating to `/dashboard/devices`, viewing registered devices, and creating a new device token.

**Acceptance Scenarios**:
1. **Given** the user is on `/dashboard/devices`, **When** loaded, **Then** the UI lists all registered devices: Device ID, Device Name, Token SHA-256 Fingerprint, Created Timestamp, and Last Seen Timestamp.
2. **Given** the user clicks "Register New Device", **When** submitted with a device name, **Then** the server generates a cryptographically secure token, displays the raw token once with a copy button, and records the SHA-256 hash in SQLite.
3. **Given** an existing device, **When** the user clicks "Revoke Device", **Then** the server removes or disables the token hash, immediately rejecting subsequent ingestion requests from that device.

---

### Edge Cases

- **SSE Client Disconnection & Reconnection**: When a browser tab is closed or connection drops, the server cleanly cleans up client channels without goroutine or memory leaks. On reconnect, the browser resumes log streaming seamlessly.
- **Log Overflow / High Throughput Burst**: If hundreds of messages arrive per second, the server's log broadcaster uses a bounded ring buffer or drops oldest logs for slow SSE consumers to guarantee that server message ingestion latency is never degraded by browser clients.
- **Air-Gapped / Offline Operation**: The web dashboard must function with 100% fidelity without internet access. All HTML, CSS, JavaScript, and icons must be embedded in the binary with zero remote CDN dependencies.
- **Database Lock Prevention**: Web table queries must execute with read-only semantics (`PRAGMA query_only = ON` or read transactions) to prevent contention with concurrent ingestion writes.
- **Unauthorized Dashboard Access**: When configured with an admin token (`--admin-token`), accessing dashboard routes without authentication prompts for an access key.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST serve an embedded Web Dashboard at `/dashboard/` directly from the standalone Go server binary using `go:embed` for all assets (HTML, CSS, JS).
- **FR-002**: System MUST stream structured server logs in real time over Server-Sent Events (SSE) at `/api/v1/dashboard/logs/stream`.
- **FR-003**: System MUST provide client-side and server-side log filtering by level (`ALL`, `DEBUG`, `INFO`, `WARN`, `ERROR`), component, and substring query.
- **FR-004**: System MUST apply strict structured redaction to all log entries before streaming to browser clients, ensuring zero raw message bodies or OTP verification codes are transmitted (Constitution Principle III).
- **FR-005**: System MUST provide an interactive SQLite table browser for `messages`, `devices`, and `schema_migrations` at `/dashboard/database`.
- **FR-006**: System MUST support paginated, column-sorted queries with status and sender filters for the `messages` table.
- **FR-007**: System MUST mask message body payloads by default in the web table view (`••••••••••••`), providing an explicit toggle button to inspect raw text.
- **FR-008**: System MUST display system health metrics (uptime, memory allocation, SQLite database size, WAL size) and message throughput counters on `/dashboard/`.
- **FR-009**: System MUST provide device management at `/dashboard/devices`, allowing inspection of token fingerprints, registration of new device credentials, and revocation of existing credentials.
- **FR-010**: System MUST maintain zero external runtime dependencies (no Node.js, npm, Docker, Python, or CDN requirements).
- **FR-011**: System MUST support optional administrative authentication via `--admin-token` flag or environment variable `RELAYX_ADMIN_TOKEN` to protect dashboard endpoints.
- **FR-012**: System MUST handle SSE client disconnects gracefully with zero goroutine leaks and protect against slow consumer blocking via buffered log channels.

### Key Entities

- **LogEntry**: Presentation model for streamed log events (Timestamp, Level, Component, Message, Attributes JSON).
- **TableMetadata**: Metadata for SQLite tables (Name, Total Rows, Column Definitions, Primary Keys).
- **TablePage**: Paginated slice of rows with pagination cursor/offset, total count, and column headers.
- **SystemMetrics**: Snapshot of server operational health (Uptime, AllocBytes, NumGoroutine, DBSizeBytes, WALSizeBytes, MessageCounts).
- **DeviceSummary**: Device record for UI (ID, Name, TokenFingerprint, CreatedAt, LastSeenAt).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The Web Dashboard loads in any standard modern web browser in under 200ms from cold launch on localhost.
- **SC-002**: Real-time log streaming latency is under 50ms from `slog` emission to browser rendering.
- **SC-003**: SQLite table queries for tables with up to 50,000 records return paginated results in under 100ms.
- **SC-004**: 100% of frontend assets are self-contained inside the binary; zero outbound network requests to third-party CDNs.
- **SC-005**: Zero sensitive message payloads or OTP codes appear in unmasked logs or default table displays (100% audit compliance with Constitution Principle III).
- **SC-006**: Bounded log broadcasting ensures server message ingestion throughput (`POST /api/v1/messages`) is unaffected by slow or connected SSE browser clients.

---

## Assumptions

- The Web Dashboard is targeted at modern desktop and mobile browsers supporting Server-Sent Events (`EventSource`) and ES6 JavaScript.
- Standard vanilla JavaScript and lightweight modern CSS (CSS custom properties, flexbox/grid) will be used to avoid heavy frontend framework build toolchains and keep binary footprint under 15 MB.
- Server runs on developer machines or local private networks where single-binary distribution is paramount.
