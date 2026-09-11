# Technical Research: Android Gateway Foundation

**Feature**: `specs/002-android-gateway-foundation`
**Date**: 2026-09-11

---

## 1. Local Offline Queue & Persistence

- **Decision**: Android Room (`androidx.room:room-runtime`, `androidx.room:room-ktx`, with KSP compiler).
- **Rationale**: 
  - Jetpack standard providing compile-time SQL query validation and seamless Kotlin Coroutines / Flow integration.
  - ACID transactions guarantee that a message is safely persisted to SQLite before any network dispatch is attempted (Constitution Principle V).
  - Clean migration path for future schema enhancements in Phase 3 (rule association).
- **Alternatives Considered**:
  - *Raw SQLiteOpenHelper*: Rejected due to high boilerplate, lack of compile-time query verification, and complex schema migration handling.
  - *DataStore / SharedPreferences*: Rejected for queue storage because a relational queue requires queries by status (`PENDING`), ordering by timestamp, and atomic status updates (`PENDING` -> `SENDING` -> `DELIVERED`).
  - *Realm / ObjectBox*: Rejected because they introduce heavy proprietary native C++ binaries, conflicting with Constitution Principle VIII (Boring Technology).

---

## 2. Background Resilience & Execution Strategy

- **Decision**: Hybrid Dispatch Architecture:
  1. **Fast-Path**: BroadcastReceiver `goAsync()` launches an immediate coroutine using `Dispatchers.IO` to insert into Room and immediately attempt direct HTTP dispatch if network is active (<100ms latency).
  2. **Durable Retry-Path**: Enqueues an expedited or standard `WorkManager` one-time work request (`MessageDispatchWorker`) with `NetworkType.CONNECTED` constraints and exponential backoff.
  3. **Boot Continuity**: `BootReceiver` listens for `android.intent.action.BOOT_COMPLETED` and enqueues `MessageDispatchWorker` to process any pending outbox records left from before the restart.
- **Rationale**:
  - BroadcastReceiver has limited execution window (10-15s) in Android 8.0+. `goAsync()` gives sufficient time to commit to Room.
  - `WorkManager` is the Google-recommended mechanism for deferrable, guaranteed background execution surviving process death, Doze mode, and device reboots.
  - Exponential backoff (10s initial, escalating up to 60s max per Constitution Principle V) ensures the device does not drain battery when the server is down.
- **Alternatives Considered**:
  - *Persistent Foreground Service*: Rejected for Phase 2 because it displays an ongoing notification, requires complex notification management, and is subject to strict Android 14+ foreground service type restrictions. WorkManager is lighter and sufficient for queue draining.
  - *AlarmManager*: Rejected due to Doze-mode deferrals and lack of built-in network condition constraints.

---

## 3. Network Transport & HTTP Client

- **Decision**: `OkHttp` (v4/v5) with Kotlin Coroutines.
- **Rationale**:
  - Native connection pooling, lightweight memory footprint, and first-class support for timeouts (`connectTimeout: 3s`, `readTimeout: 5s`).
  - Simple, robust implementation of `Authorization: Bearer <token>` injection and custom privacy interceptor.
  - Clean error handling (distinguishes network reachability, timeouts, HTTP 401 unauthorized, and HTTP 5xx errors for actionable UI diagnostics).
- **Alternatives Considered**:
  - *Ktor Client*: Evaluated, but introduces additional engine abstractions and larger binary dependencies for a straightforward 2-endpoint client (`GET /api/v1/health` and `POST /api/v1/messages`).
  - *Retrofit*: Unnecessary layer of reflection-based interface proxies for simple single-entity ingestion.

---

## 4. Data Serialization

- **Decision**: `kotlinx.serialization` (JSON).
- **Rationale**:
  - Official Jetpack/Kotlin standard; compiler-plugin based with zero runtime reflection.
  - Completely R8/Proguard safe without complex keep-rules.
  - Extremely lightweight with minimal overhead.
- **Alternatives Considered**:
  - *Gson*: Reflection-heavy, slower, does not enforce Kotlin non-nullability constraints, prone to subtle deserialization bugs.
  - *Moshi*: Excellent, but `kotlinx.serialization` is preferred for modern Kotlin Compose multiplatform consistency.

---

## 5. Gateway Configuration Storage

- **Decision**: `androidx.datastore:datastore-preferences`.
- **Rationale**:
  - Asynchronous, transactional, and exposes reactive Kotlin `Flow` for real-time UI updates when configuration changes.
  - Replaces legacy blocking `SharedPreferences`.
- **Alternatives Considered**:
  - *Room Settings Table*: Overkill for 6 simple scalar configuration keys (Host, Port, Https, DeviceId, Token, Enabled).

---

## 6. Privacy & Zero-Leak Logging Architecture

- **Decision**: Custom `RelayLogger` abstraction wrapping `android.util.Log`.
- **Rationale**:
  - Enforces Constitution Principle III across all Android components.
  - Formats log messages with explicit redaction: message bodies, verification codes, and authentication tokens are filtered out before sending to Logcat.
  - Only metadata is logged: `messageId`, `sender`, `status`, `attemptCount`, and error class.
- **Alternatives Considered**:
  - *Direct Log.d / Timber*: Rejected because unconstrained logging risks accidental leaks of SMS verification codes into system logcat.

---

## 7. Android Permissions & SMS Handling

- **Decision**:
  - Manifest permissions: `RECEIVE_SMS`, `READ_SMS`, `INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`.
  - Runtime permission check: Jetpack Compose `rememberLauncherForActivityResult` requests `RECEIVE_SMS` and `READ_SMS` on first launch or when the user toggles Forwarding ON.
  - Multipart SMS reassembly: Utilizes `Telephony.Sms.Intents.getMessagesFromIntent(intent)` to concatenate segmented SMS PDUs into a single coherent text before Room storage.
