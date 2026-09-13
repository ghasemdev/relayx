# Feature Specification: Android Rule & Filtering Engine

**Feature Branch**: `feature/005-android-rule-engine`  
**Created**: 2026-09-14  
**Status**: In Progress  
**Feature Directory**: `specs/005-android-rule-engine`  
**Phase**: Phase 5 — Rule & Filtering Engine (`relayx-android`)  

---

## 1. Overview & Business Value

RelayX is a privacy-first personal SMS relay. According to **Principle IV** of the RelayX Constitution, messages must never be blindly forwarded across the network. Personal correspondence, two-factor notifications from unauthorized senders, and spam must be intercepted and evaluated locally on the Android device *prior* to network dispatch.

Phase 5 delivers the client-side Rule & Filtering Engine in `relayx-android`:
1. **Local Pre-Filtering**: Evaluates incoming SMS against user-configured rules before network transmission.
2. **Secure Default (`DROP`)**: Messages not matching an explicit allow rule are marked as `FILTERED` locally with zero network egress.
3. **Flexible Matching**: Criteria based on Sender (exact match, prefix, allowlist) and Content (regex patterns, OTP extraction, substring search).
4. **Action Policies**:
   - `FORWARD_RAW`: Forward original SMS text.
   - `FORWARD_TRANSFORMED`: Extract value (e.g. 6-digit OTP) via regex group, omitting raw SMS text from network dispatch.
   - `DROP`: Discard message locally.
5. **Deterministic Priority**: Rules are executed strictly by priority rank (`priority ASC, id ASC`).
6. **Rule Management UI**: A dedicated Compose screen for viewing, creating, editing, reordering, enabling/disabling, and testing rules with an interactive sandbox.

---

## 2. User Scenarios & Testing

### User Story 1 - Core Rule Engine & Secure Default Drop (Priority: P1)

As a security-conscious user, I want the Android gateway to evaluate all incoming SMS messages against active rules and silently drop any message that does not match an explicit forward rule, so that my personal SMS messages never leave my phone.

**Why this priority**:
This is the core privacy guarantee of RelayX (Constitution Principle IV). Without this, personal SMS messages could inadvertently leak to the server or connected AI agents.

**Independent Test**:
Inject test SMS messages (one matching a forward rule, one with an unmatched sender) and verify that the unmatched message is marked `FILTERED` in the local Room database and never dispatched over HTTP.

**Acceptance Scenarios**:
1. **Given** an active rule matching sender `"MYBANK"` with action `FORWARD_RAW`, **When** an SMS arrives from `"MYBANK"`, **Then** the message is accepted, queued as `PENDING`, and dispatched to the server.
2. **Given** no rules matching sender `"FRIEND"`, **When** an SMS arrives from `"FRIEND"`, **Then** the rule engine evaluates the message, yields `DROP`, saves the message with `status = FILTERED`, and does NOT schedule a network dispatch worker.
3. **Given** rules are disabled or empty, **When** any SMS arrives, **Then** the secure default policy (`DROP`) applies immediately.

---

### User Story 2 - Regex OTP Extraction & Transformation (Priority: P2)

As an automation developer, I want to define content extraction rules (e.g., regex `\b\d{6}\b`) with action `FORWARD_TRANSFORMED`, so that only the verification code is sent to the server while the full SMS text is omitted from the network payload.

**Why this priority**:
Data minimization: AI agents typically only need the OTP code, not the surrounding sensitive banking or account text.

**Independent Test**:
Configure an extraction rule with pattern `Your code is (\d{6})` and action `FORWARD_TRANSFORMED`. Inject a matching message. Verify that the server ingestion payload contains `body = "482913"` instead of the full raw text.

**Acceptance Scenarios**:
1. **Given** a rule with pattern `code is (\d{6})` and action `FORWARD_TRANSFORMED`, **When** an SMS `"Your login code is 839201"` arrives, **Then** `transformedBody` is set to `"839201"` and queued for delivery.
2. **Given** a rule with action `FORWARD_TRANSFORMED` where regex does not match the content, **Then** rule evaluation continues to the next priority rule or falls back to default `DROP`.

---

### User Story 3 - Rule Persistence & Priority Execution in Room (Priority: P3)

As a user, I want my configured rules to be persisted durably across app reboots and executed in deterministic priority order, so that high-priority rules take precedence over general fallback rules.

**Why this priority**:
Persistence and determinism are required to ensure consistent behavior across process restarts, boot triggers (`BOOT_COMPLETED`), and multiple matching rules.

**Independent Test**:
Create Rule A (priority 1: DROP sender "SPAM") and Rule B (priority 2: FORWARD all). Submit a message from "SPAM" and verify Rule A matches and drops the message.

**Acceptance Scenarios**:
1. **Given** multiple rules stored in Room, **When** evaluated, **Then** they are queried with `ORDER BY priority ASC, id ASC`.
2. **Given** a matching rule is encountered, **Then** evaluation terminates immediately (first-match-wins) unless specified otherwise.
3. **Given** a disabled rule (`enabled = false`), **When** evaluating incoming SMS, **Then** the disabled rule is skipped.

