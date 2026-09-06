package storage

import (
	"context"
	"database/sql"
	"fmt"
	"io/fs"
	"log/slog"
	"sort"
	"strings"

	"relayx-server/migrations"
)

// RunMigrations executes all unapplied embedded SQL migrations in lexicographical order.
func RunMigrations(ctx context.Context, db *sql.DB) error {
	// 1. Ensure schema_migrations table exists
	createTableSQL := `
	CREATE TABLE IF NOT EXISTS schema_migrations (
		version TEXT PRIMARY KEY,
		applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
	);`
	if _, err := db.ExecContext(ctx, createTableSQL); err != nil {
		return fmt.Errorf("creating schema_migrations table: %w", err)
	}

	// 2. Discover migration files
	entries, err := fs.ReadDir(migrations.FS, ".")
	if err != nil {
		return fmt.Errorf("reading embedded migrations: %w", err)
	}

	var filenames []string
	for _, entry := range entries {
		if !entry.IsDir() && strings.HasSuffix(entry.Name(), ".sql") {
			filenames = append(filenames, entry.Name())
		}
	}
	sort.Strings(filenames)

	// 3. Apply pending migrations
	for _, filename := range filenames {
		var applied int
		checkQuery := `SELECT COUNT(1) FROM schema_migrations WHERE version = ?;`
		if err := db.QueryRowContext(ctx, checkQuery, filename).Scan(&applied); err != nil {
			return fmt.Errorf("checking migration status for %s: %w", filename, err)
		}

		if applied > 0 {
			continue // Already applied
		}

		content, err := fs.ReadFile(migrations.FS, filename)
		if err != nil {
			return fmt.Errorf("reading migration file %s: %w", filename, err)
		}

		tx, err := db.BeginTx(ctx, nil)
		if err != nil {
			return fmt.Errorf("beginning transaction for %s: %w", filename, err)
		}

		if _, err := tx.ExecContext(ctx, string(content)); err != nil {
			_ = tx.Rollback()
			return fmt.Errorf("executing migration %s: %w", filename, err)
		}

		recordSQL := `INSERT INTO schema_migrations (version, applied_at) VALUES (?, CURRENT_TIMESTAMP);`
		if _, err := tx.ExecContext(ctx, recordSQL, filename); err != nil {
			_ = tx.Rollback()
			return fmt.Errorf("recording migration %s: %w", filename, err)
		}

		if err := tx.Commit(); err != nil {
			return fmt.Errorf("committing migration %s: %w", filename, err)
		}

		slog.Info("applied database migration", "version", filename)
	}

	return nil
}
