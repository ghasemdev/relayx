package storage

import (
	"context"
	"database/sql"
	"fmt"
	"os"
	"strings"

	"relayx-server/internal/domain"
)

var allowedTables = map[string]bool{
	"messages":          true,
	"devices":           true,
	"schema_migrations": true,
}

// IsTableAllowed returns true if the specified table is in the allowlist.
func IsTableAllowed(tableName string) bool {
	return allowedTables[strings.ToLower(tableName)]
}

// GetDatabaseStats returns file size and WAL metrics for the SQLite database.
func GetDatabaseStats(dbPath string) domain.DatabaseMetrics {
	metrics := domain.DatabaseMetrics{
		DBPath: dbPath,
	}

	if fi, err := os.Stat(dbPath); err == nil {
		metrics.DBSizeBytes = fi.Size()
	}

	walPath := dbPath + "-wal"
	if fi, err := os.Stat(walPath); err == nil {
		metrics.WALSizeBytes = fi.Size()
	}

	return metrics
}

// GetMessageCounts retrieves aggregate SMS status counters from SQLite.
func GetMessageCounts(ctx context.Context, db *sql.DB) (domain.MessageCounters, error) {
	var counts domain.MessageCounters

	query := `
		SELECT 
			COUNT(*) as total,
			COALESCE(SUM(CASE WHEN status = 'FORWARDED' THEN 1 ELSE 0 END), 0) as forwarded,
			COALESCE(SUM(CASE WHEN status = 'FILTERED' THEN 1 ELSE 0 END), 0) as filtered,
			COALESCE(SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END), 0) as failed
		FROM messages
	`

	row := db.QueryRowContext(ctx, query)
	if err := row.Scan(&counts.TotalReceived, &counts.TotalForwarded, &counts.TotalFiltered, &counts.TotalFailed); err != nil {
		// Return empty counts if table doesn't exist yet
		return counts, nil
	}

	return counts, nil
}

// ListTables returns metadata for all allowed browsable tables.
func ListTables(ctx context.Context, db *sql.DB) ([]domain.TableMetadata, error) {
	tables := []string{"messages", "devices", "schema_migrations"}
	var result []domain.TableMetadata

	for _, name := range tables {
		meta, err := GetTableMetadata(ctx, db, name)
		if err == nil && meta != nil {
			result = append(result, *meta)
		}
	}

	return result, nil
}

// GetTableMetadata retrieves column definitions, primary key, and row count for a table.
func GetTableMetadata(ctx context.Context, db *sql.DB, tableName string) (*domain.TableMetadata, error) {
	tableLower := strings.ToLower(tableName)
	if !IsTableAllowed(tableLower) {
		return nil, fmt.Errorf("table %q is not browsable", tableName)
	}

	// Fetch column info using PRAGMA
	rows, err := db.QueryContext(ctx, fmt.Sprintf("PRAGMA table_info(%s)", tableLower))
	if err != nil {
		return nil, fmt.Errorf("querying table info for %s: %w", tableLower, err)
	}
	defer rows.Close()

	var columns []domain.ColumnMeta
	var primaryKey string

	for rows.Next() {
		var cid int
		var name, dataType string
		var notNull, pk int
		var dfltValue sql.NullString

		if err := rows.Scan(&cid, &name, &dataType, &notNull, &dfltValue, &pk); err != nil {
			return nil, fmt.Errorf("scanning column info: %w", err)
		}

		columns = append(columns, domain.ColumnMeta{
			Name:     name,
			DataType: dataType,
			Nullable: notNull == 0,
		})

		if pk > 0 && primaryKey == "" {
			primaryKey = name
		}
	}

	if len(columns) == 0 {
		return nil, fmt.Errorf("table %q not found or has no columns", tableLower)
	}

	// Fetch row count
	var rowCount int64
	countQuery := fmt.Sprintf("SELECT COUNT(*) FROM %s", tableLower)
	_ = db.QueryRowContext(ctx, countQuery).Scan(&rowCount)

	return &domain.TableMetadata{
		Name:       tableLower,
		RowCount:   rowCount,
		Columns:    columns,
		PrimaryKey: primaryKey,
	}, nil
}

