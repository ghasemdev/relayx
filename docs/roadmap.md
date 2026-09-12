# RelayX Development Roadmap & Delivery Plan

This document outlines the sequential 9-phase implementation plan, component deliverables, and Definition of Done for **RelayX**.

---

## 1. Development Phases

### Phase 1 — Server Foundation (`relayx-server`) `[COMPLETED - PR #1]`
- [x] Initialize Go module `relayx-server` (`go.mod`).
- [x] Implement standalone CLI entry point with flags:
  - `--host` (default: `127.0.0.1`)
  - `--port` (default: `8080`)
  - `--data` (default: `./data`)
  - `--debug` (default: `false`)
- [x] Integrate embedded SQLite engine creating `./data/sms.db` on first start.
- [x] Embed SQL schema migrations via `go:embed` (`migrations/*.sql`).
- [x] Implement Domain entities (`Message`, `Device`, `Rule`, `Pairing`).
- [x] Build Message & Device Repository with index optimizations.
- [x] Implement REST API (`/api/v1`):
  - `GET /health`
  - `POST /messages` (with Bearer device token authentication)
  - `GET /messages`, `/messages/latest`, `/messages/{id}`, `/messages/search`
- [x] Implement Graceful Shutdown (SIGINT/SIGTERM) and zero-sensitive logging middleware.
- [x] Unit & integration test suite for server endpoints and storage.

### Phase 2 — Android Gateway Foundation (`relayx-android`) `[COMPLETED - PR #2]`
- [x] Establish Android application module structure with Jetpack Compose & Material 3.
- [x] Design minimal diagnostic dashboard:
  - [x] Status indicators (Forwarding ON/OFF, Server status, Rules count, Last message time)
  - [x] Message counters (Received, Forwarded, Filtered, Failed)
- [x] Build Server Connection & Settings screen:
  - [x] Host, Port, HTTPS toggle, Device ID, Bearer Authentication token
  - [x] Connection test utility with live latency feedback
- [x] Implement Android `BroadcastReceiver` for SMS reception with required runtime permissions and multipart PDU reassembly.
- [x] Ensure background resilience:
  - [x] Screen off operation
  - [x] Device locked handling (within Android OS permissions)
  - [x] `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` restart trigger
- [x] Integrate local Room database for durable offline outbox queue.
- [x] Implement forward dispatcher with WorkManager exponential backoff retries (1s, 2s, 5s, 10s, 30s, 60s max).
- [x] Security hardening:
  - [x] Disable app backup (`android:allowBackup="false"`) with explicit Room and DataStore exclusion rules.
  - [x] Restrict cleartext HTTP traffic strictly to local loopback addresses (`10.0.2.2`, `127.0.0.1`, `localhost`) via `network_security_config.xml`.
  - [x] Strict zero-sensitive logging with `RelayLogger` payload and OTP masking.

### Phase 3 — Android Message Inspection & Detail Modal (`relayx-android`) `[COMPLETED - PR #3]`
- [x] Navigation from Dashboard metric cards (Received, Forwarded, Filtered, Failed) to a dedicated Message List Screen.
- [x] Message List Screen:
  - [x] Filter tabs by status (All, Pending Outbox, Forwarded/Delivered, Filtered, Failed/Retrying) with synchronized `HorizontalPager`.
  - [x] Search by sender, message ID, or date with debounced real-time filtering.
  - [x] Message card items displaying sender, arrival time, delivery status badge, and retry attempts.
  - [x] Edge-to-edge listing with animated bottom navigation and directional collapsible search header.
- [x] Last Message Detail Bottom Sheet:
  - [x] Clicking "Last Message Received" card on Dashboard opens an interactive `ModalBottomSheet`.
  - [x] Displays complete metadata: Unique Message UUID, Sender identity, Ingestion timestamp, Delivery status, Attempt count, Error reasons (if delivery failed).
  - [x] Privacy-preserving content view (masked preview `••••••••••••` with explicit user toggle to inspect).
  - [x] One-tap "Retry Delivery" action button to trigger immediate outbox worker dispatch for failed messages with UUID preservation.

### Phase 4 — Server Web Dashboard & Live Observability (`relayx-server`) `[IN PROGRESS]`
- [ ] Embed Web Dashboard directly inside standalone Go binary (`go:embed` HTML/JS/CSS assets).
- [ ] Live Log Streaming ("Logcat"):
  - Real-time log streaming over Server-Sent Events (SSE) or WebSockets from server structured logger (`slog`).
  - Filter logs by level (`INFO`, `WARN`, `ERROR`), component, or free-text search query.
  - Strict privacy enforcement: payload and OTP masking applied before streaming.
- [ ] SQLite Database Table Browser:
  - Interactive table browser for `messages`, `devices`, and `schema_migrations`.
  - Pagination, column sorting, sender/status filters, and raw JSON record inspection.
  - Connection pool diagnostics (WAL file size, active read/write transactions).
- [ ] Message History & Throughput Metrics:
  - Live timeline of ingested and dispatched SMS messages.
  - Throughput charts and delivery success rate indicators.
- [ ] Device Management & Security View:
  - List registered device IDs and token fingerprints.
  - Add or revoke device write credentials.

