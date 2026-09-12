# Implementation Plan: Android Message Inspection & Detail Modal

**Branch**: `feature/003-android-message-inspection` | **Date**: 2026-09-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-android-message-inspection/spec.md`

---

## Summary

Expand the `relayx-android` client with interactive diagnostic inspection and message management:
1. Enable direct navigation from Dashboard throughput cards (**Received**, **Forwarded**, **Filtered**, **Failed**) to a dedicated **Message List Screen** pre-filtered by status.
2. Build a full-featured **Message List Screen** with filter tabs, real-time search by sender or message UUID, and status-colored badges.
3. Build a reusable **Message Detail Modal Bottom Sheet** (Compose Material 3) accessible from both the Dashboard "Last Message Received" card and from any item in the Message List screen, featuring privacy-masked payload display, failure diagnostics, and a one-tap retry button.

---

## Technical Context

**Language/Version**: Kotlin 2.4+ (Kotlin 2.4.20) / Android SDK 37 (minSdk 24, compileSdk 37, targetSdk 37)

**Primary Dependencies**:
- UI: Jetpack Compose BOM 2026.08.00, Material 3, Navigation 3 (`koin-compose-navigation3:4.2.2`)
- Dependency Injection: Koin 4.2.2 + Koin Compiler Plugin 1.2.1 (`@Single`, `@KoinViewModel`, `@ComponentScan`)
- Persistence: Android Room (`OutboxMessageDao`, `OutboxMessageEntity`)
- Scheduling: Android WorkManager (`MessageDispatchWorker`)
- Reactive Programming: Kotlinx Coroutines & StateFlow

**Storage**: Local SQLite via Android Room (`relayx_gateway.db`, table `outbox_messages`).

**Testing**:
- Unit Tests: JUnit 4, Kotlinx Coroutines Test, Turbine, Fake DAOs (`rtk ./gradlew test`)
- Android Integration Tests: Compose UI Testing (`rtk ./gradlew connectedCheck`)

**Target Platform**: Android 7.0 (API 24) through Android 15+ (API 37).

**Project Type**: Native Android Mobile Application (`relayx-android`).

**Performance Goals**:
- Navigation from Dashboard card to Message List in < 150ms.
- Modal Bottom Sheet presentation in < 100ms.
- Search query filtering latency < 50ms.

**Constraints**:
- Privacy: Payload and OTP codes MUST be masked by default in the bottom sheet.
- Idempotency: Retries MUST preserve message UUID to prevent duplicate server ingestion.
- Offline-First: All message browsing and searching operates 100% locally from Room SQLite.

---

## Constitution Check

*GATE: Evaluated against `.specify/memory/constitution.md`.*

| Principle | Status | Evaluation & Mitigation |
|:---|:---|:---|
| **P-01: Local-First & Zero-Infrastructure** | **PASSED** | All message inspection, filtering, and search operations execute entirely on-device from Room SQLite without cloud dependencies. |
| **P-02: Separate Security Domains** | **PASSED** | Operates strictly within Android gateway client scope. No MCP credentials or server read keys are required or exposed. |
| **P-03: Strict Data Minimization & Privacy** | **PASSED** | Message bodies and OTP codes are masked by default (`••••••`) in the bottom sheet. Unmasking requires deliberate user action. Zero raw body logs emitted to Logcat during inspection. |
| **P-04: Device-Side Pre-Filtering** | **PASSED** | Accurately visualizes messages dropped or transformed by local rule evaluation under the `FILTERED` tab. |
| **P-05: Durable Delivery & Idempotency** | **PASSED** | Tapping "Retry Delivery" for failed outbox items updates status to `PENDING` using the existing UUID, ensuring server deduplication. |
| **P-08: Clean Architecture & Boring Tech** | **PASSED** | Standard Jetpack Compose Material 3 components (`ModalBottomSheet`, `ScrollableTabRow`, `LazyColumn`), Navigation 3 routes, and Koin 4.2 ViewModels. |

---

## Project Structure

### Documentation (this feature)

```text
specs/003-android-message-inspection/
├── spec.md              # Feature specification
├── plan.md              # This file (implementation plan)
├── research.md          # Technical research & architectural decisions
├── data-model.md        # UI models & Room DAO query extensions
├── quickstart.md        # Build, run, and manual verification steps
├── contracts/
│   └── message-inspection-ui.md # Navigation & modal bottom sheet interaction contract
└── checklists/
    └── requirements.md  # Specification quality checklist
```

### Source Code (`relayx-android`)

```text
relayx-android/src/main/java/com/parsomash/relayx/
├── data/
│   └── local/
│       └── OutboxMessageDao.kt          # New Flow queries: observeAllMessages(), observeLatestMessage(), resetForRetry()
├── domain/
│   ├── model/
│   │   └── Models.kt                    # MessageFilter, MessageListItem, MessageDetail
│   └── usecase/
│       ├── DashboardUseCases.kt         # Expose observeLatestMessageUseCase()
│       └── MessageInspectionUseCases.kt # GetFilteredMessagesUseCase, RetryMessageUseCase
├── ui/
│   ├── navigation/
│   │   └── RelayNavGraph.kt             # Add AppRoute.MessageList(initialFilter) & registration
│   ├── dashboard/
│   │   └── DashboardScreen.kt           # Clickable metric cards & last message card bottom sheet trigger
│   └── messages/
│       ├── MessageListScreen.kt         # New: Filter tabs, search bar, and LazyColumn
│       └── components/
│           ├── MessageItemCard.kt       # New: Message list row component
│           └── MessageDetailBottomSheet.kt # New: Reusable modal bottom sheet with privacy toggle & retry
└── viewmodel/
    ├── DashboardViewModel.kt            # Observe latest message detail & modal presentation state
    └── MessageListViewModel.kt          # New: Search, filter state, and selected message detail state
```

---

## Complexity Tracking

| Issue / Violation | Why Needed | Simpler Alternative Rejected Because |
|:---|:---|:---|
| *None* | Complies with all 9 principles of the RelayX Constitution. | Standard Compose M3 components and Room reactive flows. |
