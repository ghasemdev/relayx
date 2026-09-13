# Memory Synthesis: Android Rule & Filtering Engine

**Feature**: `specs/005-android-rule-engine`  
**Date**: 2026-09-14  
**Synthesized For**: Spec & Plan Generation  

---

## 1. System Scope & Objective
Phase 5 introduces the client-side pre-filtering and transformation engine to `relayx-android`. It guarantees that personal and non-whitelisted SMS messages never leave the device, enforcing Principle IV of the RelayX Constitution:
1. **Rule Evaluation Engine**: Pure Kotlin domain component executing rules deterministically in priority order.
2. **Rule Criteria**:
   - **Sender Rules**: Exact match (`sender == "BANK"`), prefix/pattern match, allowlists.
   - **Content Rules**: Regex extraction (`\b\d{6}\b`, `verification code`), substring contains.
3. **Rule Actions**:
   - `FORWARD_RAW`: Forward original SMS text.
   - `FORWARD_TRANSFORMED`: Extract value (e.g. OTP) and forward transformed payload, omitting raw text.
   - `DROP`: Silently discard unapproved messages, recording as `FILTERED` locally with zero network egress.
4. **Room Database Integration**: `rules` table with default seed rules (e.g., sample banking/verification rule).
5. **Rule Management UI**: A dedicated Rules screen in Compose with create/edit/toggle/delete, priority reordering, and an interactive rule test sandbox.

---

## 2. Hard Governance Gates
1. **Constitution P-04**: Local evaluation is mandatory. Default filter policy is `DROP`.
2. **Constitution P-03 & ADR-008**: Zero sensitive logging during rule evaluation and regex extraction.
3. **Constitution P-07**: Real SMS (`SmsReceiver`) and Mock SMS must pass through the identical `RuleEngine`.
4. **Constitution P-08**: Room database schema migration must be clean, deterministic, and tested.
