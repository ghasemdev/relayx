# System Architecture

Last reviewed: 2026-09-07

## 1. High-Level Architecture Overview

RelayX bridges real-world telecom SMS with local AI automation through a privacy-first, zero-infrastructure design:

```text
┌───────────────────────────┐
│         AI Agent          │
│   (MCP Client / IDE)      │
└─────────────┬─────────────┘
              │ MCP Protocol (wait_for_message, get_otp)
              ▼
┌───────────────────────────────────────────────────────────┐
│                  Relay Server (Go Binary)                 │
│                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────┐  │
│  │   HTTP API   │  │  MCP Server  │  │  Auth & Filter  │  │
│  │  (/api/v1)   │  │  (Tools/Evt) │  │    (Dual Key)   │  │
│  └──────┬───────┘  └──────┬───────┘  └─────────────────┘  │
│         │                 │                               │
│         └────────┬────────┘                               │
│                  ▼                                        │
│     ┌────────────────────────┐                            │
│     │ Embedded SQLite Engine │ (./data/sms.db)            │
│     └────────────────────────┘                            │
└──────────────────────────▲────────────────────────────────┘
                           │ HTTPS POST /api/v1/messages
                           │ (Bearer <device-token>)
┌──────────────────────────┴────────────────────────────────┐
│              Android Phone (relayx-android)               │
│                                                           │
│  ┌──────────────────┐       ┌──────────────────────────┐  │
│  │ SMS Broadcast Rcv│       │ Mock SMS Simulator (Dev) │  │
│  └────────┬─────────┘       └────────────┬─────────────┘  │
│           └──────────────┬───────────────┘                │
│                          ▼                                │
│              ┌───────────────────────┐                    │
│              │ Client Filter Engine  │ (ALLOW/DROP/TRANS) │
│              └───────────┬───────────┘                    │
│                          ▼                                │
│              ┌───────────────────────┐                    │
│              │ Durable Queue (Room)  │ (Offline/Reboot)   │
│              └───────────┬───────────┘                    │
│                          ▼                                │
│              ┌───────────────────────┐                    │
│              │ Forward Service / Net │ (Exp. Backoff)     │
│              └───────────────────────┘                    │
└───────────────────────────────────────────────────────────┘
```

### Optional Two-Phone Testing Mode
```text
┌──────────────────────┐            WebSocket            ┌──────────────────────┐
│ Phone A (SMS Source) │ ──────────────────────────────> │ Phone B (Automation) │
│ SIM / SMS Receiver   │      Encrypted Device Link      │ Accessibility Service│
└──────────────────────┘                                 └──────────────────────┘
```

---

## 2. Server Architecture (`relayx-server`)

The server is built in Go as a single static executable with zero external runtime dependencies.

### Package Layout
```text
relayx-server/
├── cmd/server/
│   └── main.go                 # CLI entry point, flag parsing, graceful shutdown
├── internal/
│   ├── api/                    # HTTP REST handlers (/api/v1/health, /messages)
│   ├── mcp/                    # MCP server implementation & tool handlers
│   ├── domain/                 # Core entities (Message, Device, Rule, Pairing)
│   ├── service/                # Business logic, event broker, OTP extractor
│   ├── storage/                # Embedded SQLite repository with driver
│   └── config/                 # Default localhost configuration & CLI bindings
├── migrations/                 # Embedded SQL schema (go:embed *.sql)
├── go.mod
└── Makefile                    # Cross-platform build targets
```

### Embedded SQLite Schema
- **`devices`**: `id`, `name`, `token_hash`, `created_at`, `last_seen_at`
- **`messages`**: `id` (UUID), `device_id`, `sender`, `body`, `received_at`, `created_at`, `status` (`RECEIVED`, `FILTERED`, `FORWARDED`, `FAILED`), `metadata` (JSON)
- **`rules`**: `id`, `device_id`, `sender_pattern`, `content_regex`, `action`, `priority`, `enabled`
- **`pairings`**: `id`, `source_device_id`, `client_device_id`, `pairing_code`, `status`, `expires_at`

### Database Indexes
- `idx_messages_sender` (`sender`)
- `idx_messages_received_at` (`received_at` DESC)
- `idx_messages_device_id` (`device_id`)
- `idx_messages_status` (`status`)

---

## 3. Server HTTP API (`/api/v1`)

All endpoints require `Authorization: Bearer <device-token>` (except `/health`).

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/v1/health` | `GET` | Health check, uptime, database connectivity status. |
| `/api/v1/messages` | `POST` | Ingest SMS from Android. Idempotent based on `messageId`. |
| `/api/v1/messages` | `GET` | Query messages by `sender`, `deviceId`, `after`, `before`, `limit`. |
| `/api/v1/messages/latest` | `GET` | Fetch the most recent eligible SMS. |
| `/api/v1/messages/{id}` | `GET` | Retrieve a single message by ID. |
| `/api/v1/messages/search` | `GET` | Safe server-side search by keyword or metadata. |

---

## 4. MCP Server Interface

The MCP server connects local AI Agents without allowing raw SQL execution or bypassing privacy filters.

| Tool Name | Parameters | Behavior |
|---|---|---|
| `get_latest_message` | `sender?` | Returns the most recent authorized SMS matching criteria. |
| `get_messages` | `sender?`, `device_id?`, `after?`, `limit?` | Fetches filtered message lists with pagination limits. |
| `search_messages` | `query`, `limit?` | Server-side safe search against authorized messages. |
| `wait_for_message` | `sender`, `after?`, `timeout` (seconds) | **Event-Driven**: Checks existing messages, then waits on internal Go channel/event broker until message arrives or timeout expires. |
| `get_otp` | `sender`, `regex?`, `timeout?` | Waits for matching message and applies regex extraction (default `\b\d{4,8}\b`). |

---

## 5. Android Architecture (`relayx-android`)

The Android application is designed as a lightweight, resilient utility.

### Layered Separation
- **Presentation**: Jetpack Compose single-activity dashboard displaying status, connection health, rule counters, and developer mock tools.
- **Domain**: Use cases (`ReceiveSms`, `EvaluateRules`, `EnqueueMessage`, `ForwardMessage`, `PairDevices`).
- **Data**:
  - `SmsReceiver`: BroadcastReceiver capturing `SMS_RECEIVED`.
  - `RuleEngine`: In-memory and local rule evaluation (`ALLOW`, `DROP`, `TRANSFORM`).
  - `Room Database`: Durable queue storing pending messages with status and retry count.
  - `WorkManager / ForegroundService`: Background network dispatcher with exponential backoff (1s, 2s, 5s, 10s, 30s, 60s max).
  - `WebSocketClient`: Real-time duplex communication for phone-to-phone pairing.
