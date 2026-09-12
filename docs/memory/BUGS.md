# Bugs & Regression Patterns

Last reviewed: 2026-09-13

This document tracks identified failure modes, architectural edge cases, and regression traps to prevent during development.

---

## High-Risk Regression Traps

### 1. Sensitive Data Leakage in Logging
- **Symptom**: SMS text or OTP verification codes appear in server stdout, Android logcat, or error traces.
- **Root Cause**: Unmasked debug prints or logging entire request/response payloads in middleware.
- **Prevention**: Enforce payload masking in log interceptors. Standard and debug logs must output only message ID, sender identity, and action status. Add automated tests asserting that dummy OTP patterns do not exist in log outputs.

### 2. Android Doze Mode Dropping SMS Queue Processing
- **Symptom**: SMS arrives while phone screen is off/locked, but is not forwarded until the user wakes the screen.
- **Root Cause**: Standard WorkManager jobs being deferred during deep Doze / App Standby states.
- **Prevention**: Use high-priority foreground execution or appropriate expedited WorkManager constraints. Request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` where user-approved.

### 3. Duplicate Message Creation on Network Retries
- **Symptom**: When network latency delays server ACKs, Android retries and creates multiple duplicate records.
- **Root Cause**: Server insert query generating fresh auto-increment IDs without enforcing unique `message_id` constraint.
- **Prevention**: Server schema must enforce `UNIQUE(device_id, message_id)`. The `POST /api/v1/messages` handler must execute an `ON CONFLICT DO NOTHING` or return HTTP 200 with existing record details.

### 4. Busy-Polling in MCP `wait_for_message`
- **Symptom**: Server CPU spikes to 100% when an agent waits for an SMS.
- **Root Cause**: Implementing wait logic via a `for { db.Query(...) ; time.Sleep(...) }` loop.
- **Prevention**: Implement a pub-sub subscription using Go channels and `sync.RWMutex`. The HTTP ingestion handler broadcasts new messages to active channels; the wait handler selects on the channel or `time.After(timeout)`.

### 5. False Assumptions About Device Security State
- **Symptom**: Expecting Accessibility Service to unlock a PIN-locked phone or operate when phone is turned off.
- **Root Cause**: Misunderstanding Android OS security and hardware boundaries.
- **Prevention**: Clearly document physical constraints in documentation: screen off is supported, but locked keystore restrictions apply. Power-off execution is physically impossible.

### 6. CR/LF Telnet Command Injection in Android Emulator Console Hooks
- **Symptom**: Ingesting messages containing newlines (`\r\n`) through `--adb-port` hook causes unexpected emulator console command execution or truncated SMS bodies.
- **Root Cause**: `adb emu sms send <sender> <body>` transmits commands over the emulator's raw telnet console, where unescaped CR/LF characters trigger new commands.
- **Prevention**: Enforce `SanitizeADBInput` on all arguments before passing to `adb emu`, stripping `\r` and translating `\n` to spaces. Verify via automated unit tests in `hook_test.go`.

### 7. Physical ADB Extraction of Local SQLite & DataStore Preferences
- **Symptom**: Running `adb backup` on an unlocked device extracts outbox SMS history, OTPs, and device Bearer tokens.
- **Root Cause**: Default Android manifest configuration allows full backup unless explicitly disabled.
- **Prevention**: Keep `android:allowBackup="false"` set in `AndroidManifest.xml` and maintain explicit exclusion rules in `backup_rules.xml` and `data_extraction_rules.xml`.

### 8. Compose Parameter Ordering Lint Violation (`Modifier` not first optional)
- **Symptom**: Android Studio / Compose compiler lint warning: *"Modifier parameter should be the first optional parameter"*.
- **Root Cause**: Placing optional ViewModel defaults (e.g. `viewModel: MessageListViewModel = koinViewModel()`) or optional event callbacks ahead of `modifier: Modifier = Modifier`.
- **Prevention**: Strictly order Composable parameters: (1) required parameters without default values, (2) `modifier: Modifier = Modifier` as the first optional parameter, (3) remaining optional parameters with default values.

### 9. HTTP Middleware ResponseWriter Wrapping Stripping `http.Flusher`
- **Symptom**: Server-Sent Events (SSE) endpoints return `streaming unsupported by server` (HTTP 500) when accessed through standard HTTP middleware chains.
- **Root Cause**: Interceptor structs that wrap `http.ResponseWriter` (e.g. to log response status codes) embed the interface but do not explicitly implement `http.Flusher` or `Unwrap() http.ResponseWriter`. The standard type assertion `w.(http.Flusher)` fails on the wrapped struct.
- **Prevention**: Any custom `http.ResponseWriter` wrapper must delegate `Flush()` to the underlying writer if it implements `http.Flusher`, and provide `Unwrap() http.ResponseWriter` (Go 1.20+ convention). Verify SSE streaming in end-to-end integration tests through the full middleware stack.

### 10. SQLite Foreign Key Constraint Violations on Device Revocation
- **Symptom**: Deleting a device via `DELETE /api/v1/dashboard/devices/{id}` fails with `FOREIGN KEY constraint failed` (HTTP 500) if the device has previously ingested messages.
- **Root Cause**: In SQLite with foreign keys active (`PRAGMA foreign_keys = ON;`), the `messages` table schema defines `device_id TEXT NOT NULL REFERENCES devices(id)` without `ON DELETE CASCADE`. Executing `DELETE FROM devices WHERE id = ?` fails immediately.
- **Prevention**: Implement repository deletion inside an atomic transaction: first delete related dependent records (`DELETE FROM messages WHERE device_id = ?;`), then delete the parent entity (`DELETE FROM devices WHERE id = ?;`).



