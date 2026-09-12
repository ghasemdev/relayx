# UI Contract: Android Message Inspection & Detail Modal

**Feature**: `specs/003-android-message-inspection` | **Date**: 2026-09-12

---

## 1. Navigation Route Contract

### Route Definition
```kotlin
@Serializable
data class MessageList(val initialFilter: String = "ALL") : AppRoute()
```

### Dashboard Metric Card Navigation Mapping
| Dashboard Card Tapped | Target Route Parameter | Initial Active Filter Tab |
|:---|:---|:---|
| **Received** (Total Count) | `AppRoute.MessageList("ALL")` | `MessageFilter.ALL` |
| **Forwarded** (Delivered Count) | `AppRoute.MessageList("FORWARDED")` | `MessageFilter.FORWARDED` |
| **Failed** (Error Count) | `AppRoute.MessageList("FAILED")` | `MessageFilter.FAILED` |
| **Filtered** (Dropped Count) | `AppRoute.MessageList("FILTERED")` | `MessageFilter.FILTERED` |

---

## 2. Modal Bottom Sheet Contract

### Composable Signature
```kotlin
@Composable
fun MessageDetailBottomSheet(
    detail: MessageDetail,
    onDismissRequest: () -> Unit,
    onRetryClick: (messageId: String) -> Unit,
    modifier: Modifier = Modifier
)
```

### Display Elements & Privacy Guarantees
1. **Header**:
   - Title: "Message Details"
   - Status Chip: `PENDING` (Orange/Yellow), `DELIVERED` (Green), `FAILED` (Red), `FILTERED` (Gray/Purple)
2. **Metadata Rows**:
   - `Message ID`: Truncated or full UUID with inline copy button (`LocalClipboardManager`)
   - `Sender`: Clean address (e.g. `BANK_AUTH` or `+15551234567`)
   - `Received At`: Absolute timestamp (`yyyy-MM-dd HH:mm:ss`) and relative elapsed time
   - `Attempts`: `${detail.retryCount} / 5`
3. **Payload Section**:
   - When masked (default): Displays `••••••••••••` with an Eye icon toggle button.
   - When unmasked (toggled): Displays raw SMS text or transformed OTP.
4. **Failure Diagnostics**:
   - Only shown when `detail.errorMessage` is not null.
   - Highlighted in Material 3 `errorContainer` card.
5. **Action Bar**:
   - "Retry Delivery" button (visible and enabled when `detail.canRetry == true`).
   - "Close" or swipe down to dismiss.

---

## 3. List Screen Layout & Interaction Contract

### Composable Signature
```kotlin
@Composable
fun MessageListScreen(
    viewModel: MessageListViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
)
```

### Interaction Behaviors
- **TopAppBar**: Title "Message History", navigation icon (Back arrow) invoking `onNavigateBack()`.
- **Search Bar**: `OutlinedTextField` or Material 3 `SearchBar` pinned below TopAppBar. Real-time debounce filtering on text changes.
- **Scrollable Filter Tabs**: Horizontal `ScrollableTabRow` with badges for matching item counts.
- **Item Tap**: Tapping any card sets `selectedMessageDetail` in ViewModel, presenting the `MessageDetailBottomSheet`.
- **Empty State**: Renders informative icon and text when list contains 0 matching records.
