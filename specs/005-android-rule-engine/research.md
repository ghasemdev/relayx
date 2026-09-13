# Research & Technical Decisions: Android Rule & Filtering Engine

**Feature**: `specs/005-android-rule-engine`  
**Date**: 2026-09-14  
**Status**: Completed  

---

## 1. Rule Matching Architecture & Evaluation Strategy

### Decision
Implement a pure Kotlin domain evaluation engine (`RuleEngine`) using first-match-wins deterministic priority order (`priority ASC, id ASC`) with a mandatory fallback to `DROP`.

### Rationale
- **Constitution Principle IV Compliance**: Default policy must be `DROP`. Unmatched messages are discarded locally.
- **Predictability & Safety**: Deterministic first-match-wins prevents conflicting rule behaviors. Senders and patterns are evaluated linearly in ranked priority order.
- **Low Latency**: Evaluating 10-50 compiled regex rules takes sub-millisecond execution time, ensuring SMS broadcast reception is not blocked.

### Alternatives Considered
- **All-Match Chaining (Pipelining)**: Running messages through multiple filters in sequence. *Rejected*: Adds stateful mutation complexity and makes action determination (e.g. DROP vs FORWARD) ambiguous.
- **Server-Side Filtering**: *Strictly Rejected by Constitution P-04*: Leaks personal SMS bodies to the network before filtering.

---

## 2. Regex Compilation, Safety & ReDoS Protection

### Decision
- Pre-compile valid regex patterns with cache and safety bounds.
- Validate regex patterns on user input during creation/editing in the UI with a syntax check (`try { Regex(pattern) } catch (e: PatternSyntaxException)`).
- Restrict evaluation to timed execution or limit input string lengths to prevent catastrophic backtracking (ReDoS).

### Rationale
- SMS bodies are bounded by standard SMS length (typically < 1600 characters for multi-part messages). ReDoS risk is minimal on short strings, but malformed patterns could cause `PatternSyntaxException` or app crashes if not validated upfront.

### Alternatives Considered
- **Strict Substring Only (No Regex)**: *Rejected*: Users explicitly require extracting variable 4-8 digit OTP codes from dynamic SMS templates.

---

## 3. Data Transformation & Omission

### Decision
Support three core actions:
1. `FORWARD_RAW`: Raw message body transmitted as-is in `body` field of `POST /api/v1/messages`.
2. `FORWARD_TRANSFORMED`: Regex capture group 1 (or full regex match if no group defined) extracted into `transformedBody`. The server receives the extracted value in `body`. Raw message body is never sent over the network.
3. `DROP`: Message recorded locally in Room with `status = DeliveryStatus.FILTERED`. Network dispatch worker is not triggered.

### Rationale
- Satisfies data minimization requirements (**Constitution Principle III & IV**). AI agents consuming the message via MCP only need the extracted verification code, preventing storage or transmission of sensitive account details.

---

## 4. Room Database Schema & Migration

### Decision
- Add `RuleEntity` to `RelayDatabase` with `@Entity(tableName = "filter_rules")`.
- Increment database version from 1 to 2.
- Provide `MIGRATION_1_2` executing standard SQL `CREATE TABLE IF NOT EXISTS filter_rules (...)` and indexing `priority` and `enabled`.
- Seed a default starter rule upon initial installation:
  - Name: "Default Verification Codes"
  - Sender: `*` (Match Type: `ANY`)
  - Content Pattern: `(?i)(?:code|otp|verification)[:\s]+([0-9]{4,8})`
  - Action: `FORWARD_TRANSFORMED`
  - Priority: 100
  - Enabled: true

### Rationale
- Clean schema migration maintains existing outbox messages during database upgrades.
- Seeding a safe default rule ensures out-of-the-box utility without manual configuration, while still blocking non-verification SMS.

---

## 5. UI & Interactive Testing Sandbox

### Decision
- Add a top-level `Rules` tab to `RelayNavGraph` in the bottom navigation bar alongside `Dashboard` and `Settings`.
- Provide an embedded "Rule Sandbox" card on the Rules screen allowing instant test execution against active rules without sending actual SMS messages.
- Compose Material 3 design matching existing theme with status badges, priority badges, and toggle switches.

### Rationale
- High usability: Users can immediately test their banking shortcodes or custom regex before relying on them for live OTP relay.
