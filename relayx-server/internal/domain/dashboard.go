package domain

import "time"

// LogEntry represents an individual structured log event broadcast to web clients.
type LogEntry struct {
	Timestamp  time.Time      `json:"timestamp"`
	Level      string         `json:"level"`      // DEBUG, INFO, WARN, ERROR
	Component  string         `json:"component"`  // e.g. api, storage, hook, logging
	Message    string         `json:"message"`    // Sanitized log message text
	Attributes map[string]any `json:"attributes"` // Redacted key-value pairs
}

// SystemMetrics represents a runtime snapshot of server health and storage metrics.
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

// DatabaseMetrics represents SQLite file size and WAL metrics.
type DatabaseMetrics struct {
	DBSizeBytes  int64  `json:"db_size_bytes"`
	WALSizeBytes int64  `json:"wal_size_bytes"`
	DBPath       string `json:"db_path"`
}

// MessageCounters represents aggregate SMS delivery counters.
type MessageCounters struct {
	TotalReceived  int64 `json:"total_received"`
	TotalForwarded int64 `json:"total_forwarded"`
	TotalFiltered  int64 `json:"total_filtered"`
	TotalFailed    int64 `json:"total_failed"`
}

// TableMetadata contains schema and row count details for a browsable table.
type TableMetadata struct {
	Name       string       `json:"name"`
	RowCount   int64        `json:"row_count"`
	Columns    []ColumnMeta `json:"columns"`
	PrimaryKey string       `json:"primary_key"`
}

// ColumnMeta describes a column in an SQLite table.
type ColumnMeta struct {
	Name     string `json:"name"`
	DataType string `json:"data_type"`
	Nullable bool   `json:"nullable"`
}

// TablePage represents a paginated slice of database rows.
type TablePage struct {
	TableName  string           `json:"table_name"`
	Page       int              `json:"page"`
	PageSize   int              `json:"page_size"`
	TotalRows  int64            `json:"total_rows"`
	TotalPages int              `json:"total_pages"`
	Columns    []string         `json:"columns"`
	Rows       []map[string]any `json:"rows"`
}

// DeviceSummary represents a registered client device for administrative display.
type DeviceSummary struct {
	ID               string     `json:"id"`
	Name             string     `json:"name"`
	TokenFingerprint string     `json:"token_fingerprint"` // First 8 characters of SHA-256 token hash
	CreatedAt        time.Time  `json:"created_at"`
	LastSeenAt       *time.Time `json:"last_seen_at"`
}
