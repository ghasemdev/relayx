# Feature Specification: Android Message Inspection & Detail Modal

**Feature Branch**: `feature/003-android-message-inspection`

**Created**: 2026-09-12

**Status**: Draft

**Input**: User description: "start phase 3: Android Message Inspection & Detail Modal. click in received, forwarded failed filtered must show list screen. also show detail for last massage recived need as bottom sheet"

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Throughput Metric Drilldown & Status-Filtered Message List (Priority: P1) 🎯 MVP

As an automation developer or gateway operator, I want to tap on any of the throughput metric cards on the Dashboard (Received, Forwarded, Filtered, Failed) to navigate directly to a dedicated Message History screen pre-filtered to that status category, so that I can immediately inspect the corresponding SMS records and verify gateway activity.

**Why this priority**: Fast diagnostic drilldown from high-level counters to specific message records is crucial during automation runs and troubleshooting.

**Independent Test**: Can be tested by navigating from each metric card on the dashboard and verifying that the Message List screen opens with the appropriate filter pre-selected and displays matching messages from the Room database.

**Acceptance Scenarios**:
1. **Given** the user is on the Dashboard, **When** they tap the "Received" card, **Then** the app navigates to the Message List screen with the "All" tab selected, displaying all received messages in reverse chronological order.
2. **Given** the user is on the Dashboard, **When** they tap the "Forwarded" card, **Then** the app navigates to the Message List screen with the "Forwarded" tab pre-selected, displaying only messages with `DELIVERED` status.
3. **Given** the user is on the Dashboard, **When** they tap the "Failed" card, **Then** the app navigates to the Message List screen with the "Failed" tab pre-selected, displaying only messages with `FAILED` status or pending retries.
4. **Given** the user is on the Dashboard, **When** they tap the "Filtered" card, **Then** the app navigates to the Message List screen with the "Filtered" tab pre-selected, displaying messages dropped or transformed by rule evaluation.
5. **Given** the Message List screen is open, **When** the user taps any filter tab (All, Pending, Forwarded, Failed, Filtered), **Then** the list updates reactively to display only messages matching that status.
6. **Given** no messages match the selected filter, **When** the list loads, **Then** an informative empty state message is displayed (e.g., "No failed messages").

---

### User Story 2 - Last Message Received Detail Bottom Sheet (Priority: P1)

As a gateway user, I want to tap on the "Last Message Received" card on the Dashboard to open an interactive Modal Bottom Sheet showing full message details, so that I can inspect delivery status, timestamps, and error diagnostics without navigating away from the dashboard.

**Why this priority**: Immediate verification of the latest incoming SMS is the most frequent user action during testing and live OTP relays.

**Independent Test**: Can be tested by receiving a simulated SMS, tapping the "Last Message Received" card on the Dashboard, and confirming the Modal Bottom Sheet displays all relevant metadata.

**Acceptance Scenarios**:
1. **Given** at least one message has been received, **When** the user taps the "Last Message Received" card on the Dashboard, **Then** an interactive Modal Bottom Sheet smoothly slides up displaying the message details.
2. **Given** the detail bottom sheet is open, **When** rendered, **Then** it displays:
   - Unique Message UUID (with one-tap copy button)
   - Sender phone number or alphanumeric sender ID
   - Exact arrival timestamp and relative time (e.g. "2 minutes ago")
   - Current delivery status badge (`PENDING`, `DELIVERED`, `FAILED`, `FILTERED`)
   - Attempt count (e.g. "Attempt 1 of 5")
   - Error diagnostics / failure reason (if delivery failed)
3. **Given** Constitution Principle III (Zero Sensitive Logging & Privacy), **When** the bottom sheet is opened, **Then** the message body and OTP digits are masked by default (e.g. `••••••` or masked preview), and an explicit visibility toggle (eye icon) is provided to reveal the plaintext only upon user request.
4. **Given** the message has a `FAILED` or `PENDING` delivery status, **When** viewing the bottom sheet, **Then** a prominent "Retry Delivery" button is visible and active.
5. **Given** the user taps "Retry Delivery", **When** tapped, **Then** the app schedules immediate outbox worker dispatch and updates the status indicator in the sheet.
6. **Given** no messages have been received yet, **When** the user taps the "Last Message Received" card, **Then** the card is non-interactive or shows a brief informational message indicating no messages are available.

---

### User Story 3 - Message Search & Real-Time Filtering in Message List (Priority: P2)

As an engineer managing test runs with multiple OTP senders, I want to search through message records by sender name/number or message ID, so that I can isolate a specific verification message among dozens of received messages.

**Why this priority**: In high-throughput testing environments, searching by sender (e.g., "BANK", "GOOGLE", "TWILIO") saves time and prevents manual scrolling.

**Independent Test**: Can be tested by entering a query into the search bar on the Message List screen and verifying that only matching records remain visible.

**Acceptance Scenarios**:
1. **Given** the Message List screen is open, **When** the user types in the search bar, **Then** the list filters in real time matching sender substrings or partial message UUIDs.
2. **Given** active search text, **When** the user taps the clear button (X), **Then** the search text is cleared and the full list for the selected tab is restored.
3. **Given** an active search query and a selected tab, **When** the search executes, **Then** results respect both the tab filter and the search text.

