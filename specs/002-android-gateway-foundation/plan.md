# Implementation Plan: Android Gateway Foundation

**Branch**: `feature/002-android-gateway-foundation` | **Date**: 2026-09-11 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-android-gateway-foundation/spec.md`

---

## Summary

Build the foundational Android application module (`relayx-android`) for RelayX. The Android gateway provides a modern Jetpack Compose Material 3 diagnostic dashboard, a server settings and connection testing utility, a background SMS receiver handling multipart SMS PDUs, a durable Room-backed offline outbox queue, and an asynchronous HTTP forward dispatcher with exponential backoff retries targeting `relayx-server` (`POST /api/v1/messages`), while strictly enforcing zero-sensitive logging.

---

## Technical Context

**Language/Version**: Kotlin 2.4+ (Kotlin 2.4.10) / Android SDK 37 (minSdk 24, compileSdk 37, targetSdk 37)

**Primary Dependencies**:
- UI: Jetpack Compose BOM 2026.08.00, Material 3, Activity Compose 1.13.0, Lifecycle Runtime KTX 2.11.0, Lifecycle ViewModel Compose
- Persistence: Android Room (`androidx.room:room-runtime`, `androidx.room:room-ktx`) + KSP compiler
- Background Scheduling: Android WorkManager (`androidx.work:work-runtime-ktx`)
- Networking: OkHttp 4.12.0
- Serialization: `kotlinx.serialization` JSON (1.7+)
- Preferences: `androidx.datastore:datastore-preferences`

**Storage**: Local SQLite via Android Room (`relayx_gateway.db`) for the outbox message queue; DataStore Preferences for server settings and flags.

**Testing**:
- Unit Tests: JUnit 4, Kotlinx Coroutines Test, MockK / Fake repositories (`rtk ./gradlew test`)
- Android Instrumentation Tests: AndroidX Test, Espresso, Compose UI Testing (`rtk ./gradlew connectedCheck`)

**Target Platform**: Android devices & emulators running Android 7.0 (API 24) through Android 15+ (API 37).

**Project Type**: Native Android Application (`relayx-android`)

**Performance Goals**:
- SMS receipt-to-Room persistence in < 50ms
- Immediate fast-path network dispatch in < 150ms under good network conditions
- Dashboard launch & first render in < 500ms
- Minimal battery impact (< 1% daily battery drain under idle standby)

**Constraints**:
- Must survive process death, deep Doze mode, and device reboots (`BOOT_COMPLETED`)
- Zero sensitive logging: Raw SMS bodies and verification OTP codes must NEVER be printed to Logcat
- Default DROP policy architectural preparation (all forwarded messages pass through local validation)
- Dual security domain: Android only possesses the device write token (`POST /api/v1/messages`), not the MCP read token

**Scale/Scope**: Personal Android SMS relay; handles bursts of 10-50 SMS/minute without dropping records or blocking the main thread.

---

## Constitution Check

*GATE: All principles from `.specify/memory/constitution.md` evaluated.*

| Principle | Check | Status | Evaluation & Mitigation |
|---|---|---|---|
| **P-01: Local-First & Zero-Infrastructure** | Android client communicates with local or LAN Go server. | **PASSED** | Client communicates directly with standalone `relayx-server` via clear HTTP/HTTPS; no third-party cloud infrastructure. |
| **P-02: Separate Security Domains** | Android only stores and transmits Device Write Token. | **PASSED** | Configuration only stores Device ID and Bearer Token for `POST /api/v1/messages`. MCP tokens are never held on Android. |
| **P-03: Strict Data Minimization & Privacy** | Never log SMS bodies or OTP values to Logcat. | **PASSED** | `RelayLogger` sanitizes all log outputs; raw body text and OTP tokens are strictly omitted from Logcat and error traces. |
| **P-04: Device-Side Pre-Filtering** | Local evaluation before network egress. | **PASSED** | Architecture prepares `FilterEngine` in `domain/`; incoming SMS passes through local evaluation before Room queue entry. |
| **P-05: Durable Delivery & Idempotency** | Room outbox queue; exponential backoff retries. | **PASSED** | Incoming SMS is committed to Room before network transmission; WorkManager retries with 1s, 2s, 5s, 10s, 30s, 60s backoff; unique UUID guarantees server idempotency. |
| **P-06: Event-Driven Agent MCP** | N/A to Android module directly. | **PASSED** | Server-side principle; Android ensures reliable ingestion that triggers server's event broker. |
| **P-07: Complete Mock SMS Parity** | Architectural parity for future mock testing. | **PASSED** | The pipeline `SMS -> Filter -> Room -> Dispatcher -> Network` is decoupled so future mock SMS injectors share the exact same execution path. |
| **P-08: Clean Architecture & Boring Tech** | Clean Android package structure, explicit code. | **PASSED** | Modular layered architecture: `ui/`, `viewmodel/`, `domain/`, `data/local/`, `data/remote/`, `data/receiver/`, `data/worker/`. |
| **P-09: Strict Non-Goals** | Personal relay only; no spam or OS security bypasses. | **PASSED** | Standard broadcast receiver and permissions; no lock screen or device power bypasses attempted. |

---

## Project Structure

### Documentation (this feature)

```text
specs/002-android-gateway-foundation/
├── spec.md              # Feature specification
├── plan.md              # This file (implementation plan)
├── research.md          # Technical research & architectural decisions
├── data-model.md        # Database schema, entities & UI models
├── quickstart.md        # Build, run, and manual verification steps
├── contracts/
│   └── relayx-server-client.md # HTTP client contract with relayx-server
└── checklists/
    └── requirements.md  # Specification quality checklist
