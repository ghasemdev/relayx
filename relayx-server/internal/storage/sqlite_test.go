package storage_test

import (
	"context"
	"path/filepath"
	"testing"
	"time"

	"relayx-server/internal/storage"
)

func TestSQLiteAutoCreateAndMigrate(t *testing.T) {
	tempDir := t.TempDir()
	dbPath := filepath.Join(tempDir, "nested", "sub", "test.db")

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	// 1. Open database (should create directories and run migrations)
	db, err := storage.Open(ctx, dbPath)
	if err != nil {
		t.Fatalf("failed to open database: %v", err)
	}
	defer db.Close()

	// 2. Verify schema_migrations has 001_initial.sql
	var appliedCount int
	err = db.QueryRowContext(ctx, "SELECT COUNT(1) FROM schema_migrations WHERE version = '001_initial.sql'").Scan(&appliedCount)
	if err != nil {
		t.Fatalf("failed to query schema_migrations: %v", err)
	}
	if appliedCount != 1 {
		t.Fatalf("expected 1 migration applied, got %d", appliedCount)
	}

	// 3. Verify devices and messages tables exist
	var deviceTableExists int
	err = db.QueryRowContext(ctx, "SELECT COUNT(1) FROM sqlite_master WHERE type='table' AND name='devices'").Scan(&deviceTableExists)
	if err != nil || deviceTableExists != 1 {
		t.Fatalf("devices table not created: %v", err)
	}

	var messageTableExists int
	err = db.QueryRowContext(ctx, "SELECT COUNT(1) FROM sqlite_master WHERE type='table' AND name='messages'").Scan(&messageTableExists)
	if err != nil || messageTableExists != 1 {
		t.Fatalf("messages table not created: %v", err)
	}

	// 4. Test re-opening the same database is idempotent
	db2, err := storage.Open(ctx, dbPath)
	if err != nil {
		t.Fatalf("failed to reopen database: %v", err)
	}
	defer db2.Close()
}
