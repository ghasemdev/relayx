# Data Model: Server Web Dashboard & Live Observability

**Feature**: `specs/004-server-web-dashboard`  
**Date**: 2026-09-13  
**Status**: Complete  

---

## 1. Domain Entities

### `LogEntry`
Represents an individual structured log event broadcast over SSE to the web dashboard:
```go
type LogEntry struct {
    Timestamp  time.Time              `json:"timestamp"`
    Level      string                 `json:"level"`      // "DEBUG", "INFO", "WARN", "ERROR"
    Component  string                 `json:"component"`  // e.g. "api", "storage", "hook", "mcp"
    Message    string                 `json:"message"`    // Sanitized log message text
    Attributes map[string]interface{} `json:"attributes"` // Redacted key-value pairs
}
```

### `SystemMetrics`
Represents a real-time operational snapshot of the server process and SQLite storage:
```go
type SystemMetrics struct {
    UptimeSeconds   int64           `json:"uptime_seconds"`
    Version         string          `json:"version"`
    GoVersion       string          `json:"go_version"`
    NumGoroutine    int             `json:"num_goroutine"`
    AllocBytes      uint64          `json:"alloc_bytes"`
    TotalAllocBytes uint64          `json:"total_alloc_bytes"`
    SysBytes        uint64          `json:"sys_bytes"`
    Database        DatabaseMetrics `json:"database"`
    MessageCounters MessageCounters `json:"message_counters"`
}

type DatabaseMetrics struct {
    DBSizeBytes  int64  `json:"db_size_bytes"`
    WALSizeBytes int64  `json:"wal_size_bytes"`
    DBPath       string `json:"db_path"`
}

type MessageCounters struct {
    TotalReceived  int64 `json:"total_received"`
    TotalForwarded int64 `json:"total_forwarded"`
    TotalFiltered  int64 `json:"total_filtered"`
    TotalFailed    int64 `json:"total_failed"`
}
```

### `TableMetadata`
Represents metadata for a browsable SQLite table:
```go
type TableMetadata struct {
    Name        string       `json:"name"`
    RowCount    int64        `json:"row_count"`
    Columns     []ColumnMeta `json:"columns"`
    PrimaryKey  string       `json:"primary_key"`
}

type ColumnMeta struct {
    Name     string `json:"name"`
    DataType string `json:"data_type"`
    Nullable bool   `json:"nullable"`
}
```

### `TablePage`
Represents a paginated slice of database rows returned to the web browser:
```go
type TablePage struct {
    TableName  string                   `json:"table_name"`
    Page       int                      `json:"page"`
    PageSize   int                      `json:"page_size"`
    TotalRows  int64                    `json:"total_rows"`
    TotalPages int                      `json:"total_pages"`
    Columns    []string                 `json:"columns"`
    Rows       []map[string]interface{} `json:"rows"`
}
```

### `DeviceSummary`
Represents a registered client device displayed in the admin security view:
```go
type DeviceSummary struct {
    ID              string     `json:"id"`
    Name            string     `json:"name"`
    TokenFingerprint string    `json:"token_fingerprint"` // First 8 chars of SHA-256 hash
    CreatedAt       time.Time  `json:"created_at"`
    LastSeenAt      *time.Time `json:"last_seen_at"`
}
```

---

## 2. Validation & Security Rules

1. **Table Name Allowlist**: Only `messages`, `devices`, and `schema_migrations` may be queried. Any other table name immediately rejects with HTTP 400 Bad Request.
2. **Pagination Bounds**: `page >= 1`, `1 <= page_size <= 100` (default: 25).
3. **Sort Column Validation**: `sort_by` must match an existing column name returned by `PRAGMA table_info`. Default to `created_at` DESC or primary key DESC.
4. **Sort Order Validation**: Must be strictly `ASC` or `DESC` (case-insensitive).
5. **Log Buffer Sizing**: Log ring buffer capped at 500 entries. Subscriber channel capacity capped at 100 entries. Drops oldest on buffer overflow without blocking.
