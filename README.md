# RelayX - Personal SMS Relay & MCP Automation Gateway

**RelayX** is a production-grade, privacy-first personal SMS relay and automation gateway. It enables a SIM-equipped Android phone to securely capture, filter, and forward incoming SMS messages (such as two-factor verification codes and OTPs) to a lightweight local server, making them directly available to AI Agents (Antigravity, Claude Code, Cursor, Codex) through the **Model Context Protocol (MCP)**.

---

## Architecture Overview

```text
┌───────────────────────────┐
│         AI Agent          │
│   (MCP Client / IDE)      │
└─────────────┬─────────────┘
              │ Model Context Protocol (wait_for_message, get_otp)
              ▼
┌───────────────────────────────────────────────────────────┐
│              Relay Server (Single Go Binary)              │
│                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────┐  │
│  │   HTTP API   │  │  MCP Server  │  │  Dual-Auth Core │  │
│  │  (/api/v1)   │  │  (Event-Push)│  │ (Device vs Agent│  │
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

---

## Key Features

- **Zero-Infrastructure Server**: Compiled into a single self-contained Go binary. Requires **no** Docker, JVM, Node.js, Python, PostgreSQL, or Redis.
- **Embedded SQLite Database**: Automatically creates and initializes `./data/sms.db` with embedded migrations (`go:embed`) on first launch.
- **Dual Security Domains**: Android write token (`Bearer <device-token>`) only permits message ingestion. AI Agents require separate read credentials to query MCP endpoints.
- **Client-Side Pre-Filtering**: Rules are evaluated locally on the Android device *before* transmission. Default action is `DROP`, keeping personal SMS strictly on your phone.
- **Zero Sensitive Logging**: Standard and debug logs never record raw SMS bodies or OTP verification codes.
- **Event-Driven MCP Server**: The `wait_for_message` tool uses an internal Go event channel to wake waiting agents instantly with zero busy-polling.
- **Durable Delivery**: Offline queue powered by Android Room & WorkManager, surviving Wi-Fi changes, process death, and device reboots (`BOOT_COMPLETED`).
- **Complete Mock SMS Parity**: Interactive simulator in developer tools routes mock messages through the identical production filter and queue pipeline for seamless testing without a SIM card.
- **Optional Two-Phone Mode**: Pair Phone A (SIM receiver) with Phone B (Automation Client) via encrypted WebSockets for dedicated UI testing.

---

## Quickstart Guide

### 1. Start the Server
Download or build the `sms-server` binary for your platform (Linux AMD64/ARM64, macOS ARM64/AMD64):

```bash
# Start server with default localhost binding (127.0.0.1:8080)
./sms-server

# Or bind to LAN for local network access
./sms-server --host 0.0.0.0 --port 8080
```

On first startup, the server automatically creates `./data/sms.db` and applies embedded migrations.

### 2. Configure the Android App (`relayx-android`)
1. Open **RelayX** on your Android device.
2. In **Settings**, enter your server address (e.g. `http://192.168.1.20:8080`) and device authorization token.
3. Tap **Test Connection** to verify connectivity.

### 3. Set Up a Filter Rule
In the **Rules** tab, add an allowlist rule:
- **Sender**: `BANK`
- **Regex**: `Your verification code is (\d{6})`
- **Action**: `FORWARD_TRANSFORMED` (or `FORWARD_RAW`)

### 4. Test with Mock SMS (No SIM Required)
1. Open **Developer Tools** in the app.
2. Enter Sender: `BANK`, Body: `Your verification code is 482913`.
3. Tap **Simulate SMS**.
4. The message will be filtered, queued, and delivered to the server.

### 5. Connect Your AI Agent via MCP
Add RelayX to your MCP configuration (e.g. `claude_desktop_config.json` or Antigravity MCP settings):

```json
{
  "mcpServers": {
    "relayx": {
      "command": "./sms-server",
      "args": ["--mcp-stdio"]
    }
  }
}
```

The AI Agent can now call MCP tools:
- `get_latest_message(sender="BANK")`
- `wait_for_message(sender="BANK", timeout=60)`
- `get_otp(sender="BANK")`

---

## Server CLI Options

```bash
sms-server [OPTIONS]

Options:
  --host <string>     Bind address (default: 127.0.0.1)
  --port <int>        HTTP port (default: 8080)
  --data <string>     Data directory path (default: ./data)
  --debug             Enable verbose diagnostic logging (no message bodies)
  --lan               Shorthand for --host 0.0.0.0
  --mcp-stdio         Expose MCP server over stdin/stdout for local agents
  --help              Display help information
```

---

## Non-Goals & Privacy Pledge

- **No Public Gateway**: RelayX is designed strictly for personal automation on trusted local networks.
- **No Spam / Bulk Messaging**: Outbound mass SMS is not supported.
- **No Lock-Screen Bypass**: Accessibility and automation integrations respect native Android OS security models.
- **No Telemetry**: RelayX contains zero analytics, tracking, or cloud dependencies.

---

## Documentation

- [System Architecture](docs/memory/ARCHITECTURE.md)
- [Constitution & Core Principles](.specify/memory/constitution.md)
- [Architecture Decision Records (ADRs)](docs/memory/DECISIONS.md)
- [Development Roadmap & Definition of Done](docs/roadmap.md)
- [Memory Index](docs/memory/INDEX.md)
