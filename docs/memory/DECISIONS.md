# Architecture Decision Records (ADRs)

Last reviewed: 2026-09-13

## ADR Index

| ADR ID | Decision Title | Status | Date |
|---|---|---|---|
| [ADR-001](#adr-001-single-self-contained-go-binary-with-embedded-sqlite) | Single Self-Contained Go Binary with Embedded SQLite | Accepted | 2026-09-07 |
| [ADR-002](#adr-002-dual-security-and-authorization-domains) | Dual Security and Authorization Domains | Accepted | 2026-09-07 |
| [ADR-003](#adr-003-client-side-pre-filtering-with-default-drop-policy) | Client-Side Pre-Filtering with Default-DROP Policy | Accepted | 2026-09-07 |
| [ADR-004](#adr-004-event-driven-push-model-for-mcp-wait_for_message) | Event-Driven Push Model for MCP `wait_for_message` | Accepted | 2026-09-07 |
| [ADR-005](#adr-005-durable-room-queue-and-exponential-backoff) | Durable Room Queue and Exponential Backoff | Accepted | 2026-09-07 |
| [ADR-006](#adr-006-unified-pipeline-execution-for-mock-sms) | Unified Pipeline Execution for Mock SMS | Accepted | 2026-09-07 |
| [ADR-007](#adr-007-pure-go-sqlite-driver-for-cgo-free-cross-compilation) | Pure-Go SQLite Driver for CGO-Free Cross-Compilation | Accepted | 2026-09-07 |
| [ADR-008](#adr-008-structured-redaction-handler-for-zero-leakage-privacy) | Structured Redaction Handler for Zero-Leakage Privacy | Accepted | 2026-09-07 |
| [ADR-009](#adr-009-server-side-ingestion-hooks-for-emulator-and-simulator-relay) | Server-Side Ingestion Hooks for Emulator and Simulator Relay | Accepted | 2026-09-11 |
| [ADR-010](#adr-010-koin-42-component-scanning-app-startup--navigation-3-integration) | Koin 4.2 Component Scanning, App Startup & Navigation 3 Integration | Accepted | 2026-09-12 |
| [ADR-011](#adr-011-defense-in-depth-for-gateway-data-stores-and-cleartext-scoping) | Defense-in-Depth for Gateway Data Stores and Cleartext Scoping | Accepted | 2026-09-12 |
| [ADR-012](#adr-012-navigation-3-edge-to-edge-scaffolding-with-animated-global-chrome) | Navigation 3 Edge-to-Edge Scaffolding with Animated Global Chrome | Accepted | 2026-09-13 |
| [ADR-013](#adr-013-reactive-state-synchronization-for-collapsible-headers-with-directional-scroll-reset) | Reactive State Synchronization for Collapsible Headers with Directional Scroll Reset | Accepted | 2026-09-13 |
| [ADR-014](#adr-014-embedded-static-web-dashboard-with-downstream-log-redaction-and-non-blocking-sse-fan-out) | Embedded Static Web Dashboard with Downstream Log Redaction & Non-Blocking SSE Fan-Out | Accepted | 2026-09-13 |

---

### ADR-001: Single Self-Contained Go Binary with Embedded SQLite
- **Context**: The relay server must run on personal developer machines (Linux, macOS) with zero friction and zero setup.
- **Decision**: Build the server entirely in Go, embedding SQLite and migrations (`go:embed migrations/*.sql`). Reject JVM/Ktor, Docker, Postgres, Redis, Python, or Node runtimes.
- **Consequences**:
  - Positive: Running `./sms-server` immediately starts without container daemons or dependency management.
  - Positive: Cross-compilation to Linux (AMD64/ARM64) and macOS (ARM64/AMD64) is trivial.
  - Tradeoff: CGO-free or modern pure-Go SQLite driver (such as `modernc.org/sqlite`) preferred to maintain static binary cross-compilation simplicity.

### ADR-002: Dual Security and Authorization Domains
- **Context**: An Android phone sends SMS data to the server, and an AI Agent reads SMS data via MCP. A compromised phone token must not allow reading all stored SMS.
- **Decision**: Strictly separate ingestion credentials from MCP query credentials. The device write token only permits `POST /api/v1/messages`. Reading requires explicit MCP credentials.
- **Consequences**:
  - Positive: Protects stored SMS and OTP history from unauthorized exposure.
  - Positive: Allows individual device revoking without invalidating AI Agent setups.

### ADR-003: Client-Side Pre-Filtering with Default-DROP Policy
- **Context**: Personal smartphones receive diverse messages (personal chats, spam, banking). Forwarding every message exposes unnecessary personal data.
- **Decision**: Evaluate rules locally on the Android device prior to network dispatch. The default action is `DROP`. Only explicitly matched messages are forwarded. If `FORWARD_TRANSFORMED` is active, the raw body can be stripped.
- **Consequences**:
  - Positive: High privacy; personal SMS never leaves the smartphone.
  - Tradeoff: Android client requires local rule management UI and engine.

### ADR-004: Event-Driven Push Model for MCP `wait_for_message`
- **Context**: When an AI Agent needs an OTP, it needs to wait for the incoming SMS. Constant polling wastes CPU and creates latency.
- **Decision**: Implement an internal Go event broker (channels / conditional broadcast) that notifies waiting MCP requests immediately when a message is committed.
- **Consequences**:
  - Positive: Sub-millisecond response latency once SMS is ingested. Zero CPU overhead while waiting.
  - Positive: Robust timeout handling built into Go `select` blocks.

### ADR-005: Durable Room Queue and Exponential Backoff
- **Context**: Mobile connectivity is erratic (Wi-Fi drops, sleep modes, reboots). SMS cannot be lost.
- **Decision**: Persist incoming allowed SMS into a local Room database queue before attempting network delivery. Use Android WorkManager / Foreground Service with exponential backoff retries.
- **Consequences**:
  - Positive: Survives device reboots (`BOOT_COMPLETED`), network changes, and server restarts.
  - Positive: Idempotent message IDs prevent duplication on the server.

### ADR-006: Unified Pipeline Execution for Mock SMS
- **Context**: Developers need to build, test, and verify MCP automation without access to a live SIM card.
- **Decision**: Mock SMS inputs in developer settings must pass through the exact same receiver, filter engine, Room queue, network client, and server pipeline as live SMS.
- **Consequences**:
  - Positive: Total test fidelity. If the mock pipeline succeeds, live SIM traffic is guaranteed to work.
  - Positive: End-to-end integration tests can run reliably in CI environments.

### ADR-007: Pure-Go SQLite Driver for CGO-Free Cross-Compilation
- **Context**: RelayX requires single-command cross-compilation across Linux (AMD64, ARM64) and macOS (ARM64, AMD64) without requiring external C compilers, Docker buildx, or sysroots.
- **Decision**: Adopt pure-Go SQLite driver `modernc.org/sqlite` instead of CGO-dependent `mattn/go-sqlite3`. Configure WAL mode, `busy_timeout=5000`, and single open connection pool (`SetMaxOpenConns(1)`).
- **Consequences**:
  - Positive: Complete elimination of CGO (`CGO_ENABLED=0`) across all target platforms.
  - Positive: Server auto-creates `./data/sms.db` and applies embedded migrations seamlessly on macOS and Linux hosts.
  - Tradeoff: Concurrent write operations require careful coordination via `SetMaxOpenConns(1)` to avoid file lock contention.

### ADR-008: Structured Redaction Handler for Zero-Leakage Privacy
- **Context**: RelayX handles sensitive authentication OTP codes and confidential SMS messages. Operational observability and debug logging must never leak secrets into logs or stdout.
- **Decision**: Wrap standard Go `slog` with a custom `RedactingHandler` that intercepts and scrubs sensitive keys (`body`, `otp`, `code`, `token`, `authorization`) and bearer auth tokens before outputting structured JSON logs.
- **Consequences**:
  - Positive: Enforces Constitution Principle III across all existing and future server packages automatically.
  - Positive: Validated with automated assertions in `logger_test.go` ensuring zero leakage even in `--debug` mode.
  - Tradeoff: Diagnostic logs must rely strictly on metadata (sender, message ID, timestamp, byte length) rather than inspecting raw payloads.

### ADR-009: Server-Side Ingestion Hooks for Emulator and Simulator Relay
- **Context**: In real-world automated testing, a physical smartphone with a live cellular carrier SIM receives 2FA/bank verification SMS. Testing scripts and UI automation frequently run inside virtual devices (Android Emulator or iOS Simulator) without SIM capabilities.
- **Decision**: Provide configurable server-side ingestion hooks in `relayx-server`:
  1. `--adb-port <port>` (e.g. `5554`): Automatically executes `adb -s emulator-<port> emu sms send "<sender>" "<body/code>"` whenever a new SMS is ingested.
  2. `--exec-hook <path>`: Runs a custom executable or script with message metadata and payload passed via arguments and environment variables (`RELAYX_SENDER`, `RELAYX_MESSAGE_ID`, `RELAYX_DEVICE_ID`, `RELAYX_BODY`) to support iOS Simulator (`xcrun simctl push` / SMS injection) or third-party webhooks.
  3. Execution is asynchronous and decoupled from HTTP response latency.
  4. Logging respects Constitution Principle III by redacting sensitive payload bodies.
- **Consequences**:
  - Positive: Enables seamless Physical SIM Phone -> RelayX Server -> Emulator/Simulator automation flow.
  - Positive: Test suites can receive real verification codes directly inside running emulators without manual code copying.
  - Tradeoff: Requires ADB or simulator tools to be available in the server runtime environment if hooks are enabled.

### ADR-010: Koin 4.2 Component Scanning, App Startup & Navigation 3 Integration
- **Context**: Android dependency injection and initialization must integrate cleanly with Jetpack Compose Navigation 3 and avoid main-thread blocking or boilerplate Application subclasses.
- **Decision**: Adopt Koin 4.2 with compiler plugin (`koin-annotations:1.2.1`) for compile-time verified DI (`@Single`, `@KoinViewModel`, `@ComponentScan`). Initialize DI via AndroidX App Startup (`koin-androidx-startup:4.2.2`). Connect ViewModel resolution in Navigation 3 via Koin's `koinEntryProvider()`. Standardize on Kotlin stdlib `kotlin.uuid.Uuid` and Kotlin 2.4+ Explicit Backing Fields (`-XXLanguage:+ExplicitBackingFields`) for ViewModel state flows.
- **Consequences**:
  - Positive: Zero reflection overhead on DI resolution; clean declarative ViewModels and single-responsibility modules.
  - Positive: Consistent UUID management between Android client and server domain models without `java.util.UUID` legacy dependencies.
  - Tradeoff: Requires Kotlin compiler flag `-XXLanguage:+ExplicitBackingFields` and KSP processor synchronization.

### ADR-011: Defense-in-Depth for Gateway Data Stores and Cleartext Scoping
- **Context**: An SMS gateway device stores sensitive transit data (outbox SMS, Bearer authentication tokens). Leaving Android application backup enabled exposes Room SQLite and DataStore to ADB extraction. Permitting global cleartext traffic exposes API communications outside local test environments.
- **Decision**:
  1. Set `android:allowBackup="false"` on the application manifest.
  2. Maintain explicit exclusion rules in `backup_rules.xml` and `data_extraction_rules.xml` targeting `database` (`relayx.db*`) and `datastore/` directories.
  3. Introduce `network_security_config.xml` with `base-config cleartextTrafficPermitted="false"`, scoping cleartext HTTP exclusively to local development domains (`10.0.2.2`, `127.0.0.1`, `localhost`).
- **Consequences**:
  - Positive: Prevents physical data extraction of cryptographic tokens and queued SMS via `adb backup` or device transfers.
  - Positive: Guarantees HTTPS enforcement for all non-loopback production traffic.
  - Tradeoff: Testing with LAN IP addresses requires temporary dev configuration or TLS deployment.

### ADR-012: Navigation 3 Edge-to-Edge Scaffolding with Animated Global Chrome
- **Context**: In Jetpack Compose Navigation 3, placing a global `Scaffold` with `NavigationBar` around the entire navigation host injects inner padding that causes list screens to experience double padding or bottom dead space. Furthermore, full-screen message inspection requires hiding the bottom bar smoothly to maximize list viewport area.
- **Decision**: Decouple the root `Scaffold` content padding from leaf destination layouts. Wrap the global `NavigationBar` inside `AnimatedVisibility(visible = currentRoute != "messages", enter = slideInVertically { it }, exit = slideOutVertically { it })`. Enable `MessageListScreen` to extend full-size edge-to-edge down to the system display boundary, handling its own navigation bar insets at the scroll edge.
- **Consequences**:
  - Positive: True edge-to-edge scrolling without dead space or bottom bar cutoffs.
  - Positive: Fluid, animated transitions between dashboard navigation and full-screen message lists.
  - Tradeoff: Child screens must explicitly handle window insets (`Modifier.navigationBarsPadding()`) for their bottom scroll content.

### ADR-013: Reactive State Synchronization for Collapsible Headers with Directional Scroll Reset
- **Context**: Collapsible top search bars must allow users to tap a top app bar action icon to expand search and scroll to top, but also automatically collapse and hide the search bar when scrolling down through the list. A naive boolean toggle causes state de-synchronization where subsequent downward scrolling fails to collapse the header.
- **Decision**: Combine `derivedStateOf` for immediate scroll detection with a `snapshotFlow(currentListState)` scroll offset delta collector that automatically clears the `isSearchExplicitlyExpanded` state as soon as downward scrolling is detected.
- **Consequences**:
  - Positive: Smooth, predictable UX where top bar actions trigger immediate expansion, while standard list scrolling collapses the search header naturally.
  - Positive: Decouples tab-specific `LazyListState` across `HorizontalPager` pages while preserving responsive search animation behavior.

### ADR-014: Embedded Static Web Dashboard with Downstream Log Redaction and Non-Blocking SSE Fan-Out
- **Context**: RelayX developers and operators require zero-configuration visibility into live server events, database contents, system metrics, and registered devices without external dependencies (Node.js, npm, CDNs, Docker) and without compromising Constitution Principle III (zero sensitive payload leaks).
- **Decision**:
  1. Embed all frontend assets (HTML, CSS, JavaScript) directly into the single Go server binary via `go:embed static/*` and serve them under `/dashboard/`.
  2. Implement an in-memory `LogBroadcaster` with a bounded ring buffer (500 items) and non-blocking channel dispatch (`select { case ch <- entry: default: }`).
  3. Hook the broadcaster into `RedactingHandler` downstream of attribute sanitization (`sanitizeAttr`) so sensitive keys (`body`, `otp`, `token`, `code`) are redacted before emission to browser Server-Sent Events (`/api/v1/dashboard/logs/stream`).
  4. Provide read-only SQLite table browsing with strict table allowlisting (`messages`, `devices`, `schema_migrations`), validated column sorting, and default masked body display (`••••••••••••`).
  5. Enforce optional admin token authentication via `--admin-token` / `RELAYX_ADMIN_TOKEN` supporting Bearer headers, session cookies, and query tokens (for EventSource).
- **Consequences**:
  - Positive: Fully self-contained single static binary (~10-11 MB) adhering strictly to Constitution Principle I.
  - Positive: Guarantees zero sensitive OTP/SMS body leakage over live browser streaming (Constitution Principle III).
  - Positive: Slow browser tabs or network hiccups cannot block ingestion throughput or cause unbounded memory growth.
  - Tradeoff: In-memory log buffer retains only the most recent 500 records across server restarts.



