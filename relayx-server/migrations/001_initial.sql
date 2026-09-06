-- 001_initial.sql: RelayX Core Database Schema

CREATE TABLE IF NOT EXISTS devices (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at DATETIME
);

CREATE TABLE IF NOT EXISTS messages (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES devices(id),
    message_id TEXT NOT NULL,
    sender TEXT NOT NULL,
    body TEXT NOT NULL,
    received_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    forwarded_at DATETIME,
    status TEXT NOT NULL CHECK (status IN ('RECEIVED', 'FILTERED', 'FORWARDED', 'FAILED')),
    metadata TEXT,
    CONSTRAINT uq_device_message_id UNIQUE (device_id, message_id)
);

CREATE INDEX IF NOT EXISTS idx_messages_sender ON messages(sender);
CREATE INDEX IF NOT EXISTS idx_messages_received_at ON messages(received_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_status ON messages(status);
CREATE INDEX IF NOT EXISTS idx_messages_device_id ON messages(device_id);
