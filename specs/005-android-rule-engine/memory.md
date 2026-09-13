# Feature Memory: Android Rule & Filtering Engine

**Feature**: `specs/005-android-rule-engine`  
**Date**: 2026-09-14  
**Status**: Active  

---

## 1. Architectural Constraints & Boundaries
- **P-04 (Device-Side Pre-Filtering & Secure Defaults)**:
  - Rules MUST be evaluated locally on the Android device *before* inserting into the network dispatch queue or transmitting over the network.
  - Default filter policy is `DROP`. Unmatched messages MUST NOT be dispatched to the server; they are saved locally as `DeliveryStatus.FILTERED` (or discarded per rule setting) with zero network egress.
  - When a rule specifies `FORWARD_TRANSFORMED` (e.g. OTP extraction via regex), the raw SMS body must NOT be forwarded to the server if the rule requires masking/sanitization; `transformedBody` is submitted instead.
- **P-03 & ADR-008 (Strict Data Minimization & Privacy)**:
  - Never log raw SMS content or extracted OTP codes in Android Logcat.
  - Mask sensitive content in diagnostic and rule test views.
- **P-05 (Durable Delivery & Offline Resilience)**:
  - Rules must be stored in the local Room database (`RuleEntity`, `RuleDao`) with atomic transactions.
  - Outbox queue (`OutboxMessageEntity`) records the rule decision (`status = FILTERED` or `status = PENDING`) alongside any `transformedBody`.
- **P-07 (Complete Mock SMS Parity)**:
  - Mock SMS injected via Developer Settings or tests MUST pass through the exact same `RuleEngine` evaluation pipeline.
- **P-08 (Clean Architecture & Boring Technology)**:
  - Implement deterministic priority ordering (integer priority rank: 1, 2, 3...).
  - First-match-wins deterministic rule evaluation with fallback to default policy.

---

## 2. Reused Decisions & Patterns
- **ADR-003**: Android Room architecture for persistence (`RelayDatabase`).
- **ADR-005**: Kotlin Coroutines and StateFlow for reactive UI state.
- **ADR-006**: Unified pipeline execution for real and mock SMS.

---

## 3. Bug Patterns & Risks to Prevent
- **BUGS #1 (Sensitive Data Leakage)**: Ensure rule matching and regex evaluation do not leak captured groups into `RelayLogger`.
- **ReDoS (Regular Expression Denial of Service)**: Ensure regex patterns compiled by users have execution safeguards and validation before activation.
- **Rule Order Non-Determinism**: Ensure Room DAO orders active rules deterministically by `priority ASC, id ASC`.