---

### User Story 4 - Detail Inspection for Any Message from List (Priority: P2)

As a user browsing the Message List screen, I want to tap on any message card to open the same comprehensive Message Detail Bottom Sheet, so that I can inspect past messages and retry older failures with the exact same workflow as the latest message.

**Why this priority**: Consistency across the UI ensures users can inspect, diagnose, and retry any message in history, not just the most recent one.

**Independent Test**: Can be tested by tapping an older message item in the Message List and verifying that the Bottom Sheet opens with that message's data.

**Acceptance Scenarios**:
1. **Given** the Message List screen is displayed, **When** the user taps any message card, **Then** the Message Detail Bottom Sheet opens displaying that specific message's full metadata.
2. **Given** an older failed message is opened from the list, **When** the user taps "Retry Delivery", **Then** the message status is updated to `PENDING` and expedited dispatch is triggered.

---

### Edge Cases

- **Empty Database**: When no messages have been received, the Message List displays a friendly empty state illustration with guidance on sending a test message.
- **Very Long Message Bodies**: Multi-segment SMS messages wrap gracefully in the bottom sheet with a scrollable container so buttons remain reachable.
- **Rapid Status Transitions**: If a message transitions from `PENDING` to `DELIVERED` via background WorkManager while the bottom sheet is open, the UI updates reactively via Room Flow observation.
- **Back Navigation**: Pressing the Android system back gesture/button dismisses the bottom sheet if open; if on the Message List screen, it navigates back to the Dashboard.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST make throughput metric cards on Dashboard clickable with Material 3 ripple feedback, navigating to the Message List screen.
- **FR-002**: System MUST support passing an initial filter parameter (`ALL`, `FORWARDED`, `FILTERED`, `FAILED`) when navigating from Dashboard cards.
- **FR-003**: Message List screen MUST display a horizontal scrollable tab row for filtering messages by status: `All`, `Pending`, `Forwarded`, `Failed`, `Filtered`.
- **FR-004**: Each item in the Message List MUST display: Sender address/name, relative or formatted timestamp, delivery status badge (with distinct color coding for Pending, Delivered, Failed, Filtered), and retry attempt count.
- **FR-005**: System MUST provide a search bar on the Message List screen to filter messages in real time by sender string or message ID.
- **FR-006**: System MUST display an informative empty state when the database contains no messages or when no messages match the active filter/search query.
- **FR-007**: Tapping the "Last Message Received" card on the Dashboard MUST open a Modal Bottom Sheet displaying details of the latest message.
- **FR-008**: Tapping any message card in the Message List screen MUST open the Modal Bottom Sheet displaying details of that specific message.
- **FR-009**: The Message Detail Bottom Sheet MUST display: Full Message UUID (with copy button), Sender address, Ingestion timestamp, Delivery Status, Retry Attempt count, and Error reason/diagnostics (if delivery failed).
- **FR-010**: The Message Detail Bottom Sheet MUST mask message body text and OTP tokens by default, providing an eye toggle button to reveal raw text only upon explicit user action.
- **FR-011**: The Message Detail Bottom Sheet MUST provide a "Retry Delivery" button for messages in `FAILED` or `PENDING` status, triggering immediate background dispatch.
- **FR-012**: System MUST reactively update the Message List and Bottom Sheet when new messages arrive or when message delivery status transitions in Room.
- **FR-013**: System MUST provide back navigation from the Message List screen back to the Dashboard via top App Bar navigation icon and system back gesture.

### Key Entities

- **MessageFilter**: Filter enumeration representing active view scope (`ALL`, `PENDING`, `FORWARDED`, `FAILED`, `FILTERED`).
- **MessageListItem**: Lightweight presentation model for list cards (ID, sender, relative time, status badge, retry count).
- **MessageDetail**: Comprehensive model for modal inspection (ID, sender, formatted timestamp, delivery status, retry count, max retries, error message, masked/unmasked body text).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Tapping any metric card on the Dashboard opens the Message List screen with the matching filter active in under 150ms.
- **SC-002**: Tapping the "Last Message Received" card renders the Message Detail Bottom Sheet in under 100ms.
- **SC-003**: 100% of messages recorded in Room outbox are searchable and filterable with zero dropped or duplicate entries.
- **SC-004**: Zero sensitive message text or OTP codes are shown unmasked unless the user explicitly toggles the visibility button.
- **SC-005**: Tapping "Retry Delivery" for a failed message immediately schedules expedited dispatch without creating duplicate Room records.
- **SC-006**: Search queries update the visible list within 50ms of typing on standard hardware.

---

## Assumptions

- Messages are sourced from the existing Room outbox database table (`OutboxMessageEntity`).
- Phase 2 foundation (Room, WorkManager, Koin 4.2, Navigation 3) is the base for this implementation.
- Deletion / purge of historical messages is out of scope for this phase and will be addressed in data retention management.
