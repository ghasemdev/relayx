package storage

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"relayx-server/internal/domain"
)

type SQLiteDeviceRepository struct {
	db *sql.DB
}

func NewDeviceRepository(db *sql.DB) *SQLiteDeviceRepository {
	return &SQLiteDeviceRepository{db: db}
}

func (r *SQLiteDeviceRepository) GetByTokenHash(ctx context.Context, tokenHash string) (*domain.Device, error) {
	query := `SELECT id, name, token_hash, created_at, last_seen_at FROM devices WHERE token_hash = ? LIMIT 1;`
	row := r.db.QueryRowContext(ctx, query, tokenHash)

	var d domain.Device
	var lastSeen sql.NullTime
	if err := row.Scan(&d.ID, &d.Name, &d.TokenHash, &d.CreatedAt, &lastSeen); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil // Not found
		}
		return nil, fmt.Errorf("querying device by token hash: %w", err)
	}

	if lastSeen.Valid {
		d.LastSeenAt = &lastSeen.Time
	}

	return &d, nil
}

func (r *SQLiteDeviceRepository) GetByID(ctx context.Context, id string) (*domain.Device, error) {
	query := `SELECT id, name, token_hash, created_at, last_seen_at FROM devices WHERE id = ? LIMIT 1;`
	row := r.db.QueryRowContext(ctx, query, id)

	var d domain.Device
	var lastSeen sql.NullTime
	if err := row.Scan(&d.ID, &d.Name, &d.TokenHash, &d.CreatedAt, &lastSeen); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, fmt.Errorf("querying device by id: %w", err)
	}

	if lastSeen.Valid {
		d.LastSeenAt = &lastSeen.Time
	}

	return &d, nil
}

func (r *SQLiteDeviceRepository) Upsert(ctx context.Context, d *domain.Device) error {
	query := `
	INSERT INTO devices (id, name, token_hash, created_at, last_seen_at)
	VALUES (?, ?, ?, ?, ?)
	ON CONFLICT(token_hash) DO UPDATE SET
		name = excluded.name,
		last_seen_at = excluded.last_seen_at;`

	_, err := r.db.ExecContext(ctx, query, d.ID, d.Name, d.TokenHash, d.CreatedAt, d.LastSeenAt)
	if err != nil {
		return fmt.Errorf("upserting device: %w", err)
	}
	return nil
}

func (r *SQLiteDeviceRepository) UpdateLastSeen(ctx context.Context, id string, lastSeen time.Time) error {
	query := `UPDATE devices SET last_seen_at = ? WHERE id = ?;`
	_, err := r.db.ExecContext(ctx, query, lastSeen, id)
	if err != nil {
		return fmt.Errorf("updating device last seen: %w", err)
	}
	return nil
}
