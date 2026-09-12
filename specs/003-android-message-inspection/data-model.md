# Data Model: Android Message Inspection & Detail Modal

**Feature**: `specs/003-android-message-inspection` | **Date**: 2026-09-12

---

## 1. Domain & UI Entities

### `MessageFilter` (Enum)
Represents the active filter category applied to the Message List screen.

```kotlin
enum class MessageFilter(val titleResId: Int) {
    ALL(R.string.filter_all),
    PENDING(R.string.filter_pending),
    FORWARDED(R.string.filter_forwarded),
    FAILED(R.string.filter_failed),
    FILTERED(R.string.filter_filtered);

    companion object {
        fun fromString(value: String): MessageFilter {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ALL
        }
    }
}
```

---

### `MessageListItem` (UI Presentation Model)
Lightweight immutable model consumed by `LazyColumn` items in `MessageListScreen`.

| Field | Type | Description |
|:---|:---|:---|
| `id` | `String` | Message unique identifier (UUID) |
| `sender` | `String` | Sender phone number or alphanumeric address (e.g. `BANK`, `TWILIO`) |
| `formattedTime` | `String` | Human-readable timestamp (e.g. `12:45 PM` or `Sep 12, 12:45 PM`) |
| `relativeTime` | `String` | Relative time string (e.g. `2m ago`, `Just now`) |
| `status` | `DeliveryStatus` | Delivery state (`PENDING`, `DELIVERED`, `FAILED`, `FILTERED`) |
| `retryCount` | `Int` | Number of dispatch attempts made so far |
| `isFailed` | `Boolean` | Convenience helper for red badge highlight |

---

### `MessageDetail` (UI Modal Model)
Comprehensive model consumed by `MessageDetailBottomSheet`.

| Field | Type | Description |
|:---|:---|:---|
| `id` | `String` | Full UUID |
| `sender` | `String` | Sender identity |
| `rawBody` | `String` | Original SMS message text |
| `transformedBody` | `String?` | Transformed value if rule modified body |
| `receivedAt` | `Long` | Epoch millisecond timestamp |
| `formattedReceivedAt` | `String` | Exact date-time format (`yyyy-MM-dd HH:mm:ss`) |
| `status` | `DeliveryStatus` | Delivery state |
| `retryCount` | `Int` | Attempts count |
| `lastAttemptAt` | `Long?` | Timestamp of latest delivery attempt |
| `errorMessage` | `String?` | Error description from HTTP client or worker if failed |
| `canRetry` | `Boolean` | `true` if status is `FAILED` or `PENDING` |

---

### `MessageListUiState`
State container for `MessageListScreen`.

```kotlin
data class MessageListUiState(
    val messages: List<MessageListItem> = emptyList(),
    val selectedFilter: MessageFilter = MessageFilter.ALL,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val selectedMessageDetail: MessageDetail? = null
)
```

---

## 2. Room DAO Extensions (`OutboxMessageDao`)

Add the following query methods to [`OutboxMessageDao`](relayx-android/src/main/java/com/parsomash/relayx/data/local/OutboxMessageDao.kt):

```kotlin
// 1. Observe all messages in reverse chronological order
@Query("SELECT * FROM outbox_messages ORDER BY received_at DESC")
fun observeAllMessages(): Flow<List<OutboxMessageEntity>>

// 2. Observe latest message for Dashboard card & detail sheet
@Query("SELECT * FROM outbox_messages ORDER BY received_at DESC LIMIT 1")
fun observeLatestMessage(): Flow<OutboxMessageEntity?>

// 3. Reset a failed message to PENDING for immediate retry
@Query("UPDATE outbox_messages SET status = 'PENDING', error_message = null WHERE id = :id")
suspend fun resetForRetry(id: String): Int
```

---

## 3. State Transitions

```text
[ Incoming SMS ] ──> Insert into Room (status: PENDING)
                            │
              ┌─────────────┴─────────────┐
              ▼                           ▼
      (Fast Dispatch)             (Network Outage)
              │                           │
              ▼                           ▼
     Status: DELIVERED             Status: FAILED
                                          │
                                 [ User Taps Retry ]
                                          │
                                          ▼
                                   Status: PENDING
                                          │
                                          ▼
                                   WorkManager Run
```
