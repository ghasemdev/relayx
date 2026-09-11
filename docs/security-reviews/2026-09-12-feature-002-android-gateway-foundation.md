---
document_type: security-review
review_type: branch
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

# SECURITY REVIEW REPORT — BRANCH: feature/002-android-gateway-foundation vs develop

## Executive Summary
This targeted security review analyzed the code changes introduced in `feature/002-android-gateway-foundation` relative to `develop`. The scope encompasses the core Android gateway implementation (`relayx-android`), including Room database persistence, BroadcastReceivers, WorkManager background dispatch, Ktor HTTP client, Koin DI configuration, and the companion server-side relay hooks (`relayx-server`).

Overall risk is assessed as **MODERATE** due to Android backup configuration exposing local outbox databases and credentials (`android:allowBackup="true"`), alongside globally permitted cleartext HTTP traffic. No critical remote code execution or authentication bypass vulnerabilities were identified. Strong privacy-preserving logging controls and broadcast permission restrictions are actively enforced.

## Branch Diff Reviewed
- **Target**: `feature/002-android-gateway-foundation`
- **Base**: `develop`
- **Files Changed**:
  - `relayx-android/src/main/AndroidManifest.xml`
  - `relayx-android/src/main/java/com/parsomash/relayx/RelayApplication.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/MainActivity.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/data/local/` (`OutboxMessageDao.kt`, `OutboxMessageEntity.kt`, `PreferencesRepository.kt`, `RelayDatabase.kt`)
  - `relayx-android/src/main/java/com/parsomash/relayx/data/receiver/` (`SmsReceiver.kt`, `BootReceiver.kt`)
  - `relayx-android/src/main/java/com/parsomash/relayx/data/remote/` (`ApiConstants.kt`, `RelayServerClient.kt`, `NetworkDtos.kt`)
  - `relayx-android/src/main/java/com/parsomash/relayx/data/worker/MessageDispatchWorker.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/di/AppModule.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/util/` (`RelayLogger.kt`, `AppDispatchers.kt`)
  - `relayx-android/src/main/java/com/parsomash/relayx/viewmodel/` (`DashboardViewModel.kt`, `SettingsViewModel.kt`)
  - `relayx-server/internal/hook/` (`hook.go`, `hook_test.go`)
  - `relayx-server/internal/service/message_service.go`

---

## Vulnerability Findings

### [MEDIUM] SEC-001: Insecure Data Storage & Backup Exposure in AndroidManifest
- **Finding ID**: SEC-001
- **Location**: `relayx-android/src/main/AndroidManifest.xml:17`
- **OWASP Category**: A02:2025-Cryptographic Failures / OWASP Mobile M02: Insecure Data Storage
- **CWE**: CWE-312: Cleartext Storage of Sensitive Information
- **CVSS v3.1 Score**: 5.5 (`CVSS:3.1/AV:P/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N`)
- **Description**: In `AndroidManifest.xml`, `android:allowBackup="true"` is declared. The application stores incoming SMS messages in a Room SQLite database (`relayx.db`) and Bearer authentication tokens in Jetpack DataStore (`gateway_preferences.preferences_pb`). Because backups are allowed without explicit exclusions in `data_extraction_rules.xml` or `backup_rules.xml`, an attacker with physical USB access (via `adb backup`) or device cloud backup sync can extract raw SMS communications and authentication credentials.
- **Remediation**: Set `android:allowBackup="false"` in `AndroidManifest.xml`. If backup is required, configure `@xml/data_extraction_rules` and `@xml/backup_rules` to explicitly exclude the database and datastore preference directories.
- **Spec-Kit Task**: `TASK-SEC-001`

