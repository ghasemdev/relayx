---
document_type: security-review
review_type: branch
assessment_date: 2026-09-14
codebase_analyzed: /Volumes/ADATASD810/Projects/Android/relayx
total_files_analyzed: 31
total_findings: 3
overall_risk: LOW
critical_count: 0
high_count: 0
medium_count: 0
low_count: 3
informational_count: 0
owasp_categories: [A02, A04]
cwe_ids: [CWE-200, CWE-400, CWE-1333]
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

# SECURITY REVIEW REPORT — BRANCH: feature/005-android-rule-engine vs main

## Executive Summary
This targeted security review evaluated the code changes introduced in `feature/005-android-rule-engine` relative to `main` (commit `ce7f080`). The scope encompasses the device-side rule and pre-filtering engine, regex evaluation and OTP extraction, Room database schema migration (version 2), Compose UI rule management, and the interactive rule testing sandbox.

Overall risk is assessed as **LOW**. No critical, high, or medium severity vulnerabilities were identified. The implementation strictly complies with:
- **Constitution Principle IV (Device-Side Pre-Filtering & Secure Defaults)**: Incoming SMS is evaluated locally before network dispatch. The default policy is strictly `DROP`, and unmatched messages are stored with `status = FILTERED` without enqueueing WorkManager tasks.
- **Constitution Principle III (Strict Data Minimization & Privacy)**: Zero sensitive logging of message bodies or OTP secrets in `RelayLogger` or `RuleEngine`.
- **Room Persistence Security**: All queries in `RuleDao` use parameterized queries; migration `MIGRATION_1_2` safely introduces `filter_rules` with composite indexing.

Three low-severity hygiene and defensive hardening findings were identified regarding ReDoS mitigation, regex evaluation threading, and data minimization fallbacks.

---

## Branch Diff Reviewed
- **Target Branch**: `feature/005-android-rule-engine`
- **Base Commit**: `ce7f080` (prior to Phase 5 branch creation)
- **Codebase Files Changed**:
  - `relayx-android/src/main/java/com/parsomash/relayx/RelayApplication.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/data/local/RelayDatabase.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/data/local/RuleDao.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/data/local/RuleEntity.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/data/local/RuleRepositoryImpl.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/data/receiver/SmsReceiver.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/di/AppModule.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/domain/engine/RegexValidator.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/domain/engine/RuleEngine.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/domain/model/Rule.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/domain/model/RuleEvaluationResult.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/domain/repository/RuleRepository.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/domain/usecase/IngestSmsUseCase.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/domain/usecase/RuleUseCases.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/ui/dashboard/DashboardScreen.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/ui/navigation/RelayNavGraph.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/ui/rules/RulesScreen.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/ui/rules/components/RuleEditDialog.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/ui/rules/components/RuleItemCard.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/ui/rules/components/RuleSandboxCard.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/ui/theme/Color.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/viewmodel/DashboardViewModel.kt`
  - `relayx-android/src/main/java/com/parsomash/relayx/viewmodel/RulesViewModel.kt`
  - `relayx-android/src/main/res/values/strings.xml`
  - `relayx-android/src/test/java/com/parsomash/relayx/data/local/FakeRuleDao.kt`
  - `relayx-android/src/test/java/com/parsomash/relayx/data/local/RuleDaoTest.kt`
  - `relayx-android/src/test/java/com/parsomash/relayx/domain/engine/RegexTransformationTest.kt`
  - `relayx-android/src/test/java/com/parsomash/relayx/domain/engine/RuleEngineTest.kt`
  - `relayx-android/src/test/java/com/parsomash/relayx/domain/usecase/IngestSmsUseCaseTest.kt`
  - `relayx-android/src/test/java/com/parsomash/relayx/viewmodel/DashboardViewModelTest.kt`
  - `relayx-android/src/test/java/com/parsomash/relayx/viewmodel/RulesViewModelTest.kt`

---

## Vulnerability Findings

### [LOW] SEC-014: Unbounded Regex Pattern Input and Lack of ReDoS Guardrails
**Location:** `relayx-android/src/main/java/com/parsomash/relayx/domain/engine/RegexValidator.kt:11-20`  
**OWASP Category:** A04:2025 - Insecure Design  
**CWE:** CWE-1333: Ineffective Regular Expression Complexity  
**CVSS v3.1:** 3.3 (AV:L/AC:L/PR:L/UI:R/S:U/C:N/I:N/A:L)  
**Description:** `RegexValidator.validatePattern(pattern)` validates regex syntax by attempting compilation `Regex(pattern)`. However, neither `RegexValidator` nor `RuleEditDialog` enforces a maximum length boundary on regex patterns or guards against catastrophic backtracking patterns (e.g., nested quantifiers `(a+)+$`). If a user inputs a pathological regular expression, matching long SMS messages could lead to high CPU consumption.  
**Remediation:** Enforce a maximum length limit (e.g. 256 characters) on regex pattern inputs in `RuleEditDialog` and `SaveRuleUseCase`, and consider wrapping regex execution with timeout safeguards.  
**Spec-Kit Task:** TASK-SEC-014  

