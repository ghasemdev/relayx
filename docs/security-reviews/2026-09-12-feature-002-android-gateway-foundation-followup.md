---
document_type: security-review
review_type: followup
assessment_date: 2026-09-12
codebase_analyzed: /Volumes/ADATASD810/Projects/Android/relayx
total_files_analyzed: 28
total_findings: 3
overall_risk: MODERATE
critical_count: 0
high_count: 0
medium_count: 1
low_count: 2
informational_count: 0
owasp_categories: [A02, A03, A05]
cwe_ids: [CWE-312, CWE-319, CWE-93]
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

# SECURITY REVIEW FOLLOW-UP PLAN — FEATURE 002: ANDROID GATEWAY FOUNDATION

## Executive Summary
This follow-up plan converts findings from the branch security review (`2026-09-12-feature-002-android-gateway-foundation.md`) into an actionable remediation schedule. 

Three findings were assessed:
1. **SEC-001 [MEDIUM]**: Insecure local data storage via `android:allowBackup="true"`.
2. **SEC-002 [LOW]**: Globally enabled cleartext HTTP traffic.
3. **SEC-003 [LOW]**: CR/LF command injection vector in server-side emulator ADB relay hook.

All three findings are scoped into clear, actionable remediation tasks. No finding requires architectural redesign, and all are ready for immediate implementation or staged application before feature branch merge.

---

## Inputs Reviewed
- **Security Review Report**: [`docs/security-reviews/2026-09-12-feature-002-android-gateway-foundation.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/security-reviews/2026-09-12-feature-002-android-gateway-foundation.md)
- **Task Backlog**: [`specs/002-android-gateway-foundation/tasks.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/specs/002-android-gateway-foundation/tasks.md)
- **Constitution**: [`.specify/memory/constitution.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/.specify/memory/constitution.md) (Principles II, III, IV, V)
- **Codebase Scope**: `relayx-android` (Manifest, Room, DataStore, Receivers) and `relayx-server` (ADB Hook)

---

## Resolution Decisions

| Finding ID | Title | Severity | Resolution | Rationale / Target |
|:---|:---|:---|:---|:---|
| **SEC-001** | Insecure Data Storage in AndroidManifest (`allowBackup`) | Medium | **Implement now** | Trivial fix with high impact; disabling backup prevents physical/cloud data dumps of raw SMS outbox and bearer tokens. |
| **SEC-002** | Global Cleartext Traffic Permitted in Manifest | Low | **Implement now** | Adding `network_security_config.xml` allows local dev (`10.0.2.2`, `127.0.0.1`) while enforcing HTTPS for all production remote hosts. |
| **SEC-003** | CR/LF Injection in Server ADB Relay Hook | Low | **Implement now** | Sanitizing newline characters in `msg.Sender` and `msg.Body` prevents telnet injection attacks against the Android emulator console. |

---

## Actionable Follow-Up Tasks

### TASK-SEC-001: Disable Application Backup and Exclude Sensitive Local Stores
- **Severity**: Medium | **OWASP**: A02:2025-Cryptographic Failures | **CWE**: CWE-312
- **Source Finding**: SEC-001
- **Target Files**:
  - `relayx-android/src/main/AndroidManifest.xml`
  - `relayx-android/src/main/res/xml/data_extraction_rules.xml`
  - `relayx-android/src/main/res/xml/backup_rules.xml`
- **Description**: Set `android:allowBackup="false"` in `AndroidManifest.xml`. Update `data_extraction_rules.xml` and `backup_rules.xml` to explicitly exclude `databases/` (`relayx.db`) and `datastore/` (`gateway_preferences.preferences_pb`) from any device transfer or cloud backup mechanisms.
- **Acceptance Criteria**:
  1. `android:allowBackup` is set to `false`.
  2. Automated build and APK verification pass.
  3. No application databases or preferences are extracted during `adb backup`.

### TASK-SEC-002: Restrict Cleartext Traffic via Network Security Config
- **Severity**: Low | **OWASP**: A05:2025-Security Misconfiguration | **CWE**: CWE-319
- **Source Finding**: SEC-002
- **Target Files**:
  - `relayx-android/src/main/AndroidManifest.xml`
  - `relayx-android/src/main/res/xml/network_security_config.xml`
- **Description**: Create `res/xml/network_security_config.xml` that configures `base-config cleartextTrafficPermitted="false"` while explicitly whitelisting local development domains (`10.0.2.2`, `localhost`, `127.0.0.1`) under `domain-config cleartextTrafficPermitted="true"`. Remove `android:usesCleartextTraffic="true"` from the manifest.
- **Acceptance Criteria**:
  1. Manifest references `android:networkSecurityConfig="@xml/network_security_config"`.
  2. Cleartext HTTP is permitted solely to emulator loopback (`10.0.2.2`) and localhost.
  3. Non-local servers require HTTPS by default.

### TASK-SEC-003: Sanitize Newline Characters in ADB Relay Hook
- **Severity**: Low | **OWASP**: A03:2025-Injection | **CWE**: CWE-93
- **Source Finding**: SEC-003
- **Target Files**:
  - `relayx-server/internal/hook/hook.go`
  - `relayx-server/internal/hook/hook_test.go`
- **Description**: In `runADB`, sanitize `msg.Sender` and `msg.Body` by stripping or escaping carriage return (`\r`) and newline (`\n`) characters prior to passing them as arguments to `adb emu sms send`. Prevent logging full command output on failure to avoid leaking sensitive SMS text in server logs.
- **Acceptance Criteria**:
  1. Senders or message bodies containing `\r\n` are sanitized into single-line strings before emulator dispatch.
  2. Unit tests verify CRLF injection attempts are neutralized.
  3. `go test ./...` passes.

---

## Backlog-Ready Task Summary Table

| Task ID | Title | Severity | Type | Source Finding | Depends On | Acceptance Criteria |
|:---|:---|:---|:---|:---|:---|:---|
| **TASK-SEC-001** | Disable App Backup & Protect Local SQLite/DataStore | Medium | Implement | SEC-001 | None | `allowBackup="false"` in manifest; verification build passes |
| **TASK-SEC-002** | Scoped Network Security Config for Local vs Remote | Low | Implement | SEC-002 | None | `network_security_config.xml` whitelists `10.0.2.2`, blocks external cleartext |
| **TASK-SEC-003** | Sanitize CR/LF in ADB Emulator Relay Hook | Low | Implement | SEC-003 | None | Strip newlines in `hook.go`; unit tests pass |

---

## Confirmed Secure Patterns Retained
- **`android.permission.BROADCAST_SMS` Protection**: Preserved in `SmsReceiver`.
- **Zero Sensitive Logging**: Preserved via `RelayLogger` regex sanitization and metadata-only log outputs.
- **Idempotent Ingestion**: Guaranteed by client-side unique UUIDs and database unique constraints.
- **Battery-Aware WorkManager Dispatch**: Retained with network constraints and exponential backoff.

---

## Memory Hub INDEX.md Row

```text
| docs/security-reviews/2026-09-12-feature-002-android-gateway-foundation-followup.md | followup | 2026-09-12 | MODERATE | C:0 H:0 M:1 L:2 | A02,A03,A05 |
```