```

### Source Code (Android Module)

```text
relayx-android/src/main/
├── AndroidManifest.xml
└── java/com/parsomash/relayx/
    ├── MainActivity.kt                  # Single Activity host with tab navigation
    ├── RelayApplication.kt             # Application class initializing Room & WorkManager
    ├── ui/
    │   ├── theme/                       # Color, Type, Theme (Material 3)
    │   ├── dashboard/
    │   │   └── DashboardScreen.kt       # Live status indicators & message counters
    │   ├── settings/
    │   │   └── SettingsScreen.kt        # Server configuration & Test Connection UI
    │   └── navigation/
    │       └── RelayNavGraph.kt         # Navigation between Dashboard & Settings
    ├── viewmodel/
    │   ├── DashboardViewModel.kt        # Observes queue stats & controls forwarding
    │   └── SettingsViewModel.kt         # Manages configuration & connection test
    ├── domain/
    │   ├── model/
    │   │   ├── QueuedMessage.kt         # Domain model for SMS outbox record
    │   │   ├── GatewayConfig.kt         # Domain model for server & app settings
    │   │   └── GatewayStats.kt          # Aggregated delivery statistics
    │   └── usecase/
    │       ├── IngestSmsUseCase.kt      # Validates and persists incoming SMS
    │       ├── DispatchOutboxUseCase.kt # Sends pending messages via HTTP client
    │       └── TestConnectionUseCase.kt # Probes server /health endpoint
    ├── data/
    │   ├── local/
    │   │   ├── RelayDatabase.kt         # Room database definition
    │   │   ├── OutboxMessageDao.kt      # DAO with queries for PENDING messages & stats
    │   │   ├── OutboxMessageEntity.kt   # Room database table definition
    │   │   └── PreferencesRepository.kt # DataStore repository for settings
    │   ├── remote/
    │   │   ├── RelayServerClient.kt     # OkHttp client for /health and /messages
    │   │   └── dto/
    │   │       ├── HealthResponseDto.kt
    │   │       └── IngestMessageDto.kt
    │   ├── receiver/
    │   │   ├── SmsReceiver.kt           # BroadcastReceiver for SMS_RECEIVED
    │   │   └── BootReceiver.kt          # BroadcastReceiver for BOOT_COMPLETED
    │   └── worker/
    │       └── MessageDispatchWorker.kt # WorkManager worker with backoff retries
    └── util/
        └── RelayLogger.kt               # Logcat wrapper preventing sensitive data leaks
```

---

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| *None* | No constitutional violations identified. | Architecture follows standard Jetpack libraries and Clean Architecture. |
