# Data Model: Server Foundation

**Feature**: 001-server-foundation  
**Date**: 2026-09-07  

---

## 1. Entities & Schema

```text
┌──────────────────────────────────────────────────────────┐
│                         devices                          │
├───────────────────┬──────────────┬───────────────────────┤
│ id                │ TEXT         │ PRIMARY KEY (UUID)    │
│ name              │ TEXT         │ NOT NULL              │
│ token_hash        │ TEXT         │ NOT NULL UNIQUE       │
│ created_at        │ TIMESTAMP    │ NOT NULL              │
│ last_seen_at      │ TIMESTAMP    │ NULLABLE              │
└───────────────────┴──────────────┴───────────────────────┘
                                ▲
                                │ 1
                                │
                                │ N
┌───────────────────────────────┴──────────────────────────┐
│                         messages                         │
├───────────────────┬──────────────┬───────────────────────┤
│ id                │ TEXT         │ PRIMARY KEY (UUID)    │
│ device_id         │ TEXT         │ FOREIGN KEY (devices) │
│ message_id        │ TEXT         │ NOT NULL (client ID)  │
│ sender            │ TEXT         │ NOT NULL              │
│ body              │ TEXT         │ NOT NULL              │
│ received_at       │ TIMESTAMP    │ NOT NULL (carrier)    │
│ created_at        │ TIMESTAMP    │ NOT NULL (server)     │
│ forwarded_at      │ TIMESTAMP    │ NULLABLE              │
│ status            │ TEXT         │ NOT NULL DEFAULT 'REC'│
│ metadata          │ TEXT         │ NULLABLE (JSON string)│
└───────────────────┴──────────────┴───────────────────────┘
```

### Table 1: `devices`
Represents an authorized client device (such as the Android gateway smartphone).

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `TEXT` | `PRIMARY KEY` | Internal UUID identifier. |
| `name` | `TEXT` | `NOT NULL` | Human-readable device label (e.g. "Pixel 8 Pro"). |
| `token_hash` | `TEXT` | `NOT NULL UNIQUE` | SHA-256 hash of the Bearer authorization token. |
| `created_at` | `DATETIME` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Registration timestamp. |
| `last_seen_at` | `DATETIME` | `NULLABLE` | Timestamp of last successful authenticated request. |

### Table 2: `messages`
Represents an incoming SMS message record ingested from an Android gateway.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `TEXT` | `PRIMARY KEY` | Server-assigned internal UUID. |
| `device_id` | `TEXT` | `NOT NULL REFERENCES devices(id)` | Ingesting device identifier. |
| `message_id` | `TEXT` | `NOT NULL` | Client-generated stable message UUID for deduplication. |
| `sender` | `TEXT` | `NOT NULL` | SMS originating address or service sender (e.g. "BANK", "+1234567890"). |
| `body` | `TEXT` | `NOT NULL` | Full raw SMS text or transformed payload. |
| `received_at` | `DATETIME` | `NOT NULL` | Device-recorded carrier arrival timestamp. |
| `created_at` | `DATETIME` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Server ingestion timestamp. |
| `forwarded_at` | `DATETIME` | `NULLABLE` | Timestamp when forwarded to downstream client/MCP. |
| `status` | `TEXT` | `NOT NULL CHECK (status IN ('RECEIVED','FILTERED','FORWARDED','FAILED'))` | Ingestion & delivery lifecycle state. |
| `metadata` | `TEXT` | `NULLABLE` | JSON object string containing client metadata (carrier, SIM slot, signal). |

**Indexes & Constraints**:
- `UNIQUE(device_id, message_id)`: Guarantees idempotent ingestion.
- `CREATE INDEX idx_messages_sender ON messages(sender);`
- `CREATE INDEX idx_messages_received_at ON messages(received_at DESC);`
- `CREATE INDEX idx_messages_status ON messages(status);`

### Table 3: `schema_migrations`
Tracks applied database migrations.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `version` | `TEXT` | `PRIMARY KEY` | Migration file identifier (e.g. `001_initial.sql`). |
| `applied_at` | `DATETIME` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Execution timestamp. |

---

## 2. State Lifecycle

```text
[Incoming POST] ───> [Validation]
                          │
                   Pass   ▼   Fail ───> [HTTP 400 Bad Request]
               [Duplicate Check]
                          │
                 New      ▼   Duplicate ───> [HTTP 200 OK (Existing)]
               [Insert: RECEIVED]
                          │
                          ▼
            [Broadcast to Event Broker]
```
