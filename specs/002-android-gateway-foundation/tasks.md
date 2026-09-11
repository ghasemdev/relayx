# Tasks: Android Gateway Foundation

**Feature**: `specs/002-android-gateway-foundation`
**Date**: 2026-09-11
**Status**: Ready for Implementation
**Dependencies Diagram**: [task-dependencies.md](task-dependencies.md)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project build configuration, dependencies, and manifest declarations.

- [ ] T001 Configure Room, WorkManager, OkHttp, Kotlinx Serialization, and DataStore dependencies in `gradle/libs.versions.toml`
- [ ] T002 Apply plugins and add dependencies in `relayx-android/build.gradle.kts`
- [ ] T003 Configure Android application permissions (`RECEIVE_SMS`, `READ_SMS`, `INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`) in `relayx-android/src/main/AndroidManifest.xml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core data layer, database persistence, domain models, and privacy utilities.

- [ ] T004 Implement zero-sensitive logging utility `RelayLogger` in `relayx-android/src/main/java/com/parsomash/relayx/util/RelayLogger.kt`
- [ ] T005 [P] Define core domain models (`GatewayConfig`, `QueuedMessage`, `GatewayStats`) in `relayx-android/src/main/java/com/parsomash/relayx/domain/model/Models.kt`
- [ ] T006 [P] Implement DataStore `PreferencesRepository` for server configuration and flags in `relayx-android/src/main/java/com/parsomash/relayx/data/local/PreferencesRepository.kt`
- [ ] T007 [P] Define Room database entity `OutboxMessageEntity` in `relayx-android/src/main/java/com/parsomash/relayx/data/local/OutboxMessageEntity.kt`
- [ ] T008 Implement `OutboxMessageDao` with queue polling, status transitions, and message counts in `relayx-android/src/main/java/com/parsomash/relayx/data/local/OutboxMessageDao.kt`
- [ ] T009 Implement `RelayDatabase` Room database class in `relayx-android/src/main/java/com/parsomash/relayx/data/local/RelayDatabase.kt`
- [ ] T010 Implement `RelayApplication` initializing database and repository singletons in `relayx-android/src/main/java/com/parsomash/relayx/RelayApplication.kt`

**Checkpoint**: Core persistence and configuration foundation ready. User stories can proceed.

---

## Phase 3: User Story 2 - Server Connection Configuration & Validation (Priority: P1)

**Goal**: Allow user to configure destination server parameters and verify reachability with one tap.

**Independent Test**: Enter server address and port in Settings, tap "Test Connection", and verify successful HTTP response and latency badge.

- [ ] T011 [P] [US2] Create network DTOs (`HealthResponseDto`, `IngestMessageDto`) in `relayx-android/src/main/java/com/parsomash/relayx/data/remote/dto/NetworkDtos.kt`
- [ ] T012 [US2] Implement `RelayServerClient` OkHttp client with Bearer auth and `/health` probe in `relayx-android/src/main/java/com/parsomash/relayx/data/remote/RelayServerClient.kt`
- [ ] T013 [US2] Implement `TestConnectionUseCase` in `relayx-android/src/main/java/com/parsomash/relayx/domain/usecase/TestConnectionUseCase.kt`
- [ ] T014 [US2] Implement `SettingsViewModel` managing configuration inputs and connection probe state in `relayx-android/src/main/java/com/parsomash/relayx/viewmodel/SettingsViewModel.kt`
- [ ] T015 [US2] Implement Material 3 `SettingsScreen` with input fields and connection test action in `relayx-android/src/main/java/com/parsomash/relayx/ui/settings/SettingsScreen.kt`

**Checkpoint**: Server configuration and connection testing functional independently.

---

## Phase 4: User Story 1 - Diagnostic Dashboard & Forwarding Control (Priority: P1) 🎯 MVP

**Goal**: Display live status indicators, operational toggle, and live message counters.

**Independent Test**: Launch dashboard, toggle forwarding state, and confirm UI reflects active listening and counter metrics.

- [ ] T016 [US1] Implement `GetGatewayStatsUseCase` and `ToggleForwardingUseCase` in `relayx-android/src/main/java/com/parsomash/relayx/domain/usecase/DashboardUseCases.kt`
- [ ] T017 [US1] Implement `DashboardViewModel` observing queue statistics and forwarding state in `relayx-android/src/main/java/com/parsomash/relayx/viewmodel/DashboardViewModel.kt`
- [ ] T018 [US1] Implement Material 3 `DashboardScreen` displaying status badges, master switch, and live counter cards in `relayx-android/src/main/java/com/parsomash/relayx/ui/dashboard/DashboardScreen.kt`
- [ ] T019 [US1] Implement navigation bar and host layout in `relayx-android/src/main/java/com/parsomash/relayx/ui/navigation/RelayNavGraph.kt` and integrate into `relayx-android/src/main/java/com/parsomash/relayx/MainActivity.kt`

**Checkpoint**: Diagnostic dashboard and settings navigation fully functional.

---

## Phase 5: User Story 3 - Incoming SMS Reception & Durable Offline Storage (Priority: P1)

**Goal**: Intercept incoming SMS PDUs and immediately commit to Room outbox before network transmission.

**Independent Test**: Send SMS to device via `adb emu sms send` while offline, verify message persists in Room with status `PENDING` and counter increments.

- [ ] T020 [US3] Implement `IngestSmsUseCase` creating unique message UUID and saving to Room outbox in `relayx-android/src/main/java/com/parsomash/relayx/domain/usecase/IngestSmsUseCase.kt`
- [ ] T021 [US3] Implement `SmsReceiver` BroadcastReceiver assembling multipart SMS PDUs and executing `IngestSmsUseCase` in `relayx-android/src/main/java/com/parsomash/relayx/data/receiver/SmsReceiver.kt`
- [ ] T022 [US3] Implement runtime permission request flow for `RECEIVE_SMS` in `relayx-android/src/main/java/com/parsomash/relayx/ui/dashboard/DashboardScreen.kt`

**Checkpoint**: SMS reception and local outbox queue persistence complete and verifiable offline.

---

## Phase 6: User Story 4 - Reliable Forward Dispatch with Exponential Backoff (Priority: P2)

**Goal**: Asynchronously transmit pending outbox messages to `POST /api/v1/messages` with exponential backoff retries.

**Independent Test**: Queue a message with server stopped; observe retries with escalating delays (1s, 2s, 5s, 10s, 30s, 60s); start server and confirm status updates to `DELIVERED`.

- [ ] T023 [US4] Implement `DispatchOutboxUseCase` orchestrating network submission and updating message delivery state in `relayx-android/src/main/java/com/parsomash/relayx/domain/usecase/DispatchOutboxUseCase.kt`
- [ ] T024 [US4] Implement `MessageDispatchWorker` CoroutineWorker with exponential backoff and network constraints in `relayx-android/src/main/java/com/parsomash/relayx/data/worker/MessageDispatchWorker.kt`
- [ ] T025 [US4] Trigger `MessageDispatchWorker` from `SmsReceiver` and `DashboardViewModel` on new message arrival

**Checkpoint**: Full end-to-end forward pipeline with retry resilience complete.

---

## Phase 7: User Story 5 - Background Continuity Across Sleep & Device Reboot (Priority: P2)

**Goal**: Ensure gateway automatically resumes monitoring and queue processing after device restart and during screen-off sleep.

**Independent Test**: Simulate reboot (`adb shell am broadcast -a android.intent.action.BOOT_COMPLETED`), verify `BootReceiver` triggers queue draining.

- [ ] T026 [US5] Implement `BootReceiver` listening for `BOOT_COMPLETED` and scheduling `MessageDispatchWorker` in `relayx-android/src/main/java/com/parsomash/relayx/data/receiver/BootReceiver.kt`
- [ ] T027 [US5] Register `BootReceiver` in `relayx-android/src/main/AndroidManifest.xml` with `RECEIVE_BOOT_COMPLETED`

**Checkpoint**: System reboot survival verified.

---

## Phase 8: User Story 6 - Strict Privacy & Log Masking (Priority: P3)

**Goal**: Guarantee zero leakage of SMS message bodies or OTP verification codes in Logcat or system traces.

**Independent Test**: Inspect Logcat output during SMS reception and forward dispatch to ensure no message bodies or OTP codes appear.

- [ ] T028 [US6] Audit all log calls in `SmsReceiver`, `MessageDispatchWorker`, and `RelayServerClient` to enforce `RelayLogger` metadata-only logging
- [ ] T029 [US6] Implement automated test in `relayx-android/src/test/java/com/parsomash/relayx/util/RelayLoggerTest.kt` verifying that message bodies and OTP tokens are never outputted

**Checkpoint**: Privacy guarantees verified with automated assertions.

---

## Phase 9: Polish & Quality Gates

**Purpose**: Cross-cutting unit testing, code quality, and APK verification.

- [ ] T030 [P] Implement unit tests for `PreferencesRepository` in `relayx-android/src/test/java/com/parsomash/relayx/data/local/PreferencesRepositoryTest.kt`
- [ ] T031 [P] Implement unit tests for `RelayServerClient` in `relayx-android/src/test/java/com/parsomash/relayx/data/remote/RelayServerClientTest.kt`
- [ ] T032 Run full verification suite (`rtk ./gradlew test` and `rtk ./gradlew assembleDebug`)

---

## Dependencies & Execution Order

> See [task-dependencies.md](task-dependencies.md) for the complete 12-wave Mermaid directed acyclic graph (DAG) and critical path analysis.

```text
Phase 1 (Setup) ──> Phase 2 (Foundational)
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
   Phase 3 (US2:     Phase 4 (US1:     Phase 5 (US3:
   Settings & Ping)  Dashboard MVP)    SMS Reception & Queue)
         │                 │                 │
         └─────────────────┼─────────────────┘
                           ▼
                     Phase 6 (US4: Dispatch & Backoff)
                           │
                           ▼
                     Phase 7 (US5: Boot Continuity)
                           │
                           ▼
                     Phase 8 (US6: Privacy & Log Masking)
                           │
                           ▼
                     Phase 9 (Polish & Quality Gates)
```

### Parallel Opportunities

- **Phase 2**: T005, T006, and T007 can be developed concurrently in parallel.
- **Phase 3**: T011 network DTOs can run in parallel with settings UI scaffolding.
- **Phase 9**: T030 and T031 unit tests can be developed in parallel across local and remote data layers.
