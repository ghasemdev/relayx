# RelayX Constitution

Authoritative operating principles, architectural boundaries, and governance rules for the **RelayX** SMS Relay & MCP Automation Gateway.

---

## Constitution Principles Index

| ID | Principle | Scope | Summary | Anchor |
|:---|:---|:---|:---|:---|
| **P-01** | [Local-First & Zero-Infrastructure](#principle-i-local-first--zero-infrastructure-runtime) | Server / Deployment | Single self-contained Go binary, embedded SQLite, zero runtime dependencies. | `#principle-i-local-first--zero-infrastructure-runtime` |
| **P-02** | [Separate Security Domains](#principle-ii-separate-security--authorization-domains) | Auth / Network | Android write token != MCP read token; default bind `127.0.0.1`. | `#principle-ii-separate-security--authorization-domains` |
| **P-03** | [Strict Data Minimization & Privacy](#principle-iii-strict-data-minimization--privacy) | Privacy / Logs | Never log SMS bodies or OTPs; configurable retention and manual purge. | `#principle-iii-strict-data-minimization--privacy` |
| **P-04** | [Device-Side Pre-Filtering](#principle-iv-device-side-pre-filtering--secure-defaults) | Android / Egress | Filter locally before network egress; default DROP; allowlists only. | `#principle-iv-device-side-pre-filtering--secure-defaults` |
| **P-05** | [Durable Delivery & Idempotency](#principle-v-durable-delivery-offline-resilience--idempotency) | Queue / Transport | Survive reboots and offline state; exponential backoff; idempotent deduplication. | `#principle-v-durable-delivery-offline-resilience--idempotency` |
| **P-06** | [Event-Driven Agent MCP Interface](#principle-vi-event-driven-agent-mcp-interface) | MCP / Server | Event-driven `wait_for_message` over polling; safe tool queries; regex OTP. | `#principle-vi-event-driven-agent-mcp-interface` |
| **P-07** | [Complete Mock SMS Parity](#principle-vii-complete-mock-sms-parity) | Testing / CI | Mock SMS runs through the exact same production filter/queue/server pipeline. | `#principle-vii-complete-mock-sms-parity` |
| **P-08** | [Clean Architecture & Boring Tech](#principle-viii-clean-architecture--boring-technology) | Architecture | Modular Go and Android packages; simple, explicit, maintainable code. | `#principle-viii-clean-architecture--boring-technology` |
| **P-09** | [Strict Non-Goals & Safety Limits](#principle-ix-strict-non-goals--safety-limits) | Governance | Personal relay only; no spam, cloud harvesting, or OS security bypasses. | `#principle-ix-strict-non-goals--safety-limits` |

---

## Core Principles

### Principle I: Local-First & Zero-Infrastructure Runtime

1. **Self-Contained Server**: The server must compile into a single static binary (`sms-server`) for Linux (AMD64, ARM64) and macOS (ARM64, AMD64).
2. **Forbidden Server Dependencies**: Do NOT use JVM, Ktor, Docker, PostgreSQL, Redis, Node.js, Python, or external database services.
3. **Embedded Database**: SQLite must be embedded directly into the Go executable. Database migrations must be embedded using Go `embed` (`migrations/*.sql`).
4. **Auto-Initialization**: On first run, `./sms-server` must initialize `./data/sms.db` automatically without user intervention or external setup scripts.

### Principle II: Separate Security & Authorization Domains

1. **Domain Separation**: The credential used by Android to submit SMS (`Authorization: Bearer <device-token>`) MUST NOT grant MCP read access. The AI Agent must use separate read credentials.
2. **Default Binding**: The server must default to localhost (`127.0.0.1:8080`). Remote binding (`--host 0.0.0.0` or `--lan`) must explicitly require authentication.
3. **Transport Security**: HTTPS and WSS are required for any non-localhost communication.
4. **Device Pairing**: In two-phone mode, communication between Phone A and Phone B requires authenticated pairing (numeric code or QR) and encrypted WebSockets.

### Principle III: Strict Data Minimization & Privacy

1. **Zero Sensitive Logging**: Standard and debug logs must NEVER output SMS message bodies, verification codes, or OTP values.
2. **Log Redaction**: Debug logs may only record metadata (sender, timestamp, message ID, filter decisions, retry attempts).
3. **Configurable Retention**: The database must enforce configurable retention windows and provide explicit deletion capabilities. No indefinite storage by default.

### Principle IV: Device-Side Pre-Filtering & Secure Defaults

1. **Local Evaluation**: Rules must be evaluated on the Android device *before* forwarding over the network. Unmatched messages are discarded locally.
2. **Secure Default**: The default filter policy is `DROP`. Every forwarded message must match an explicit `ALLOW`, `CONTENT`, or `TRANSFORM` rule.
3. **Transformation & Masking**: When a rule specifies `FORWARD_TRANSFORMED` (e.g. OTP extraction via regex), the raw SMS body may be omitted entirely from transmission.

### Principle V: Durable Delivery, Offline Resilience & Idempotency

1. **Asynchronous Ingestion**: Android SMS receivers must never perform synchronous network requests. Incoming SMS is validated, filtered, and enqueued into a durable local queue (Room / WorkManager).
2. **State Resilience**: The system must survive network loss, server downtime, Wi-Fi shifts, process restarts, and device reboots (`BOOT_COMPLETED`).
3. **Exponential Backoff**: Failed delivery attempts must retry with exponential backoff (e.g., 1s, 2s, 5s, 10s, 30s, 60s max) avoiding battery drain.
4. **Idempotency**: All message submissions must carry a unique `messageId`. Server write endpoints must be strictly idempotent, rejecting duplicate insertions.

### Principle VI: Event-Driven Agent MCP Interface

1. **Push Notifications Over Polling**: The MCP tool `wait_for_message` must utilize internal Go channels/sync primitives to wake on message arrival, never busy-polling the database.
2. **Structured Timeouts**: `wait_for_message` must honor a caller-specified timeout and return a structured timeout response upon expiration.
3. **Safe Tool Surface**: Expose only authorized tools: `get_latest_message`, `get_messages`, `search_messages`, `wait_for_message`, and `get_otp`. Agent queries must respect application-level security policies.

### Principle VII: Complete Mock SMS Parity

1. **Unified Pipeline**: Mock SMS simulated in developer settings must traverse the exact same code path as real SIM SMS: `Mock SMS -> Filter Engine -> Transformation -> Queue -> Network -> Server -> SQLite -> MCP`.
2. **No Mock Shortcuts**: Development and automated testing must never utilize bypass routes or stubbed pipelines that omit validation or queue logic.

### Principle VIII: Clean Architecture & Boring Technology

1. **Simple Go Layering**: `cmd/server/` -> `internal/api/` & `internal/mcp/` -> `internal/service/` -> `internal/domain/` -> `internal/storage/`.
2. **Clean Android Architecture**: `ui/` -> `viewmodel/` -> `domain/` (Use cases) -> `data/` (Room, Network client).
3. **Minimalist UI**: The Android interface is a focused diagnostic utility (status indicator, active rules, counters, connection settings).

### Principle IX: Strict Non-Goals & Safety Limits

1. **Prohibited Features**: RelayX will never implement bulk marketing SMS, spam distribution, phone number harvesting, carrier network bypasses, or lock-screen security bypasses.
2. **OS Compliance**: Background operation and accessibility services must strictly adhere to Android OS security boundaries and permissions. Device power-off operation is recognized as physically impossible.

---

## Governance

- **Supremacy**: This constitution supersedes all implementation drafts, pull requests, and architecture diagrams.
- **Amendments**: Modifying these principles requires updating this document, adjusting tests, and documenting the change in `docs/memory/DECISIONS.md`.
- **Compliance Gates**: All SDD planning (`speckit-plan`) and security audits (`speckit-security-review-audit`) must verify adherence to these principles.

**Version**: 1.0.0 | **Ratified**: 2026-09-07 | **Last Amended**: 2026-09-07
