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

func (r *SQLiteDeviceRepository) List(ctx context.Context) ([]domain.Device, error) {
	query := `SELECT id, name, token_hash, created_at, last_seen_at FROM devices ORDER BY created_at DESC;`
	rows, err := r.db.QueryContext(ctx, query)
	if err != nil {
		return nil, fmt.Errorf("listing devices: %w", err)
	}
	defer rows.Close()

	var devices []domain.Device
	for rows.Next() {
		var d domain.Device
		var lastSeen sql.NullTime
		if err := rows.Scan(&d.ID, &d.Name, &d.TokenHash, &d.CreatedAt, &lastSeen); err != nil {
			return nil, fmt.Errorf("scanning device row: %w", err)
		}
		if lastSeen.Valid {
			d.LastSeenAt = &lastSeen.Time
		}
		devices = append(devices, d)
	}

	if devices == nil {
		devices = []domain.Device{}
	}

	return devices, nil
}

func (r *SQLiteDeviceRepository) Delete(ctx context.Context, id string) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("beginning transaction: %w", err)
	}
	defer tx.Rollback()

	if _, err := tx.ExecContext(ctx, `DELETE FROM messages WHERE device_id = ?;`, id); err != nil {
		return fmt.Errorf("deleting device messages: %w", err)
	}

	if _, err := tx.ExecContext(ctx, `DELETE FROM devices WHERE id = ?;`, id); err != nil {
		return fmt.Errorf("deleting device: %w", err)
	}

	return tx.Commit()
}
