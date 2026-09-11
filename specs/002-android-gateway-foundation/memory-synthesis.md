# Memory Synthesis: 002-android-gateway-foundation

## Core Directives & Hard Constraints
- **Client-Side Pre-Filtering (P-04 & ADR-003)**: Evaluation happens on the smartphone; messages are kept local unless an explicit forwarding rule permits egress.
- **Offline Durability & Idempotency (P-05 & ADR-005)**: All messages must be persisted into Room before network dispatch. Dispatch uses unique `messageId` and retries with exponential backoff (1s, 2s, 5s, 10s, 30s, 60s).
- **Zero Sensitive Logging (P-03, ADR-008 & BUGS #1)**: Android Logcat and diagnostics must strictly exclude SMS body text and OTP values.
- **Doze Mode & Boot Resilience (BUGS #2 & P-05)**: Survives `BOOT_COMPLETED` and screen-off Doze states via WorkManager and foreground/expedited tasks where necessary.
- **Dual Security Domain (P-02 & ADR-002)**: Mobile device uses Device ID + Bearer Write Token for ingestion (`POST /api/v1/messages`).

## Conflict Analysis
- No conflicts identified with `.specify/memory/constitution.md` or durable architecture records (`docs/memory/INDEX.md`, `ARCHITECTURE.md`, `DECISIONS.md`).
- Aligns directly with Phase 2 requirements in `docs/roadmap.md`.