### [LOW] SEC-002: Global Cleartext Traffic Permitted in Android Manifest
- **Finding ID**: SEC-002
- **Location**: `relayx-android/src/main/AndroidManifest.xml:25`
- **OWASP Category**: A05:2025-Security Misconfiguration / OWASP Mobile M03: Insecure Communication
- **CWE**: CWE-319: Cleartext Transmission of Sensitive Information
- **CVSS v3.1 Score**: 3.8 (`CVSS:3.1/AV:A/AC:L/PR:N/UI:N/S:U/C:L/I:N/A:N`)
- **Description**: The application declares `android:usesCleartextTraffic="true"` on the `<application>` tag. While intended for local emulator testing against `10.0.2.2:8080`, leaving this enabled globally permits unencrypted HTTP transmission across all network interfaces. In production or LAN environments, SMS contents and Bearer tokens can be intercepted by adversaries on local Wi-Fi networks.
- **Remediation**: Create a dedicated `res/xml/network_security_config.xml` that permits cleartext only for localhost / emulator IP addresses (`10.0.2.2`, `127.0.0.1`, `localhost`), and disable global cleartext traffic (`android:usesCleartextTraffic="false"`) or enforce HTTPS for non-loopback endpoints.
- **Spec-Kit Task**: `TASK-SEC-002`

### [LOW] SEC-003: Potential Emulator Telnet Protocol Injection via Newlines in ADB Hook
- **Finding ID**: SEC-003
- **Location**: `relayx-server/internal/hook/hook.go:59`
- **OWASP Category**: A03:2025-Injection
- **CWE**: CWE-93: Improper Neutralization of CRLF Sequences ('CRLF Injection')
- **CVSS v3.1 Score**: 3.3 (`CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:N/I:L/A:N`)
- **Description**: In `runADB`, the server executes `adb -s <target> emu sms send <sender> <body>`. Although `os/exec` directly passes argv arguments without spawning a shell (preventing OS command injection), the Android Emulator console service communicates internally via a line-based telnet protocol. If an unvalidated SMS body or sender contains carriage return or newline characters (`\r` or `\n`), it could inject unintended commands into the emulator console socket.
- **Remediation**: Strip or replace newline characters (`\r`, `\n`) from `msg.Sender` and `msg.Body` before invoking `adb emu sms send`.
- **Spec-Kit Task**: `TASK-SEC-003`

---

## Confirmed Secure Patterns

1. **SMS Broadcast Hijack Protection**:
   `SmsReceiver` in `AndroidManifest.xml` is guarded with `android:permission="android.permission.BROADCAST_SMS"`. This restricts incoming broadcasts strictly to the Android OS telephony stack, preventing third-party malicious apps from spoofing SMS ingest events.

2. **Constitution Principle III (Zero Sensitive Logging)**:
   [`RelayLogger.kt`](file:///Volumes/ADATASD810/Projects/Android/relayx/relayx-android/src/main/java/com/parsomash/relayx/util/RelayLogger.kt) systematically redacts OTP verification codes and Bearer tokens (`OTP_PATTERN` and `BEARER_PATTERN`). Logcat outputs in `SmsReceiver`, `MessageDispatchWorker`, and `RelayServerClient` only record metadata (message UUID, length in bytes, segment count, delivery status code), with zero SMS body leakage.

3. **Idempotent Ingestion (Constitution Principle V)**:
   All messages carry client-generated unique IDs (`kotlin.uuid.Uuid`). Both the Android Room outbox database and the Go server database enforce primary key uniqueness on `message_id`, preventing duplicate submissions during network retries.

4. **Battery-Aware Resilient Dispatch**:
   WorkManager's `MessageDispatchWorker` utilizes `NetworkType.CONNECTED` constraints and exponential backoff retry criteria, preventing battery exhaustion during server downtime or loss of connectivity.

---

## Prioritized Action Plan

1. **P1 (Immediate)**: Set `android:allowBackup="false"` in `relayx-android/src/main/AndroidManifest.xml` (`TASK-SEC-001`).
2. **P2 (Hardening)**: Implement `network_security_config.xml` to restrict cleartext HTTP strictly to `10.0.2.2` and loopback addresses (`TASK-SEC-002`).
3. **P3 (Sanitization)**: Sanitize CR/LF characters in `relayx-server/internal/hook/hook.go` prior to forwarding via `adb emu sms send` (`TASK-SEC-003`).

---

## Memory Hub INDEX.md Row

```text
| docs/security-reviews/2026-09-12-feature-002-android-gateway-foundation.md | branch | 2026-09-12 | MODERATE | C:0 H:0 M:1 L:2 | A02,A03,A05 |
```
