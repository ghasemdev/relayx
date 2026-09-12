---
document_type: security-review
review_type: followup
assessment_date: 2026-09-13
codebase_analyzed: /Volumes/ADATASD810/Projects/Android/relayx
total_files_analyzed: 14
total_findings: 2
overall_risk: LOW
critical_count: 0
high_count: 0
medium_count: 0
low_count: 2
informational_count: 1
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

# SECURITY REVIEW FOLLOW-UP PLAN — FEATURE 003: ANDROID MESSAGE INSPECTION & DETAIL MODAL

## Executive Summary
This follow-up plan evaluates security findings, technical debt, and confirmed remediations for **Phase 3 / Feature 003: Android Message Inspection & Detail Modal** (`feature/003-android-message-inspection`).

Previous foundational findings from Feature 002 (**SEC-001** backup exposure, **SEC-002** cleartext HTTP, and **SEC-003** server ADB newline injection) have all been **confirmed remediated and merged into `main`**.

Feature 003 introduces UI presentation and modal inspection of SMS outbox records. Overall risk is assessed as **LOW**. No high or medium vulnerabilities were discovered. Strict adherence to **Constitution Principle III** (Zero raw message bodies or OTPs in Logcat; default masking in UI) and **Principle V** (Idempotent delivery retries without UUID regeneration) is maintained. Two low-severity items are tracked as technical debt for future privacy hardening (clipboard sensitivity flag on Android 13+ and screenshot masking during app switcher transitions).

---

