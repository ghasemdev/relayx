# Bugs & Regression Patterns

Last reviewed: 2026-09-07

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