// QueryTable executes a paginated, sorted query against an allowed table.
func QueryTable(ctx context.Context, db *sql.DB, tableName string, page, pageSize int, sortBy, sortOrder string, status, sender, search string) (*domain.TablePage, error) {
	tableLower := strings.ToLower(tableName)
	if !IsTableAllowed(tableLower) {
		return nil, fmt.Errorf("table %q is not browsable", tableName)
	}

	meta, err := GetTableMetadata(ctx, db, tableLower)
	if err != nil {
		return nil, err
	}

	// Bounds checks
	if page < 1 {
		page = 1
	}
	if pageSize < 1 || pageSize > 100 {
		pageSize = 25
	}

	// Validate sort column
	validCol := false
	for _, c := range meta.Columns {
		if strings.EqualFold(c.Name, sortBy) {
			sortBy = c.Name
			validCol = true
			break
		}
	}
	if !validCol {
		if meta.PrimaryKey != "" {
			sortBy = meta.PrimaryKey
		} else if len(meta.Columns) > 0 {
			sortBy = meta.Columns[0].Name
		}
	}

	// Validate sort order
	if !strings.EqualFold(sortOrder, "ASC") {
		sortOrder = "DESC"
	} else {
		sortOrder = "ASC"
	}

	// Build WHERE conditions
	var whereClauses []string
	var args []any

	if status != "" {
		for _, c := range meta.Columns {
			if c.Name == "status" {
				whereClauses = append(whereClauses, "status = ?")
				args = append(args, status)
				break
			}
		}
	}

	if sender != "" {
		for _, c := range meta.Columns {
			if c.Name == "sender" {
				whereClauses = append(whereClauses, "sender LIKE ?")
				args = append(args, "%"+sender+"%")
				break
			}
		}
	}

	if search != "" {
		var searchClauses []string
		for _, c := range meta.Columns {
			if strings.EqualFold(c.DataType, "TEXT") || strings.EqualFold(c.DataType, "VARCHAR") {
				searchClauses = append(searchClauses, fmt.Sprintf("%s LIKE ?", c.Name))
				args = append(args, "%"+search+"%")
			}
		}
		if len(searchClauses) > 0 {
			whereClauses = append(whereClauses, "("+strings.Join(searchClauses, " OR ")+")")
		}
	}

	whereSQL := ""
	if len(whereClauses) > 0 {
		whereSQL = " WHERE " + strings.Join(whereClauses, " AND ")
	}

	// Total rows matching filter
	countQuery := fmt.Sprintf("SELECT COUNT(*) FROM %s%s", tableLower, whereSQL)
	var totalRows int64
	if err := db.QueryRowContext(ctx, countQuery, args...).Scan(&totalRows); err != nil {
		return nil, fmt.Errorf("counting rows: %w", err)
	}

	totalPages := int((totalRows + int64(pageSize) - 1) / int64(pageSize))
	if totalPages == 0 {
		totalPages = 1
	}

	// Paging query
	offset := (page - 1) * pageSize
	dataQuery := fmt.Sprintf("SELECT * FROM %s%s ORDER BY %s %s LIMIT %d OFFSET %d",
		tableLower, whereSQL, sortBy, sortOrder, pageSize, offset)

	dataRows, err := db.QueryContext(ctx, dataQuery, args...)
	if err != nil {
		return nil, fmt.Errorf("executing data query: %w", err)
	}
	defer dataRows.Close()

	colNames, err := dataRows.Columns()
	if err != nil {
		return nil, fmt.Errorf("retrieving column names: %w", err)
	}

	var rows []map[string]any
	for dataRows.Next() {
		colVals := make([]any, len(colNames))
		colValPtrs := make([]any, len(colNames))
		for i := range colVals {
			colValPtrs[i] = &colVals[i]
		}

		if err := dataRows.Scan(colValPtrs...); err != nil {
			return nil, fmt.Errorf("scanning row values: %w", err)
		}

		rowMap := make(map[string]any)
		for i, colName := range colNames {
			val := colVals[i]
			if b, ok := val.([]byte); ok {
				rowMap[colName] = string(b)
			} else {
				rowMap[colName] = val
			}
		}
		rows = append(rows, rowMap)
	}

	if rows == nil {
		rows = []map[string]any{}
	}

	return &domain.TablePage{
		TableName:  tableLower,
		Page:       page,
		PageSize:   pageSize,
		TotalRows:  totalRows,
		TotalPages: totalPages,
		Columns:    colNames,
		Rows:       rows,
	}, nil
}
