# Feature Specification: Android Gateway Foundation

**Feature Branch**: `feature/002-android-gateway-foundation`

**Created**: 2026-09-11

**Status**: Draft

**Input**: User description: "now update roadmap, then start phase2 (Android Gateway Foundation - relayx-android)"

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Diagnostic Dashboard & Forwarding Control (Priority: P1)

As a developer or automation engineer, I want a clean diagnostic dashboard that displays the live status of the gateway (forwarding active, server connectivity, active rules, last message time, and message throughput counters) and allows me to toggle message forwarding on or off.

**Why this priority**: Immediate visibility into gateway health and operational control is essential for validating the setup and troubleshooting communication during automation runs.

**Independent Test**: Can be tested by launching the app, toggling forwarding on/off, and verifying that status indicators and counters update appropriately.

**Acceptance Scenarios**:
1. **Given** the app is launched for the first time, **When** the dashboard appears, **Then** it shows the forwarding state (default OFF until configured), server connection status (unconfigured/disconnected), active rules count (0), and all counters (Received: 0, Forwarded: 0, Filtered: 0, Failed: 0).
2. **Given** valid server settings exist, **When** the user toggles Forwarding to ON, **Then** the operational state immediately reflects "Active/Listening" and background interception is activated.
3. **Given** the system processes incoming messages, **When** messages are handled, **Then** the counters for Received, Forwarded, Filtered, or Failed increment in real-time and the "Last Message" timestamp updates.

---

### User Story 2 - Server Connection Configuration & Validation (Priority: P1)

As a user, I want to configure the destination RelayX server details (host, port, HTTP/HTTPS toggle, device identifier, and bearer authorization token) and execute a one-tap connection test to confirm the server is reachable and accepting traffic.

**Why this priority**: Without server endpoint parameters and credentials, no SMS messages can be successfully ingested by the server.

**Independent Test**: Can be tested by entering valid and invalid server parameters, tapping "Test Connection", and verifying that connection feedback is accurate and responsive.

**Acceptance Scenarios**:
1. **Given** the settings screen is opened, **When** the user enters Host (e.g. `192.168.1.50`), Port (e.g. `8080`), Device ID (e.g. `phone-pixel-01`), and Bearer Token, **Then** the inputs are persisted safely across app restarts.
2. **Given** valid server configuration and an active running server, **When** the user taps "Test Connection", **Then** the app sends a verification probe and displays a clear success confirmation with server latency/status.
3. **Given** an invalid host, stopped server, or wrong credentials, **When** the user taps "Test Connection", **Then** the app displays an actionable diagnostic error (e.g. "Server unreachable on port 8080" or "Authorization rejected").

---

### User Story 3 - Incoming SMS Reception & Durable Offline Storage (Priority: P1)

As an automation system, I need incoming SMS messages to be intercepted by the Android device and immediately committed into durable local storage so that no message is ever lost due to app restarts, process termination, or network outages.

**Why this priority**: SMS delivery is asynchronous and one-shot from the cellular carrier; if a message is dropped during receipt, the OTP or verification code is permanently lost.

**Independent Test**: Can be tested by receiving an SMS while the network is disabled and verifying that the message is durably recorded in the local queue with status "Pending".

**Acceptance Scenarios**:
1. **Given** the app has SMS permissions and forwarding is active, **When** an incoming SMS arrives from the cellular network, **Then** the app captures the sender, payload, and timestamp, increments the "Received" counter, and persists the message locally before any network dispatch is attempted.
2. **Given** the device has no internet connection, **When** an SMS arrives, **Then** the message is saved locally in a "Pending" outbox state without timing out or failing irreversibly.
3. **Given** a long multipart SMS spanning multiple segments, **When** all segments are received, **Then** the app reassembles the full text accurately into a single logical message entity.

---

### User Story 4 - Reliable Forward Dispatch with Exponential Backoff (Priority: P2)

As a user, I want queued messages to be forwarded automatically to the configured RelayX server with exponential backoff retries if the server is temporarily offline or the network drops.

**Why this priority**: Transient network hiccups and server restarts must be handled gracefully without operator intervention or message duplicates.

**Independent Test**: Can be tested by queuing a message while the server is stopped, observing scheduled retries with increasing backoff delays, starting the server, and verifying successful delivery.

**Acceptance Scenarios**:
1. **Given** a pending message in the local queue and active network, **When** the forward dispatcher executes, **Then** it transmits the message payload along with its unique message ID to the server, marks the message "Delivered", and increments the "Forwarded" counter.
2. **Given** the server returns a connection error or HTTP 5xx error, **When** delivery fails, **Then** the dispatcher records the failure attempt, schedules a retry using exponential backoff (e.g., 1s, 2s, 5s, 10s, 30s, up to 60s max), and increments the "Failed / Retrying" status.
3. **Given** a message is successfully delivered on a subsequent retry, **When** the server acknowledges receipt, **Then** the local status transitions to "Delivered" and no duplicate records are generated.

---

### User Story 5 - Background Continuity Across Sleep & Device Reboot (Priority: P2)

As an automation engineer, I want the gateway to keep functioning when the screen is off or the phone reboots, automatically resuming message forwarding as soon as the operating system boots.

**Why this priority**: Dedicated gateway phones run unattended in test labs and server racks; manual intervention after a power cycle or sleep transition defeats automation.

