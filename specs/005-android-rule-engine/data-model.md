# Data Model: Android Rule & Filtering Engine

**Feature**: `specs/005-android-rule-engine`  
**Date**: 2026-09-14  

---

## 1. Domain Entities

### Rule
Represents a user-defined filtering, transformation, or dropping policy.

```kotlin
data class Rule(
    val id: String,                         // UUID string
    val name: String,                       // Human-readable title (e.g., "Chase 2FA")
    val senderPattern: String,              // Match string or regex for sender
    val senderMatchType: SenderMatchType,   // EXACT, PREFIX, CONTAINS, REGEX, ANY
    val contentPattern: String? = null,     // Optional regex or substring filter on body
    val action: RuleAction,                 // FORWARD_RAW, FORWARD_TRANSFORMED, DROP
    val transformPattern: String? = null,   // Optional regex with capture group for extraction
    val priority: Int,                      // Integer rank (lower = higher priority, e.g. 1 > 10)
    val enabled: Boolean = true,            // Toggle state
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

### SenderMatchType
```kotlin
enum class SenderMatchType {
    EXACT,      // Case-insensitive exact string match (e.g. "MYBANK")
    PREFIX,     // Starts with (e.g. "+1800")
    CONTAINS,   // Substring match (e.g. "AUTH")
    REGEX,      // Regular expression match on sender address
    ANY         // Wildcard: matches any sender
}
```

### RuleAction
```kotlin
enum class RuleAction {
    FORWARD_RAW,         // Forward original message body
    FORWARD_TRANSFORMED,   // Forward extracted pattern (e.g. OTP); omit raw body
    DROP                 // Discard locally without network egress
}
```

### RuleEvaluationResult
Result returned by `RuleEngine.evaluate(sender, body)`.

```kotlin
data class RuleEvaluationResult(
    val matchedRule: Rule?,
    val action: RuleAction,
    val transformedBody: String?,
    val isDropped: Boolean,
    val executionLog: String
)
```

---

## 2. Room Database Entity

### Table: `filter_rules`

```kotlin
@Entity(
    tableName = "filter_rules",
    indices = [
        Index(value = ["priority", "enabled"], name = "idx_rules_priority_enabled")
    ]
)
data class RuleEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "sender_pattern")
    val senderPattern: String,

    @ColumnInfo(name = "sender_match_type")
    val senderMatchType: String,

    @ColumnInfo(name = "content_pattern")
    val contentPattern: String? = null,

    @ColumnInfo(name = "action")
    val action: String,

    @ColumnInfo(name = "transform_pattern")
    val transformPattern: String? = null,

    @ColumnInfo(name = "priority")
    val priority: Int,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
```

---

## 3. Relationships & State Integration

```text
Incoming SMS (sender, body)
             │
             ▼
     ┌───────────────┐
     │  RuleEngine   │ <─── Active Rules (sorted by priority ASC, id ASC)
     └───────┬───────┘
             │
     ┌───────┴───────────────────────────────┐
     │                                       │
     ▼ (Matched FORWARD / TRANSFORM)         ▼ (Matched DROP or No Match)
OutboxMessageEntity                     OutboxMessageEntity
  status = PENDING                        status = FILTERED
  transformedBody = <extracted>           transformedBody = null
  (Enqueued to WorkManager)               (NO Network Dispatch)
```

---

## 4. Validation Rules

- **Name**: Must not be blank (1-50 characters).
- **SenderPattern**: Required unless `senderMatchType == ANY`.
- **Regex Patterns**: `contentPattern` and `transformPattern` (if provided) must compile via `Regex(pattern)` without throwing `PatternSyntaxException`.
- **Priority**: Must be a positive integer (`>= 1`).
