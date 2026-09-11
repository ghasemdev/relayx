# Data Model: Android Gateway Foundation

**Feature**: `specs/002-android-gateway-foundation`
**Date**: 2026-09-11

---

## 1. Room Database Entities

### Entity: `OutboxMessageEntity`
- **Table Name**: `outbox_messages`
- **Description**: Represents an incoming SMS message queued for delivery to the RelayX server.

| Column | Type | Constraints | Description |
|:---|:---|:---|:---|
| `id` | `TEXT` | `PRIMARY KEY NOT NULL` | Globally unique message UUID (matches server `message_id`). |
| `sender` | `TEXT` | `NOT NULL` | Originating phone number or alphanumeric sender tag. |
| `raw_body` | `TEXT` | `NOT NULL` | Complete text of the SMS message. |
| `transformed_body` | `TEXT` | `NULL` | Extracted content (e.g. OTP) if rule applied; null for raw messages. |
| `received_at` | `INTEGER` | `NOT NULL` | Epoch timestamp in milliseconds when the SMS was received. |
| `status` | `TEXT` | `NOT NULL` | Delivery lifecycle state: `PENDING`, `SENDING`, `DELIVERED`, `FAILED`. |
| `retry_count` | `INTEGER` | `NOT NULL DEFAULT 0` | Number of failed delivery attempts. |
| `last_attempt_at` | `INTEGER` | `NULL` | Epoch timestamp in milliseconds of the most recent delivery attempt. |
| `error_message` | `TEXT` | `NULL` | Sanitized diagnostic error from the most recent failed dispatch. |

#### Indexes:
- `index_outbox_status_received_at`: `CREATE INDEX idx_outbox_status ON outbox_messages(status, received_at ASC)` (optimizes queue polling and FIFO dispatch).

#### Lifecycle State Transitions:
```text
[SMS Received] 
      │
      ▼
   PENDING  <──────────────┐ (Network error / retry backoff)
      │                    │
      ▼ (Dispatch Start)   │
   SENDING  ───────────────┤
      │                    │
      ├────────────────────┘
      ▼ (HTTP 200/201 ACK)
  DELIVERED (Terminal)
```

---

## 2. Preference Entities (DataStore)

### Entity: `GatewayConfiguration`
- **Store**: `androidx.datastore.preferences.core` (`gateway_preferences`)
- **Description**: User and operational gateway settings.

| Key | Type | Default | Description |
|:---|:---|:---|:---|
| `server_host` | `String` | `"10.0.2.2"` | IP address or domain of the RelayX server. |
| `server_port` | `Int` | `8080` | Port number of the server. |
| `use_https` | `Boolean` | `false` | When true, connects via `https://`; otherwise `http://`. |
| `device_id` | `String` | Auto-generated UUID | Unique client identifier registered with the server. |
| `bearer_token` | `String` | `""` | Authentication token matching server device token. |
| `forwarding_enabled` | `Boolean` | `false` | Master toggle to enable/disable SMS interception & dispatch. |

---

## 3. UI Presentation State

### State: `DashboardUiState`
- **Description**: State exposed by `DashboardViewModel` to the Compose UI.

```kotlin
data class DashboardUiState(
    val forwardingEnabled: Boolean = false,
    val serverStatus: ServerConnectionStatus = ServerConnectionStatus.Disconnected,
    val activeRulesCount: Int = 0,
    val lastMessageTimestamp: Long? = null,
    val stats: GatewayCounters = GatewayCounters(),
    val missingPermissions: List<String> = emptyList()
)

data class GatewayCounters(
    val received: Int = 0,
    val forwarded: Int = 0,
    val filtered: Int = 0,
    val failed: Int = 0
)

sealed interface ServerConnectionStatus {
    object Unconfigured : ServerConnectionStatus
    object Disconnected : ServerConnectionStatus
    object Connecting : ServerConnectionStatus
    data class Connected(val latencyMs: Long) : ServerConnectionStatus
    data class Error(val message: String) : ServerConnectionStatus
}
```

### State: `SettingsUiState`
- **Description**: State exposed by `SettingsViewModel` for server configuration.

```kotlin
data class SettingsUiState(
    val host: String = "",
    val port: String = "8080",
    val useHttps: Boolean = false,
    val deviceId: String = "",
    val bearerToken: String = "",
    val isTestingConnection: Boolean = false,
    val testResult: ConnectionTestResult? = null
)

sealed interface ConnectionTestResult {
    data class Success(val latencyMs: Long, val serverVersion: String) : ConnectionTestResult
    data class Failure(val errorMessage: String) : ConnectionTestResult
}
```
