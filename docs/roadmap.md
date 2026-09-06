# RelayX Development Roadmap & Delivery Plan

This document outlines the sequential 7-phase implementation plan, component deliverables, and Definition of Done for **RelayX**.

---

## 1. Development Phases

### Phase 1 — Server Foundation (`relayx-server`)
- [ ] Initialize Go module `relayx-server` (`go.mod`).
- [ ] Implement standalone CLI entry point with flags:
  - `--host` (default: `127.0.0.1`)
  - `--port` (default: `8080`)
  - `--data` (default: `./data`)
  - `--debug` (default: `false`)
- [ ] Integrate embedded SQLite engine creating `./data/sms.db` on first start.
- [ ] Embed SQL schema migrations via `go:embed` (`migrations/*.sql`).
- [ ] Implement Domain entities (`Message`, `Device`, `Rule`, `Pairing`).
- [ ] Build Message & Device Repository with index optimizations.
- [ ] Implement REST API (`/api/v1`):
  - `GET /health`
  - `POST /messages` (with Bearer device token authentication)
  - `GET /messages`, `/messages/latest`, `/messages/{id}`, `/messages/search`
- [ ] Implement Graceful Shutdown (SIGINT/SIGTERM) and zero-sensitive logging middleware.
- [ ] Unit & integration test suite for server endpoints and storage.

### Phase 2 — Android Gateway Foundation (`relayx-android`)
- [ ] Establish Android application module structure with Jetpack Compose.
- [ ] Design minimal diagnostic dashboard:
  - Status indicators (Forwarding ON/OFF, Server status, Rules count, Last message time)
  - Message counters (Received, Forwarded, Filtered, Failed)
- [ ] Build Server Connection & Settings screen:
  - Host, Port, HTTPS toggle, Device ID, Bearer Authentication token
  - Connection test utility
- [ ] Implement Android `BroadcastReceiver` for SMS reception with required runtime permissions.
- [ ] Ensure background resilience:
  - Screen off operation
  - Device locked handling (within Android OS permissions)
  - `BOOT_COMPLETED` restart trigger
- [ ] Integrate local Room database for durable offline queue.
- [ ] Implement forward dispatcher with exponential backoff retries (1s, 2s, 5s, 10s, 30s, 60s max).

### Phase 3 — Rule & Filtering Engine
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

### Phase 4 — Model Context Protocol (MCP) Server
- [ ] Embed MCP Server within the Go standalone executable.
- [ ] Implement MCP Tools:
  - `get_latest_message`: Fetch most recent eligible SMS.
  - `get_messages`: Query with sender, deviceId, timestamp filters.
  - `search_messages`: Safe text search on authorized messages.
  - `wait_for_message`: Event-driven blocking call with timeout; wakes via Go channel broadcast on message arrival.
  - `get_otp`: High-level tool extracting verification codes via configurable regex.
- [ ] Enforce independent MCP authorization domain (Agent token != Device write token).
- [ ] Integration tests verifying MCP tools and timeout mechanics.

### Phase 5 — Testing Infrastructure & Mock SMS
- [ ] Build Mock SMS simulator within Android Developer Tools:
  - Interactive UI to inject mock sender and message body.
- [ ] Ensure strict architectural parity: Mock SMS must traverse the exact production pipeline:
  `Mock Input -> Filter Engine -> Transformation -> Room Queue -> HTTP Client -> Server -> SQLite -> MCP`.
- [ ] Create automated End-to-End integration test validating end-to-end OTP relay without a SIM card.
- [ ] Failure recovery tests: Network outage simulation, server restart survival, and duplicate submission idempotency checks.

### Phase 6 — Two-Phone Testing Mode
- [ ] Model device pairing protocol (6-digit numeric pairing code or QR code).
- [ ] Implement secure WebSocket device-to-device communication channel.
- [ ] Configure roles:
  - **Phone A**: SIM-equipped SMS source.
  - **Phone B**: Automation Client without SIM.
- [ ] Stream filtered SMS events over WebSocket with ACKs, reconnects, and heartbeats.
- [ ] Implement optional Android Accessibility Service integration on Phone B for UI automation testing.

### Phase 7 — Cross-Platform Release & Packaging
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
   - Server automatically initializes `./data/sms.db` and listens on `127.0.0.1:8080`.
2. **App Configuration**:
   - Configure `relayx-android` with `http://<server-ip>:8080` and device token.
   - Test connection succeeds.
3. **Rule Configuration**:
   - Create rule: `sender = BANK`, regex = `Your verification code is (\d{6})`, action = `FORWARD_TRANSFORMED`.
4. **End-to-End Simulation**:
   - Simulate SMS: `"Your verification code is 482913"` from `"BANK"`.
   - Android evaluates rule locally, extracts OTP, queues, and POSTs to server.
   - Server commits message to SQLite.
5. **MCP Verification**:
   - AI Agent connects via MCP and calls `wait_for_message(sender="BANK", timeout=60)`.
   - MCP returns the message immediately upon ingestion.
   - Agent extracts `482913`.
6. **Resilience & Privacy**:
   - If server is stopped, Android retries with exponential backoff without losing the message.
   - Retried submissions do not create duplicate messages in SQLite.
   - Zero sensitive SMS content or OTP values appear in server or Android logs.
