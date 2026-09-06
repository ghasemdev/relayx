package storage

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"strings"

	"relayx-server/internal/domain"
)

type SQLiteMessageRepository struct {
	db *sql.DB
}

func NewMessageRepository(db *sql.DB) *SQLiteMessageRepository {
	return &SQLiteMessageRepository{db: db}
}

func (r *SQLiteMessageRepository) Create(ctx context.Context, msg *domain.Message) (*domain.Message, bool, error) {
	var metadataJSON sql.NullString
	if len(msg.Metadata) > 0 {
		bytes, err := json.Marshal(msg.Metadata)
		if err != nil {
			return nil, false, fmt.Errorf("marshaling message metadata: %w", err)
		}
		metadataJSON = sql.NullString{String: string(bytes), Valid: true}
	}

	insertSQL := `
	INSERT INTO messages (id, device_id, message_id, sender, body, received_at, created_at, forwarded_at, status, metadata)
	VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	ON CONFLICT(device_id, message_id) DO NOTHING;`

	res, err := r.db.ExecContext(ctx, insertSQL,
		msg.ID,
		msg.DeviceID,
		msg.MessageID,
		msg.Sender,
		msg.Body,
		msg.ReceivedAt,
		msg.CreatedAt,
		msg.ForwardedAt,
		string(msg.Status),
		metadataJSON,
	)
	if err != nil {
		return nil, false, fmt.Errorf("inserting message: %w", err)
	}

	rows, err := res.RowsAffected()
	if err != nil {
		return nil, false, fmt.Errorf("checking rows affected: %w", err)
	}

	if rows > 0 {
		return msg, true, nil // Successfully created new message
	}

	// Idempotent duplicate: fetch and return existing message
	query := `
	SELECT id, device_id, message_id, sender, body, received_at, created_at, forwarded_at, status, metadata
	FROM messages
	WHERE device_id = ? AND message_id = ?
	LIMIT 1;`

	row := r.db.QueryRowContext(ctx, query, msg.DeviceID, msg.MessageID)
	existing, err := scanMessage(row)
	if err != nil {
		return nil, false, fmt.Errorf("retrieving existing message: %w", err)
	}

	return existing, false, nil
}

func (r *SQLiteMessageRepository) GetByID(ctx context.Context, id string) (*domain.Message, error) {
	query := `
	SELECT id, device_id, message_id, sender, body, received_at, created_at, forwarded_at, status, metadata
	FROM messages
	WHERE id = ?
	LIMIT 1;`

	row := r.db.QueryRowContext(ctx, query, id)
	msg, err := scanMessage(row)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil // Not found
		}
		return nil, fmt.Errorf("getting message by id %s: %w", id, err)
	}

	return msg, nil
}

func (r *SQLiteMessageRepository) GetLatest(ctx context.Context) (*domain.Message, error) {
	query := `
	SELECT id, device_id, message_id, sender, body, received_at, created_at, forwarded_at, status, metadata
	FROM messages
	ORDER BY received_at DESC, created_at DESC
	LIMIT 1;`

	row := r.db.QueryRowContext(ctx, query)
	msg, err := scanMessage(row)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil // Empty table
		}
		return nil, fmt.Errorf("getting latest message: %w", err)
	}

	return msg, nil
}

func (r *SQLiteMessageRepository) List(ctx context.Context, filter domain.MessageFilter) ([]domain.Message, int, error) {
	var whereClauses []string
	var args []any

	if filter.Sender != "" {
		whereClauses = append(whereClauses, "sender = ?")
		args = append(args, filter.Sender)
	}
	if filter.Status != "" {
		whereClauses = append(whereClauses, "status = ?")
		args = append(args, string(filter.Status))
	}

	whereSQL := ""
	if len(whereClauses) > 0 {
		whereSQL = " WHERE " + strings.Join(whereClauses, " AND ")
	}

	// 1. Total count
	countQuery := "SELECT COUNT(1) FROM messages" + whereSQL + ";"
	var total int
	if err := r.db.QueryRowContext(ctx, countQuery, args...).Scan(&total); err != nil {
		return nil, 0, fmt.Errorf("counting messages: %w", err)
	}

	// 2. Query items
	limit := filter.Limit
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	offset := filter.Offset
	if offset < 0 {
		offset = 0
	}

	listSQL := `
	SELECT id, device_id, message_id, sender, body, received_at, created_at, forwarded_at, status, metadata
	FROM messages` + whereSQL + `
	ORDER BY received_at DESC, created_at DESC
	LIMIT ? OFFSET ?;`

	queryArgs := append(args, limit, offset)
	rows, err := r.db.QueryContext(ctx, listSQL, queryArgs...)
	if err != nil {
		return nil, 0, fmt.Errorf("listing messages: %w", err)
	}
	defer rows.Close()

	var messages []domain.Message
	for rows.Next() {
		var m domain.Message
		var forwardedAt sql.NullTime
		var statusStr string
		var metadataJSON sql.NullString

		if err := rows.Scan(
			&m.ID,
			&m.DeviceID,
			&m.MessageID,
			&m.Sender,
			&m.Body,
			&m.ReceivedAt,
			&m.CreatedAt,
			&forwardedAt,
			&statusStr,
			&metadataJSON,
		); err != nil {
			return nil, 0, fmt.Errorf("scanning message row: %w", err)
		}

		m.Status = domain.MessageStatus(statusStr)
		if forwardedAt.Valid {
			m.ForwardedAt = &forwardedAt.Time
		}
		if metadataJSON.Valid && metadataJSON.String != "" {
			_ = json.Unmarshal([]byte(metadataJSON.String), &m.Metadata)
		}

		messages = append(messages, m)
	}

	if err := rows.Err(); err != nil {
		return nil, 0, fmt.Errorf("iterating message rows: %w", err)
	}

	return messages, total, nil
}

type rowScanner interface {
	Scan(dest ...any) error
}

func scanMessage(s rowScanner) (*domain.Message, error) {
	var m domain.Message
	var forwardedAt sql.NullTime
	var statusStr string
	var metadataJSON sql.NullString

	if err := s.Scan(
		&m.ID,
		&m.DeviceID,
		&m.MessageID,
		&m.Sender,
		&m.Body,
		&m.ReceivedAt,
		&m.CreatedAt,
		&forwardedAt,
		&statusStr,
		&metadataJSON,
	); err != nil {
		return nil, err
	}

	m.Status = domain.MessageStatus(statusStr)
	if forwardedAt.Valid {
		m.ForwardedAt = &forwardedAt.Time
	}
	if metadataJSON.Valid && metadataJSON.String != "" {
		_ = json.Unmarshal([]byte(metadataJSON.String), &m.Metadata)
	}

	return &m, nil
}
