# API Contract: Server Web Dashboard & Observability

**Base Path**: `/api/v1/dashboard`  
**Web UI Route**: `/dashboard/`  
**Authentication**: Optional Admin Token via `--admin-token` or `RELAYX_ADMIN_TOKEN`. When enabled, requests require header `Authorization: Bearer <admin-token>` or cookie `relayx_admin_token=<admin-token>`.

---

## 1. Web UI Serving

### `GET /dashboard/`
- **Description**: Serves the single-page web dashboard application (HTML).
- **Headers**: `Content-Type: text/html; charset=utf-8`
- **Status**: 200 OK

### `GET /dashboard/static/*`
- **Description**: Serves embedded CSS, JS, and asset files.
- **Cache-Control**: `public, max-age=3600`
- **Status**: 200 OK

---

## 2. Real-Time Log Streaming (SSE)

### `GET /api/v1/dashboard/logs/stream`
- **Description**: Establishes a persistent Server-Sent Events stream delivering real-time sanitized log entries.
- **Headers**:
  - `Content-Type: text/event-stream`
  - `Cache-Control: no-cache`
  - `Connection: keep-alive`
- **Query Parameters**:
  - `level` (optional): Minimum log level (`DEBUG`, `INFO`, `WARN`, `ERROR`). Default: `INFO`.
  - `component` (optional): Filter logs by component name.
  - `search` (optional): Substring filter on message text.
- **Event Payload (JSON)**:
  ```json
  {
    "timestamp": "2026-09-13T01:30:00.123Z",
    "level": "INFO",
    "component": "api",
    "message": "message ingested successfully",
    "attributes": {
      "message_id": "c7910f45-c417-4a67-ad21-c0a514c9f78b",
      "sender": "15551234567",
      "body": "[REDACTED]"
    }
  }
  ```

---

## 3. System Metrics & Observability

### `GET /api/v1/dashboard/metrics`
- **Description**: Returns live system runtime and database storage statistics.
- **Response (200 OK)**:
  ```json
  {
    "uptime_seconds": 3600,
    "version": "1.0.0",
    "go_version": "go1.24",
    "num_goroutine": 14,
    "alloc_bytes": 8388608,
    "total_alloc_bytes": 33554432,
    "sys_bytes": 16777216,
    "database": {
      "db_size_bytes": 65536,
      "wal_size_bytes": 32768,
      "db_path": "./data/sms.db"
    },
    "message_counters": {
      "total_received": 142,
      "total_forwarded": 130,
      "total_filtered": 8,
      "total_failed": 4
    }
  }
  ```

---

## 4. SQLite Table Browser

### `GET /api/v1/dashboard/database/tables`
- **Description**: Lists all browsable tables with row counts and column definitions.
- **Response (200 OK)**:
  ```json
  [
    {
      "name": "messages",
      "row_count": 142,
      "primary_key": "id",
      "columns": [
        {"name": "id", "data_type": "TEXT", "nullable": false},
        {"name": "device_id", "data_type": "TEXT", "nullable": false},
        {"name": "sender", "data_type": "TEXT", "nullable": false},
        {"name": "body", "data_type": "TEXT", "nullable": false},
        {"name": "received_at", "data_type": "TIMESTAMP", "nullable": false},
        {"name": "created_at", "data_type": "TIMESTAMP", "nullable": false},
        {"name": "status", "data_type": "TEXT", "nullable": false},
        {"name": "metadata", "data_type": "TEXT", "nullable": true}
      ]
    }
  ]
  ```

### `GET /api/v1/dashboard/database/tables/{name}`
- **Description**: Queries a specific table with pagination, sorting, and filtering.
- **Path Parameter**: `name` (Must be one of: `messages`, `devices`, `schema_migrations`).
- **Query Parameters**:
  - `page`: integer (default: 1)
  - `page_size`: integer (default: 25, max: 100)
  - `sort_by`: string (default: primary key or `created_at`)
  - `sort_order`: `ASC` or `DESC` (default: `DESC`)
  - `status`: string (filter by status column if present)
  - `sender`: string (filter by sender column if present)
  - `search`: string (free text search across text columns)
- **Response (200 OK)**:
  ```json
  {
    "table_name": "messages",
    "page": 1,
    "page_size": 25,
    "total_rows": 142,
    "total_pages": 6,
    "columns": ["id", "device_id", "sender", "body", "received_at", "status"],
    "rows": [
      {
        "id": "c7910f45-c417-4a67-ad21-c0a514c9f78b",
        "device_id": "pixel-4-xl",
        "sender": "15551234567",
        "body": "Your login code is 849201",
        "received_at": "2026-09-13T00:45:10Z",
        "status": "FORWARDED"
      }
    ]
  }
  ```

---

## 5. Device Management

### `GET /api/v1/dashboard/devices`
- **Description**: Lists registered devices and token fingerprints.
- **Response (200 OK)**:
  ```json
  [
    {
      "id": "dev_01j7abc...",
      "name": "Pixel 4 XL Testing",
      "token_fingerprint": "a3f8c12b...",
      "created_at": "2026-09-12T10:00:00Z",
      "last_seen_at": "2026-09-13T00:50:00Z"
    }
  ]
  ```

### `POST /api/v1/dashboard/devices`
- **Description**: Registers a new device and returns the generated secret bearer token (shown only once).
- **Request Body**:
  ```json
  {
    "name": "Production Relay SIM"
  }
  ```
- **Response (201 Created)**:
  ```json
  {
    "id": "dev_01j7xyz...",
    "name": "Production Relay SIM",
    "token": "rlx_dev_98234abcf...",
    "created_at": "2026-09-13T01:35:00Z"
  }
  ```

### `DELETE /api/v1/dashboard/devices/{id}`
- **Description**: Revokes a device credential.
- **Response (200 OK)**:
  ```json
  {
    "message": "device revoked successfully"
  }
  ```
