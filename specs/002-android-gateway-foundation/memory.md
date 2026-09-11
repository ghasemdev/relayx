# Feature Memory: 002-android-gateway-foundation

## Scope & Objective
Establish the foundational Android application (`relayx-android`) for RelayX:
1. **Modern Architecture & UI**: Jetpack Compose, Material 3, Clean Architecture (UI -> ViewModel -> Domain Use Cases -> Data Repositories).
2. **Diagnostic Dashboard**:
   - Status indicators: Forwarding toggle (ON/OFF), Server reachability status, Active rules count, Last message timestamp.
   - Live message counters: Received, Forwarded, Filtered, Failed.
3. **Server Connection & Settings**:
   - Configuration for Server Host, Port, HTTPS toggle, Device ID, Bearer Authentication token.
   - "Test Connection" ping utility verifying `GET /api/v1/health` connectivity with the Go server.
4. **SMS Ingestion Engine**:
   - Android `BroadcastReceiver` (`Telephony.Sms.Intents.SMS_RECEIVED_ACTION`) handling incoming SMS PDUs.
   - Runtime permissions workflow for `RECEIVE_SMS` and `READ_SMS`.
5. **Durable Local Offline Queue**:
   - Room Database entity (`QueuedMessage` / `OutboxMessage`) persisting received messages before network dispatch.
   - Guaranteed message retention across process termination and device reboots (`BOOT_COMPLETED`).
6. **Resilient Forward Dispatcher**:
   - Asynchronous forward worker with exponential backoff retries (1s, 2s, 5s, 10s, 30s, 60s cap).
   - HTTP client targeting `POST /api/v1/messages` with `Authorization: Bearer <device-token>`.
   - Guaranteed idempotency via unique `messageId` (UUID).
7. **Privacy & Log Masking**:
   - Strict adherence to Constitution Principle III: Android Logcat and debug statements must NEVER print SMS bodies or verification codes.

## Architectural Constraints & Rules
- **Principle IV (Pre-Filtering & Secure Defaults)**: Prepare architecture so incoming SMS passes through local evaluation before network egress. Default drop policy will be enforced; in this foundation phase, a default catch-all allow rule or initial passthrough rule structure is created.
- **Principle V (Durable Delivery, Offline Resilience & Idempotency)**: SMS receiver must never execute synchronous network calls on the main thread or directly in the receiver. Ingestion pushes immediately to Room database. WorkManager / Coroutine worker handles dispatch.
- **Principle III (Zero Sensitive Logging)**: Sanitize and mask all logcat tags and messages; never output SMS payload or OTP digits.
- **Android Target**: minSdk 24, targetSdk 37, Kotlin 2.4, Compose BOM 2026.08.00.

## Watchpoints & Risks
- **Android Background Execution Limits**: Android 8+ background service limitations and Doze mode require WorkManager or expedited execution to avoid message dispatch stalls while screen is off.
- **Boot Completed Permission**: Requires `RECEIVE_BOOT_COMPLETED` permission and appropriate broadcast receiver registration.
- **SMS PDU Multipart Handling**: Long SMS messages are split into multiple PDUs; receiver must reassemble multipart messages correctly using `Telephony.Sms.Intents.getMessagesFromIntent(intent)`.