---

### User Story 4 - Rule Management Compose UI (Priority: P4)

As a user, I want an intuitive screen in the Android app to view active rules, create new rules, toggle their active state, edit criteria, and delete obsolete rules.

**Why this priority**:
Allows users to manage their filtering policies directly on the device without editing config files or database tables manually.

**Independent Test**:
Open the Rules tab, add a new rule via dialog/form, toggle it off, verify the active rules count updates on the Dashboard in real time.

**Acceptance Scenarios**:
1. **Given** the bottom navigation bar, **When** the user taps "Rules", **Then** the Rules list screen is displayed with active rule count and list items.
2. **Given** the Rules screen, **When** the user taps "+ Add Rule", **Then** a creation dialog allows setting Rule Name, Sender criteria, Pattern regex, Action (`FORWARD_RAW`, `FORWARD_TRANSFORMED`, `DROP`), and Priority.
3. **Given** a rule card, **When** the switch is toggled, **Then** the rule's `enabled` state is updated in the database and reflected in the Dashboard counter.

---

### User Story 5 - Interactive Rule Testing Sandbox (Priority: P5)

As a user, I want a testing sandbox within the app where I can type a sample sender and message body to preview which rule matches and what the extracted payload would be.

**Why this priority**:
Prevents regex errors and accidental message drops by giving users an instant preview before saving or activating rules.

**Independent Test**:
Type sample sender `"BANK"` and body `"Code: 123456"` into the sandbox, tap "Test Rules", and verify the UI shows "Matched: Bank 2FA Rule -> Action: FORWARD_TRANSFORMED -> Extracted: 123456".

**Acceptance Scenarios**:
1. **Given** a sample input, **When** tested, **Then** the test result displays: Matching Rule Name, Evaluated Action, Extracted Payload, and Full Execution Trace.
2. **Given** an invalid regex entered by the user, **When** tested or saved, **Then** an inline error displays without crashing the app.

---

## 3. Edge Cases & Safeguards

1. **ReDoS (Regular Expression Denial of Service)**:
   - User-entered regexes must be validated upon entry with timeout safeguards to prevent catastrophic backtracking.
2. **Empty / Blank Senders**:
   - Handle anonymous or shortcode senders gracefully.
3. **Multipart PDU Messages**:
   - Reassembled multipart SMS must be evaluated as a single coherent text body.
4. **No Rules Configured**:
   - System defaults strictly to `DROP`, preventing any unverified message from forwarding.
5. **Conflicting Priorities**:
   - Enforce deterministic tie-breaking via `priority ASC, id ASC`.

---

## 4. Functional Requirements

- **FR-001**: System MUST provide a `Rule` domain entity with fields: `id`, `name`, `senderPattern`, `senderMatchType` (`EXACT`, `PREFIX`, `ANY`), `contentPattern`, `action` (`FORWARD_RAW`, `FORWARD_TRANSFORMED`, `DROP`), `priority`, `enabled`, and timestamps.
- **FR-002**: System MUST store rules in a Room database table `filter_rules` with automatic migration.
- **FR-003**: System MUST execute rules in ascending priority order (`priority ASC, id ASC`).
- **FR-004**: System MUST apply default `DROP` action when no active rule matches.
- **FR-005**: System MUST extract regex capture group 1 (or entire match) when action is `FORWARD_TRANSFORMED`.
- **FR-006**: System MUST omit raw SMS text from the dispatch payload if the rule specifies transformed forwarding only.
- **FR-007**: System MUST record rule decisions (`status = FILTERED` or `status = PENDING`) in `OutboxMessageEntity`.
- **FR-008**: System MUST integrate the `RuleEngine` into `IngestSmsUseCase`, intercepting both real SIM SMS (`SmsReceiver`) and Developer Tools Mock SMS.
- **FR-009**: System MUST provide a Compose `RulesScreen` accessible via top-level navigation.
- **FR-010**: System MUST provide an interactive rule tester sandbox in the UI.
- **FR-011**: System MUST update the Dashboard "Active Rules" counter reactively via `Flow<Int>`.
- **FR-012**: System MUST seed default starter rules (e.g. standard 2FA OTP rule) on first launch.
- **FR-013**: System MUST validate regex patterns prior to saving and catch `PatternSyntaxException`.
- **FR-014**: System MUST strictly adhere to zero sensitive logging (no OTPs or bodies logged during rule execution).

---

## 5. Definition of Done

- All 5 user stories implemented and covered by unit tests.
- Room migration test verifies schema upgrade from version 1 to 2.
- `IngestSmsUseCaseTest` verifies `FORWARD_RAW`, `FORWARD_TRANSFORMED`, and default `DROP`.
- Android UI verification (`./gradlew test` passes cleanly).
- Manual verification via Mock SMS in emulator confirms matching rules forward while unmatching rules record `status = FILTERED`.