**Independent Test**: Can be tested by restarting the device with pending messages and verifying that the background gateway resumes and delivers the outbox messages once network is restored.

**Acceptance Scenarios**:
1. **Given** forwarding was enabled, **When** the device completes system boot, **Then** the app automatically reinitializes its background receiver and queue dispatcher without requiring the user to open the UI.
2. **Given** the device screen is turned off and the OS enters low-power idle mode, **When** an SMS arrives, **Then** the receiver processes and enqueues the message promptly without being dropped by battery optimization.

---

### User Story 6 - Strict Privacy & Log Masking (Priority: P3)

As a security-conscious user, I want assurance that sensitive message content and verification codes are never leaked into device system logs, crash reports, or visible system notifications.

**Why this priority**: Android system logs (Logcat) can often be read by developer tools or diagnostic utilities; sensitive OTPs must remain strictly confidential.

**Independent Test**: Can be tested by inspecting device logs during SMS receipt and dispatch to ensure message bodies and OTP tokens are masked or omitted.

**Acceptance Scenarios**:
1. **Given** any SMS is received, queued, or dispatched, **When** diagnostic logging is generated, **Then** the log output contains only metadata (sender ID, message identifier, timestamp, byte size, attempt number) and never the raw body or OTP digits.

---

### Edge Cases

- **Airplane Mode / Complete Network Loss**: Messages accumulate safely in the local durable queue and are automatically dispatched in chronological order once connectivity resumes.
- **Server Database Conflict / Duplicate Submission**: If network drops right after the server ingested a message, the subsequent retry with the same unique message identifier is handled idempotently without error.
- **Missing Runtime Permissions**: If `RECEIVE_SMS` or `READ_SMS` permissions are denied by the user, the dashboard displays a prominent, informative permission prompt and disables forwarding until granted.
- **Malformed or Rapid Bursts of SMS**: Rapid concurrent incoming SMS messages are ingested into the database without locking conflicts or UI freezes.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a diagnostic dashboard displaying Forwarding Status, Server Reachability, Active Rules Count, Last Message Timestamp, and Live Counters (Received, Forwarded, Filtered, Failed).
- **FR-002**: System MUST allow users to toggle SMS forwarding service between Enabled and Disabled states.
- **FR-003**: System MUST provide a Server Configuration screen to input and persist: Server Host (IP/Domain), Port, Protocol (HTTP/HTTPS), Device Identifier, and Bearer Authentication Token.
- **FR-004**: System MUST provide an interactive "Test Connection" tool that queries the server health endpoint and provides user-friendly status feedback (latency, reachability, or error reason).
- **FR-005**: System MUST request and validate necessary SMS runtime permissions (`RECEIVE_SMS`, `READ_SMS`) before activating the gateway.
- **FR-006**: System MUST intercept incoming SMS via an Android BroadcastReceiver, assemble multipart messages, and extract sender address, message text, and arrival timestamp.
- **FR-007**: System MUST immediately persist every received eligible SMS into a durable local outbox queue before attempting any network delivery.
- **FR-008**: System MUST assign a globally unique identifier (UUID) to every ingested message for end-to-end deduplication and idempotency.
- **FR-009**: System MUST dispatch pending outbox messages asynchronously to the configured server using HTTP POST with the Bearer authorization token.
- **FR-010**: System MUST automatically retry failed message dispatches using exponential backoff with delays of 1s, 2s, 5s, 10s, 30s, and a maximum delay of 60s.
- **FR-011**: System MUST automatically resume background interception and dispatch following device restart (`BOOT_COMPLETED`) if forwarding was previously enabled.
- **FR-012**: System MUST redact or omit all sensitive message content (body text, OTP codes, credentials) from all application logs and debugging output.

### Key Entities

- **GatewayConfiguration**: Represents user settings including server host, port, useHttps toggle, deviceId, bearerToken, and forwardingEnabled state.
- **QueuedMessage**: Represents an SMS message in the local durable outbox, including unique messageId, sender, body, receivedAt timestamp, deliveryStatus (PENDING, SENDING, DELIVERED, FAILED), retryCount, lastAttemptAt, and nextAttemptAt.
- **GatewayStats**: Represents operational metrics including total messages received, successfully forwarded, filtered/dropped, and failed/retried.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of received SMS messages are committed to durable local storage prior to network dispatch, resulting in zero message loss during simulated app or network failures.
- **SC-002**: Server connection test provides diagnostic feedback (success confirmation or actionable error) in under 3 seconds under normal network conditions.
- **SC-003**: During network interruption, pending messages retry with exponential backoff and resume dispatch within 5 seconds of network restoration.
- **SC-004**: After a full device reboot, the gateway resumes background monitoring within 10 seconds of system boot without user interaction.
- **SC-005**: Zero sensitive message text or verification codes appear in application logs or system traces under standard or debug modes.

---

## Assumptions

- Target devices run Android 7.0 (API Level 24) or higher up to Android 15 (API Level 37).
- User has physical or administrative access to grant runtime SMS permissions on the Android device.
- The RelayX server instance is accessible from the Android device over local Wi-Fi, LAN, VPN, or public IP.
- Default filter policy in this foundational phase accepts all valid SMS and forwards them (full multi-rule filtering engine will be expanded in Phase 3).
