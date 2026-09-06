# Project Context

Last reviewed: 2026-09-07

## Product & Mission
**RelayX** is a production-quality, security-conscious personal SMS Relay and Automation Gateway. Its core purpose is personal automation and testing—specifically capturing incoming SMS messages (such as OTP verification codes) from a SIM-equipped Android smartphone and making them securely available to a local AI Agent via the Model Context Protocol (MCP).

## System Actors
1. **Android Gateway (`relayx-android`)**: A SIM-equipped Android smartphone running the RelayX utility. Captures incoming SMS, executes client-side filtering rules, transforms payloads (optional OTP extraction), stores them in a durable local queue, and forwards allowed messages over HTTPS to the server.
2. **Relay Server (`sms-server`)**: A lightweight, standalone Go executable with zero runtime dependencies. Houses an embedded SQLite database and provides both an authenticated HTTP REST API for ingestion and an MCP server for AI Agents.
3. **AI Agent (MCP Client)**: An automated AI developer or agent pairing with RelayX to execute automated workflows (e.g., logging into web services, completing 2FA login challenges by calling `wait_for_message` and `get_otp`).
4. **Second Phone (Testing Mode)**: An optional second Android device without a SIM card paired to Phone A via WebSocket to receive messages for local UI testing and Accessibility automation.

## Key Constraints
- **Zero Server Infrastructure**: The server MUST compile into a single static binary for Linux and macOS. No Docker, JVM, Node, Python, Postgres, or Redis.
- **Embedded Database**: SQLite embedded inside the Go executable with migrations compiled in via `go:embed`.
- **Security Domain Separation**: Android device write credentials (`Bearer <device-token>`) must never grant MCP read access. MCP read queries require distinct agent credentials.
- **Local-First & Default Drop**: Localhost binding (`127.0.0.1:8080`) by default. Rules are evaluated on the phone before forwarding; default filter action is `DROP`.
- **Zero Sensitive Logging**: Standard and debug logs must NEVER log SMS bodies or OTP codes.
- **Testability Without SIM**: Fully functional Mock SMS development mode traversing the identical production pipeline.

## Important Domains
- `relayx-android`: Android Kotlin application (Jetpack Compose, Room, WorkManager, Coroutines).
- `relayx-server` (`sms-server`): Go standalone server (net/http, embedded SQLite, embedded MCP server).
- `specs/`: Feature specifications, technical plans, and task breakdowns.
- `docs/memory/`: Durable cross-feature architecture, decisions, bug patterns, and index.

## Current Priorities
1. Build the standalone Go server foundation (HTTP API, SQLite, auth, embedded migrations).
2. Build the Android gateway with SMS receiver, local rule engine, and durable forward queue.
3. Expose the Go MCP server with event-driven `wait_for_message` and regex-based `get_otp`.
4. Implement end-to-end Mock SMS testing and cross-platform binary releases.
