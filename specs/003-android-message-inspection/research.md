# Research: Android Message Inspection & Detail Modal

**Feature**: `specs/003-android-message-inspection` | **Date**: 2026-09-12

---

## 1. Navigation 3 Parameterized Routes

### Decision
Extend `AppRoute` sealed class with `@Serializable data class MessageList(val initialFilter: String = "ALL") : AppRoute()`, registered in `navigationModule` via `navigation<AppRoute.MessageList>`.

### Rationale
- RelayX uses `androidx.navigation3` and `koin-compose-navigation3:4.2.2`.
- Navigation 3 represents routes as serializable `NavKey` objects in `rememberNavBackStack`.
- Parameterizing `MessageList(val initialFilter: String)` allows deep navigation directly from Dashboard metric cards (`Received`, `Forwarded`, `Filtered`, `Failed`) while maintaining back-stack restoration and system back button handling.

### Alternatives Considered
- *Shared ViewModel state without route arguments*: Fragile; loses filter state on process recreation.
- *Single-screen conditional rendering with internal state*: Fails back-stack semantics; pressing Android system back button would exit the app rather than returning to Dashboard.

---

## 2. Compose Material 3 ModalBottomSheet for Message Details

### Decision
Use `androidx.compose.material3.ModalBottomSheet` with `rememberModalBottomSheetState(skipPartiallyExpanded = false)` as a shared, reusable composable `MessageDetailBottomSheet`.

### Rationale
- Android Material 3 provides native touch drag, scrim dimming, predictive back gestures, and accessibility integration.
- `MessageDetailBottomSheet` can be invoked from two independent entry points:
  1. `DashboardScreen`: Tapping the "Last Message Received" card.
  2. `MessageListScreen`: Tapping any historical message item.
- Reusing the same sheet ensures uniform error diagnostics, retry actions, and privacy masking.

### Alternatives Considered
- *Full-screen Activity/Route*: Disconnects user context; inspect-and-dismiss actions should be lightweight and non-disruptive.
- *AlertDialog*: Insufficient vertical space for multi-line error diagnostics, timestamps, UUID copy actions, and formatted SMS content.

---

## 3. Reactive State Flow & Search in MessageListViewModel

### Decision
Introduce `@KoinViewModel class MessageListViewModel(private val outboxDao: OutboxMessageDao, private val workManager: WorkManager)` exposing a unified `StateFlow<MessageListUiState>` generated from `combine(selectedFilterFlow, searchQueryFlow, outboxDao.observeAllMessages())`.

### Rationale
- Room `Flow` emissions ensure instantaneous UI updates whenever an incoming SMS is received or when background `MessageDispatchWorker` transitions a status from `PENDING` to `DELIVERED` or `FAILED`.
- Debouncing search query input (150ms) ensures smooth 60fps typing performance over large outbox databases.

### Alternatives Considered
- *Separate Room queries for every search key*: Redundant SQLite index lookups for small-to-medium personal gateway outbox queues (< 5,000 items); in-memory filtering of the Room flow provides sub-10ms response times.

---

## 4. Privacy-Preserving Payload Masking (Constitution Principle III)

### Decision
Display message payloads and verification OTPs masked by default in `MessageDetailBottomSheet` (e.g. `••••••` or masked preview), providing an explicit eye toggle icon to reveal plain text. Never log unmasked bodies to Logcat during inspection.

### Rationale
- Constitution Principle III states: *"SMS bodies, verification codes, and personal content must never be logged or emitted unmasked into non-ephemeral storage."*
- User-directed inspection on an unlocked screen is permissible under explicit consent, but default view should protect against casual over-the-shoulder viewing.

### Alternatives Considered
- *Complete omission of message body*: Degrades usability; users inspecting a failed message cannot verify whether the message contained the expected payload.
- *Plaintext display by default*: Violates privacy-first principles.

---

## 5. Idempotent Retry Execution

### Decision
Tapping "Retry Delivery" in the bottom sheet calls `OutboxMessageDao.resetForRetry(id)` (updating status to `PENDING`, resetting retry attempt counters or setting backoff to 0) and enqueues an expedited one-time `MessageDispatchWorker`.

### Rationale
- Leverages existing WorkManager infrastructure.
- Idempotency-Key (UUID) is retained, ensuring the companion server deduplicates the retry without creating duplicate rows.
