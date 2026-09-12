---
document_type: security-review
review_type: branch
assessment_date: 2026-09-13
codebase_analyzed: /Volumes/ADATASD810/Projects/Android/relayx
total_files_analyzed: 19
total_findings: 2
overall_risk: LOW
critical_count: 0
high_count: 0
medium_count: 0
low_count: 2
informational_count: 0
owasp_categories: [A02, A04]
cwe_ids: [CWE-200, CWE-359]
field_summaries:
  document_type: "Always 'security-review'. Allows indexers to skip non-review documents."
  review_type: "Which command generated this document: audit, branch, staged, plan, tasks, or followup."
  assessment_date: "ISO 8601 date the review was performed (YYYY-MM-DD)."
  overall_risk: "Highest severity tier with active findings (CRITICAL, HIGH, MODERATE, LOW, INFORMATIONAL)."
  critical_count: "Number of Critical findings (CVSS 9.0-10.0)."
  high_count: "Number of High findings (CVSS 7.0-8.9)."
  medium_count: "Number of Medium findings (CVSS 4.0-6.9)."
  low_count: "Number of Low findings (CVSS 0.1-3.9)."
  informational_count: "Number of Informational findings."
  owasp_categories: "OWASP Top 10 2025 categories (A01-A10) that have at least one finding."
  cwe_ids: "CWE identifiers referenced in this document."
  finding_id: "Unique finding identifier (SEC-NNN) for cross-referencing and task linkage."
  location: "File path and line number of the vulnerable code (path/to/file.ext:line)."
  owasp_category: "OWASP Top 10 2025 category for this finding (AXX:2025-Name)."
  cwe: "Common Weakness Enumeration identifier with short name (CWE-NNN: Name)."
  cvss_score: "CVSS v3.1 base score (0.0-10.0). 9.0+=Critical, 7.0-8.9=High, 4.0-6.9=Medium, 0.1-3.9=Low."
  spec_kit_task: "Spec-Kit task ID for backlog tracking and remediation follow-up (TASK-SEC-NNN)."
---

# SECURITY REVIEW REPORT — BRANCH: feature/003-android-message-inspection vs develop

## Executive Summary
This targeted security review analyzed the code changes introduced in `feature/003-android-message-inspection` relative to `develop`. The scope encompasses the message inspection UI, Room DAO extensions, Jetpack Compose navigation scaffolding, ViewModel reactive pipeline, and modal detail inspection.

Overall risk is assessed as **LOW**. No critical, high, or medium severity vulnerabilities were identified. The implementation strictly complies with **Constitution Principle III** (Zero raw message bodies or OTPs in Logcat; default masking in UI) and **Principle V** (Idempotent delivery retries without UUID regeneration). Two low-severity items are identified and documented as technical debt for Phase 7 security hardening (clipboard sensitivity tagging for Android 13+ and app switcher thumbnail privacy).

---

## Branch Diff Reviewed
- **Target Branch**: `feature/003-android-message-inspection`
- **Base Branch**: `develop`
- **Codebase Files Changed**:
  - `relayx-android/src/main/java/com/parsomash/relayx/data/local/OutboxMessageDao.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/domain/model/MessageFilter.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/domain/model/MessageInspectionModels.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/domain/model/Models.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/ui/dashboard/DashboardScreen.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/ui/message/MessageDetailBottomSheet.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/ui/message/MessageItemCard.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/ui/message/MessageListScreen.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/ui/navigation/RelayNavGraph.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/ui/theme/Color.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/viewmodel/DashboardViewModel.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/viewmodel/MessageListViewModel.kt`
  - `relayx-android/src/main/res/values/strings.xml`
  - `relayx-android/src/test/java/com/parsomash/relayx/data/local/FakeOutboxMessageDao.kt`
  - `relayx-android/src/test/java/com/parsomash/relayx/data/local/OutboxMessageDaoTest.kt`
  - `relayx-android/src/test/java/com/parsomash/relayx/viewmodel/DashboardViewModelTest.kt`
  - `relayx-android/src/test/java/com/parsomash/relayx/viewmodel/MessageListViewModelTest.kt`

---

## Vulnerability Findings

### [LOW] SEC-004: Missing Sensitive Content Flag for Android 13+ Clipboard Manager
- **Finding ID**: SEC-004
- **Location**: `relayx-android/src/main/java/com/parsomash/relayx/ui/message/MessageDetailBottomSheet.kt:180`
- **OWASP Category**: A02:2025-Cryptographic Failures / OWASP Mobile M02: Insecure Data Storage
- **CWE**: CWE-200: Exposure of Sensitive Information to an Unauthorized Actor
- **CVSS v3.1 Score**: 2.6 (`CVSS:3.1/AV:L/AC:H/PR:N/UI:R/S:U/C:L/I:N/A:N`)
- **Description**: In `MessageDetailBottomSheet.kt`, copying the message identifier invokes `clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Message ID", detail.id)))`. While the current implementation only copies non-sensitive UUID strings, Android 13+ (API 33+) displays an on-screen preview of copied clipboard content. If copying sensitive SMS message bodies or OTP verification codes is introduced in future releases without `ClipDescription.EXTRA_IS_SENSITIVE = true`, cleartext secrets will be visually exposed in the system clipboard overlay.
- **Remediation**: Establish an architectural rule that whenever message payloads or credentials are copied to the clipboard, `PersistableBundle.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)` must be attached to the `ClipDescription`.
- **Spec-Kit Task**: `TASK-SEC-004` (Tracked as Technical Debt for Phase 7).

