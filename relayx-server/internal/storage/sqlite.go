package storage

import (
	"context"
	"database/sql"
	"fmt"
	"os"
	"path/filepath"
	"time"

	_ "modernc.org/sqlite"
)

// Open initializes and configures a SQLite connection with WAL mode and runs embedded migrations.
func Open(ctx context.Context, dbPath string) (*sql.DB, error) {
	// Ensure parent directory exists
	dir := filepath.Dir(dbPath)
	if dir != "" && dir != "." {
		if err := os.MkdirAll(dir, 0755); err != nil {
			return nil, fmt.Errorf("creating database directory %s: %w", dir, err)
		}
	}

	// Open connection with DSN pragmas
	dsn := fmt.Sprintf("%s?_pragma=busy_timeout(5000)&_pragma=journal_mode(WAL)&_pragma=synchronous(NORMAL)&_pragma=foreign_keys(ON)", dbPath)
	db, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, fmt.Errorf("opening sqlite database at %s: %w", dbPath, err)
	}

	// Set connection pool parameters suitable for SQLite
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)
	db.SetConnMaxLifetime(time.Hour)

	// Verify connectivity
	pingCtx, cancel := context.WithTimeout(ctx, 3*time.Second)
	defer cancel()
	if err := db.PingContext(pingCtx); err != nil {
		_ = db.Close()
		return nil, fmt.Errorf("pinging sqlite database: %w", err)
	}

	// Run embedded migrations
	if err := RunMigrations(ctx, db); err != nil {
		_ = db.Close()
		return nil, fmt.Errorf("running database migrations: %w", err)
	}

	return db, nil
}