---

### [LOW] SEC-015: Inadvertent Full Body Fallback on Empty Regex in Transformed Rules
**Location:** `relayx-android/src/main/java/com/parsomash/relayx/domain/engine/RuleEngine.kt:41-48`  
**OWASP Category:** A02:2025 - Cryptographic & Data Exposure Failures  
**CWE:** CWE-200: Exposure of Sensitive Information to an Unauthorized Actor  
**CVSS v3.1:** 3.1 (AV:L/AC:H/PR:L/UI:N/S:U/C:L/I:N/A:N)  
**Description:** When a rule specifies `RuleAction.FORWARD_TRANSFORMED` but neither `transformPattern` nor `contentPattern` is configured or valid, `RuleEngineImpl` defaults `extracted` to `body` (the raw SMS body). This means a misconfigured transformed rule would transmit the entire unmasked SMS body rather than failing or dropping the message.  
**Remediation:** If `rule.action == RuleAction.FORWARD_TRANSFORMED` and no pattern is configured or extraction produces no match, do not fallback to `body`. Instead, treat it as an extraction failure or drop the message to preserve data minimization.  
**Spec-Kit Task:** TASK-SEC-015  

---

### [LOW] SEC-016: Sandbox Test Coroutine Executes on Main Dispatcher
**Location:** `relayx-android/src/main/java/com/parsomash/relayx/domain/usecase/RuleUseCases.kt:77-80`  
**OWASP Category:** A04:2025 - Insecure Design  
**CWE:** CWE-400: Uncontrolled Resource Consumption  
**CVSS v3.1:** 2.8 (AV:L/AC:L/PR:L/UI:N/S:U/C:N/I:N/A:L)  
**Description:** `TestRulesUseCase` executes `ruleEngine.evaluate(...)` in the caller's coroutine context. In `RulesViewModel.onEvent(RulesUiEvent.RunTest)`, the call originates inside `viewModelScope.launch`, which runs on `Dispatchers.Main.immediate`. When evaluating multiple rules with complex regexes against sample texts, executing on the Main thread can cause UI frame drops or ANRs.  
**Remediation:** Inject `AppDispatchers` into `TestRulesUseCase` and confine the evaluation to `withContext(dispatchers.default)`.  
**Spec-Kit Task:** TASK-SEC-016  

---

## Confirmed Secure Patterns

1. **Strict Device-Side Pre-Filtering (`RuleEngine` & `IngestSmsUseCase`)**:
   - Default filter policy is unconditionally `DROP` when no active rule matches.
   - Dropped messages are assigned `status = FILTERED`.
   - `SmsReceiver` only enqueues `MessageDispatchWorker` if `ingested.status == DeliveryStatus.PENDING`.
2. **Data Minimization in Network Dispatch (`DispatchOutboxUseCase`)**:
   - `body = entity.transformedBody ?: entity.rawBody` guarantees that only the extracted verification code/OTP is transmitted when transformed rules match.
3. **Zero Sensitive Logging**:
   - `RuleEngine` logs only static rule names, priorities, and action enums.
   - All app logging passes through `RelayLogger`, which actively redacts OTP digits and authorization tokens.
4. **Parameterized Persistence (`RuleDao`)**:
   - All Room queries use parameterized SQL; no raw string concatenations or dynamic SQL injection risks.

---

## Action Plan & Follow-ups

| Priority | Task ID | Remediation Action | Target Milestone |
|:---|:---|:---|:---|
| 1 | `TASK-SEC-015` | Disallow raw body fallback when `FORWARD_TRANSFORMED` pattern is blank or fails extraction | Phase 5 Polish / Phase 7 |
| 2 | `TASK-SEC-016` | Confine `TestRulesUseCase` to `Dispatchers.Default` | Phase 5 Polish / Phase 7 |
| 3 | `TASK-SEC-014` | Add pattern length validation in `RegexValidator` and `SaveRuleUseCase` | Phase 7 Security Hardening |