## Inputs Reviewed
- **Feature Specification**: [`specs/003-android-message-inspection/spec.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/specs/003-android-message-inspection/spec.md)
- **Architecture & Implementation Plan**: [`specs/003-android-message-inspection/plan.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/specs/003-android-message-inspection/plan.md)
- **Task Backlog**: [`specs/003-android-message-inspection/tasks.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/specs/003-android-message-inspection/tasks.md)
- **UI Contract**: [`contracts/message-inspection-ui.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/specs/003-android-message-inspection/contracts/message-inspection-ui.md)
- **Constitution**: [`.specify/memory/constitution.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/.specify/memory/constitution.md) (Principles II, III, IV, V)
- **Codebase Artifacts**:
  - `relayx-android/src/main/java/com/parsomash/relayx/ui/message/MessageDetailBottomSheet.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/ui/message/MessageListScreen.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/viewmodel/MessageListViewModel.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/data/local/OutboxMessageDao.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/ui/navigation/RelayNavGraph.kt`

---

## Resolution Decisions

| Finding ID | Title | Severity | Resolution | Rationale / Target |
|:---|:---|:---|:---|:---|
| **SEC-001** | Insecure Data Storage in AndroidManifest (`allowBackup`) | Medium | **Already covered** | Remediated in `main` (`android:allowBackup="false"`, `data_extraction_rules.xml`, `backup_rules.xml`). |
| **SEC-002** | Global Cleartext Traffic Permitted in Manifest | Low | **Already covered** | Remediated in `main` via `network_security_config.xml` restricting cleartext to `10.0.2.2`/localhost. |
| **SEC-003** | CR/LF Injection in Server ADB Relay Hook | Low | **Already covered** | Remediated in `main` via `SanitizeADBInput` stripping `\r` and `\n` in `relayx-server/internal/hook/hook.go`. |
| **SEC-004** | Clipboard Sensitivity Flag on Android 13+ (API 33+) | Low | **Track as technical debt** | Currently only message UUID is copied; if raw SMS body / OTP copying is added in future, `EXTRA_IS_SENSITIVE` must be flagged. |
| **SEC-005** | Ephemeral Screen Masking & Recents Thumbnail Exposure | Low | **Track as technical debt** | When raw payload is revealed via eye toggle, switching to Recents could expose text in snapshot. Target for Phase 7 hardening. |
| **SEC-006** | Manual Retry Request Spamming | Informational | **Already covered** | Deduplication enforced via `isRetrying` state guard and WorkManager `ExistingWorkPolicy.KEEP`. |

---

## Technical Debt Backlog

### TASK-SEC-004: Tag Sensitive Content in Android 13+ Clipboard Manager
- **Severity**: Low | **OWASP**: A02:2025-Cryptographic Failures | **CWE**: CWE-200: Exposure of Sensitive Information
- **Source Finding**: SEC-004
- **Target Files**:
  - `relayx-android/src/main/java/com/parsomash/relayx/ui/message/MessageDetailBottomSheet.kt`
- **Description**: Android 13 (API level 33) introduced a visual clipboard preview overlay. If users copy sensitive information, the OS provides an `EXTRA_IS_SENSITIVE` persistable bundle key to suppress plain text in the clipboard preview overlay (`ClipDescription.EXTRA_IS_SENSITIVE = true`). While the current copy button copies message UUIDs, any future extension allowing SMS body or OTP clipboard operations must apply this flag.
- **Why Safe to Defer**: Current UI only places the non-sensitive message UUID into the clipboard, not the OTP or message body.
- **Remaining Risk**: Minimal; UUID exposure poses no credential or authentication compromise risk.
- **Revisit Trigger**: Implementation of Phase 5 (Filter Rules / Export) or if SMS payload copying is added.
- **Milestone Target**: Phase 7 (Security Hardening).

### TASK-SEC-005: Window Privacy Protection (FLAG_SECURE / Auto-Remasking)
- **Severity**: Low | **OWASP**: A04:2025-Insecure Design | **CWE**: CWE-359: Exposure of Private Personal Information
- **Source Finding**: SEC-005
- **Target Files**:
  - `relayx-android/src/main/java/com/parsomash/relayx/ui/message/MessageDetailBottomSheet.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/MainActivity.kt`
- **Description**: When a user unmasks the raw SMS payload via the eye toggle in `MessageDetailBottomSheet`, the cleartext remains displayed indefinitely while the modal is open. If the user navigates away or switches apps, the Android system takes an app snapshot for the task switcher thumbnail.
- **Why Safe to Defer**: Payload is masked by default (`••••••••••••`). Unmasking requires explicit user interaction, and the bottom sheet dismisses upon navigating back or tapping outside.
- **Remaining Risk**: Shoulder-surfing or thumbnail capture if user leaves the app while payload is actively unmasked.
- **Revisit Trigger / Remediations**: Add an auto-remasking coroutine timer (e.g. 30s timeout) or conditionally toggle `WindowManager.LayoutParams.FLAG_SECURE` when payload is revealed.
- **Milestone Target**: Phase 7 (Security Hardening & Enterprise Vault).

---

## Confirmed Secure Patterns in Phase 3

1. **Constitution Principle III (Zero Sensitive Data in Logs & Default Masking)**:
   - SMS payload in [`MessageDetailBottomSheet.kt`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-android/src/main/java/com/parsomash/relayx/ui/message/MessageDetailBottomSheet.kt) defaults to masked state (`••••••••••••`) using monospace bullet masking.
   - List cards in [`MessageItemCard.kt`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-android/src/main/java/com/parsomash/relayx/ui/message/MessageItemCard.kt) render only sender, timestamp, and status badges; the raw message payload is never rendered in list views.
   - Zero message body logging in Logcat throughout all inspection and viewmodel routines.

2. **Constitution Principle V (Idempotency in Manual Retries)**:
   - Manual delivery retry in [`OutboxMessageDao.kt`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-android/src/main/java/com/parsomash/relayx/data/local/OutboxMessageDao.kt) via `resetForRetry(id, now)` updates existing outbox status to `PENDING` and resets attempt counters without mutating or regenerating the UUID.
   - Dispatched via [`MessageDispatchWorker`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-android/src/main/java/com/parsomash/relayx/data/worker/MessageDispatchWorker.kt) with `ExistingWorkPolicy.KEEP`, ensuring worker deduplication.

3. **Injection-Proof Local Data Access**:
   - Room SQLite queries use typed DAO interfaces with strict compile-time parameter binding (`WHERE id = :id`), preventing SQL injection.

4. **Modern, Configuration-Aware Compose Implementation**:
   - Eliminated deprecated `LocalClipboardManager` in favor of `LocalClipboard` with `ClipEntry`.
   - String resources resolved via `stringResource(...)` to ensure configuration awareness across orientation and locale changes.

---

## Prioritized Action Plan

| Priority | Task ID | Summary | Target Milestone |
|:---|:---|:---|:---|
| **P3 (Tech Debt)** | TASK-SEC-004 | Add `EXTRA_IS_SENSITIVE` to clipboard entries when copying bodies | Phase 7 (Hardening) |
| **P3 (Tech Debt)** | TASK-SEC-005 | Auto-remask payload timeout & app switcher thumbnail protection | Phase 7 (Hardening) |

---

## Memory Hub INDEX.md Row

```text
| docs/security-reviews/2026-09-13-feature-003-android-message-inspection-followup.md | followup | 2026-09-13 | LOW | C:0 H:0 M:0 L:2 | A02,A04 |
```