### [LOW] SEC-005: Unmasked Payload Snapshot Exposure in Recent Apps Switcher
- **Finding ID**: SEC-005
- **Location**: `relayx-android/src/main/java/com/parsomash/relayx/ui/message/MessageDetailBottomSheet.kt:74`
- **OWASP Category**: A04:2025-Insecure Design / OWASP Mobile M02: Insecure Data Storage
- **CWE**: CWE-359: Exposure of Private Personal Information
- **CVSS v3.1 Score**: 2.1 (`CVSS:3.1/AV:P/AC:L/PR:N/UI:R/S:U/C:L/I:N/A:N`)
- **Description**: `MessageDetailBottomSheet` provides an eye toggle that unmasks the raw SMS payload (`isPayloadRevealed = true`). If a user reveals an SMS containing confidential OTPs or sensitive personal text and switches out of RelayX into another app, the Android OS captures a visual screenshot of the current window for the task switcher (Recents). This screenshot is stored on disk unencrypted by SystemUI and visible in the app switcher.
- **Remediation**: Introduce an auto-remasking timer (e.g. automatically resetting `isPayloadRevealed = false` after 30 seconds of inactivity) and/or conditionally toggle `WindowManager.LayoutParams.FLAG_SECURE` when raw payloads are revealed in Phase 7 hardening.
- **Spec-Kit Task**: `TASK-SEC-005` (Tracked as Technical Debt for Phase 7).

---

## Confirmed Secure Patterns

1. **Constitution Principle III (Zero Sensitive Data in Logs & Default Masking)**:
   - SMS payload in [`MessageDetailBottomSheet.kt`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-android/src/main/java/com/parsomash/relayx/ui/message/MessageDetailBottomSheet.kt#L190-L245) is masked by default (`••••••••••••`) using monospace bullet symbols.
   - List cards in [`MessageItemCard.kt`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-android/src/main/java/com/parsomash/relayx/ui/message/MessageItemCard.kt) render only sender, relative timestamp, and status chips; message bodies are never loaded into list item composables.
   - Zero sensitive logging: Neither SMS bodies nor OTP tokens are output to Logcat during message inspection, filtering, or retry execution.

2. **Constitution Principle V (Idempotency in Manual Retries)**:
   - Tapping "Retry Delivery" invokes `OutboxMessageDao.resetForRetry(id, now)`. This resets `status = 'PENDING'`, clears `error_message`, and updates `last_attempt_at` strictly in-place.
   - The message UUID is **never regenerated or mutated**, guaranteeing end-to-end idempotent processing at the upstream server.

3. **Strict Injection-Proof Data Layer**:
   - Room queries in [`OutboxMessageDao.kt`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-android/src/main/java/com/parsomash/relayx/data/local/OutboxMessageDao.kt) use typed parameter binding (`WHERE id = :id`).
   - In-memory search filtering in [`MessageListViewModel.kt`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-android/src/main/java/com/parsomash/relayx/viewmodel/MessageListViewModel.kt) performs sanitized lowercase substring matching without raw regex compilation vulnerabilities.

4. **Modern Compose Cleanliness & Configuration Awareness**:
   - Replaced deprecated `LocalClipboardManager` with modern `LocalClipboard` + `ClipEntry`.
   - Replaced deprecated `ScrollableTabRow` with `PrimaryScrollableTabRow`.
   - String resources resolved via `stringResource(...)` rather than stale context lookups.

5. **Battery-Aware Dispatch & Work Deduplication**:
   - Retries enqueued with `ExistingWorkPolicy.KEEP` in `MessageDispatchWorker`, preventing redundant WorkManager scheduling.

---

## Prioritized Action Plan

1. **P3 (Technical Debt — Phase 7)**: Attach `ClipDescription.EXTRA_IS_SENSITIVE` if message payload copying is implemented (`TASK-SEC-004`).
2. **P3 (Technical Debt — Phase 7)**: Implement auto-remasking timeout or `FLAG_SECURE` window protection during active payload unmasking (`TASK-SEC-005`).

---

## Memory Hub INDEX.md Row

```text
| docs/security-reviews/2026-09-13-feature-003-android-message-inspection.md | branch | 2026-09-13 | LOW | C:0 H:0 M:0 L:2 | A02,A04 |
```