### Phase 5 — Rule & Filtering Engine (`relayx-android`)
- [ ] Build client-side Rule Engine evaluated prior to network dispatch.
- [ ] Support Rule Types:
  - **Sender Rules**: Exact match (`sender == "BANK"`), pattern match, allowlists.
  - **Content Rules**: Regex extraction (`\b\d{6}\b`, `verification code`).
- [ ] Support Rule Actions:
  - `FORWARD_RAW`: Forward original SMS text.
  - `FORWARD_TRANSFORMED`: Extract value (e.g. OTP) and optionally omit raw text.
  - `DROP`: Silently discard unapproved messages.
- [ ] Implement deterministic rule priority ordering.
- [ ] Build Rule Management UI in Android app to create, reorder, enable, and disable rules.
- [ ] Comprehensive unit tests for regex extraction and priority evaluation.

### Phase 6 — Model Context Protocol (MCP) Server (`relayx-server`)
- [ ] Embed MCP Server within the Go standalone executable.
- [ ] Implement MCP Tools:
  - `get_latest_message`: Fetch most recent eligible SMS.
  - `get_messages`: Query with sender, deviceId, timestamp filters.
  - `search_messages`: Safe text search on authorized messages.
  - `wait_for_message`: Event-driven blocking call with timeout; wakes via Go channel broadcast on message arrival.
  - `get_otp`: High-level tool extracting verification codes via configurable regex.
- [ ] Enforce independent MCP authorization domain (Agent token != Device write token).
- [ ] Integration tests verifying MCP tools and timeout mechanics.

### Phase 7 — Testing Infrastructure, Mock SMS & Emulator Relay
- [ ] Build Mock SMS simulator within Android Developer Tools:
  - Interactive UI to inject mock sender and message body.
- [ ] Ensure strict architectural parity: Mock SMS must traverse the exact production pipeline:
  `Mock Input -> Filter Engine -> Transformation -> Room Queue -> HTTP Client -> Server -> SQLite -> MCP`.
- [ ] Server Emulator & Simulator Ingestion Hook:
  - Configurable server CLI flags: `--adb-port` (e.g., `5554`) and `--exec-hook` (custom script / command).
  - Automatically executes `adb -s emulator-<port> emu sms send "<sender>" "<text>"` or iOS Simulator hook upon message ingestion.
  - Enables physical phone with live SIM to relay OTP/SMS to server, which immediately re-injects into Android Emulator or iOS Simulator for UI automation testing.
- [ ] Create automated End-to-End integration test validating end-to-end OTP relay without a SIM card.
- [ ] Failure recovery tests: Network outage simulation, server restart survival, and duplicate submission idempotency checks.

### Phase 8 — Two-Phone & Virtual Device Testing Mode
- [ ] Model device pairing protocol (6-digit numeric pairing code or QR code).
- [ ] Implement secure WebSocket device-to-device communication channel.
- [ ] Configure relay topologies:
  - **Topology 1 (Physical-to-Emulator)**: Phone A (physical SIM) -> RelayX Server -> `adb emu sms send` into Android Emulator / `simctl` into iOS Simulator.
  - **Topology 2 (Two-Phone Pair)**: Phone A (SIM-equipped) -> Server / WebSocket -> Phone B (automation device without SIM).
- [ ] Stream filtered SMS events over WebSocket with ACKs, reconnects, and heartbeats.
- [ ] Implement optional Android Accessibility Service integration on Phone B for UI automation testing.

### Phase 9 — Cross-Platform Release & Packaging
- [ ] Build automated cross-compilation pipeline for Go server targets:
  - `sms-server-linux-amd64`
  - `sms-server-linux-arm64`
  - `sms-server-darwin-amd64`
  - `sms-server-darwin-arm64`
- [ ] Package Android release APK (`relayx-android`).
- [ ] Complete documentation suite (`README.md`, `ARCHITECTURE.md`, `SECURITY.md`, `API.md`, `MCP.md`).

---

## 2. Definition of Done (Scenario Verification)

The project is considered complete when the following end-to-end scenario executes successfully:

1. **Zero-Dependency Startup**:
   - Run `./sms-server` on a clean Linux or macOS machine without Docker, JVM, or database installed.
   - Server automatically initializes `./data/sms.db`, starts the Web Dashboard on `127.0.0.1:8080`, and streams live logs.
2. **App Configuration & Inspection**:
   - Configure `relayx-android` with `http://<server-ip>:8080` and device token.
   - Test connection succeeds.
   - Tapping metric cards opens the message history list screen; tapping the last message card opens the detail bottom sheet.
3. **Rule Configuration**:
   - Create rule: `sender = BANK`, regex = `Your verification code is (\d{6})`, action = `FORWARD_TRANSFORMED`.
4. **End-to-End Simulation**:
   - Simulate SMS: `"Your verification code is 482913"` from `"BANK"`.
   - Android evaluates rule locally, extracts OTP, queues in Room, and POSTs to server.
   - Server commits message to SQLite and updates the Web Dashboard real-time feed.
5. **MCP Verification**:
   - AI Agent connects via MCP and calls `wait_for_message(sender="BANK", timeout=60)`.
   - MCP returns the message immediately upon ingestion.
   - Agent extracts `482913`.
6. **Resilience & Privacy**:
   - If server is stopped, Android retries with exponential backoff without losing the message.
   - Retried submissions do not create duplicate messages in SQLite.
   - Zero sensitive SMS content or OTP values appear in server logs, Android logcat, or web dashboard streams.

