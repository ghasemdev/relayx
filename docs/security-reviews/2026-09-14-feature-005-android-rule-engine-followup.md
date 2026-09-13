---
document_type: security-review
review_type: followup
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

# SECURITY REVIEW FOLLOW-UP PLAN — FEATURE 005: ANDROID RULE & FILTERING ENGINE

## Executive Summary
This follow-up plan evaluates security findings, technical debt, and remediation scheduling for **Phase 5 / Feature 005: Android Rule & Filtering Engine** (`feature/005-android-rule-engine`), based on the branch security review conducted on 2026-09-14.

Overall risk is assessed as **LOW**. No critical, high, or medium severity vulnerabilities were identified. The core filtering engine successfully enforces **Constitution Principle IV** (Device-Side Pre-Filtering & Secure Defaults) and **Principle III** (Strict Data Minimization & Privacy).

Out of the 3 low-severity findings:
- **2 findings are scheduled for immediate remediation** (`TASK-SEC-015`: Prevent full raw body fallback when transformed regex is blank/unmatched, and `TASK-SEC-016`: Confine sandbox test coroutines to `Dispatchers.Default`).
- **1 finding is tracked as technical debt** (`TASK-SEC-014`: Unbounded regex length input validation, deferred to Phase 7 Security Hardening).

---

## Inputs Reviewed
- **Feature Specification**: [`specs/005-android-rule-engine/spec.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/specs/005-android-rule-engine/spec.md)
- **Implementation Plan**: [`specs/005-android-rule-engine/plan.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/specs/005-android-rule-engine/plan.md)
- **Task Breakdown**: [`specs/005-android-rule-engine/tasks.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/specs/005-android-rule-engine/tasks.md)
- **Branch Security Review**: [`docs/security-reviews/2026-09-14-feature-005-android-rule-engine.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/security-reviews/2026-09-14-feature-005-android-rule-engine.md)
- **Constitution**: [`.specify/memory/constitution.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/.specify/memory/constitution.md) (Principles III, IV, VII)

---

## Resolution Decisions

### 1. `SEC-015` / `TASK-SEC-015`: Disallow Raw Body Fallback in `FORWARD_TRANSFORMED` Rules
- **Outcome**: `Implement now`
- **Rationale**: Strict data minimization is a core architectural invariant (Constitution Principle IV). If a user configures a rule with `FORWARD_TRANSFORMED`, transmitting the entire unmasked raw SMS body due to a missing or non-matching regex violates user privacy expectations. When extraction produces no match or no pattern exists, the engine should skip the rule or drop the message rather than falling back to the raw body.

### 2. `SEC-016` / `TASK-SEC-016`: Confine Sandbox Test Coroutine to `Dispatchers.Default`
- **Outcome**: `Implement now`
- **Rationale**: Quick hygiene fix. In `RulesViewModel`, `viewModelScope.launch` defaults to `Dispatchers.Main.immediate`. `TestRulesUseCase` should explicitly switch to `Dispatchers.Default` via `AppDispatchers` to prevent CPU-intensive regex matching from blocking the Android UI thread.

### 3. `SEC-014` / `TASK-SEC-014`: Enforce Maximum Regex Pattern Length Boundaries
- **Outcome**: `Track as technical debt`
- **Why safe to defer**: Regex patterns are only input by the device owner through the on-device Compose UI. There is no remote multi-tenant attack vector or unauthenticated external input source configuring rules.
- **Residual risk**: An accidental or experimental pathological pattern (nested quantifiers like `(a+)+$`) could cause high CPU utilization during matching.
- **Revisit trigger**: Phase 7 Security Hardening & Polish, or when remote rule synchronization / export-import is introduced.
- **Target milestone**: Phase 7.

---

## Backlog-Ready Tasks

| Task ID | Title | Severity | Type | Source Finding | Depends On | Acceptance Criteria |
|:---|:---|:---|:---|:---|:---|:---|
| `TASK-SEC-015` | Prevent raw body fallback when `FORWARD_TRANSFORMED` regex is blank or unmatched | Low | Implement | SEC-015 | None | If `rule.action == FORWARD_TRANSFORMED` and extraction fails, do not return raw `body`; fall through or drop; verified by unit test in `RuleEngineTest`. |
| `TASK-SEC-016` | Confine `TestRulesUseCase` execution to `Dispatchers.Default` | Low | Implement | SEC-016 | None | `TestRulesUseCase` injects `AppDispatchers` and evaluates `ruleEngine.evaluate` with `withContext(dispatchers.default)`. |
| `TASK-SEC-014` | Add pattern length validation in `RegexValidator` and `SaveRuleUseCase` | Low | Technical Debt | SEC-014 | None | Enforce maximum 256 chars on regex pattern input; revisit during Phase 7 hardening. |

---

## Immediate Remediation Plan

### Remediation Step 1: Fix `FORWARD_TRANSFORMED` Fallback in `RuleEngine.kt`
In `relayx-android/src/main/java/com/parsomash/relayx/domain/engine/RuleEngine.kt`:
```kotlin
RuleAction.FORWARD_TRANSFORMED -> {
    val pattern = rule.transformPattern?.takeIf { it.isNotBlank() }
        ?: rule.contentPattern?.takeIf { it.isNotBlank() }

    val extracted = if (pattern != null) {
        RegexValidator.extract(pattern, body)
    } else {
        null // Do not fallback to raw body when transformation is explicitly requested!
    }

    if (!extracted.isNullOrBlank()) {
        return RuleEvaluationResult(
            matchedRule = rule,
            action = RuleAction.FORWARD_TRANSFORMED,
            transformedBody = extracted,
            isDropped = false,
            executionLog = "Matched rule '${rule.name}' with FORWARD_TRANSFORMED"
        )
    }
    // Fall through if extraction produced nothing
}
```

### Remediation Step 2: Confine `TestRulesUseCase` to `Dispatchers.Default`
In `relayx-android/src/main/java/com/parsomash/relayx/domain/usecase/RuleUseCases.kt`:
```kotlin
@Factory
class TestRulesUseCase(
    private val ruleEngine: RuleEngine,
    private val ruleRepository: RuleRepository,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend operator fun invoke(sender: String, body: String): RuleEvaluationResult =
        withContext(dispatchers.default) {
            val activeRules = ruleRepository.getActiveRulesDirect()
            ruleEngine.evaluate(sender, body, activeRules)
        }
}
```

---

## Technical Debt Backlog

### TD-SEC-014: Enforce Pattern Length and ReDoS Limits
- **Tracking ID**: `TASK-SEC-014`
- **Location**: `relayx-android/src/main/java/com/parsomash/relayx/domain/engine/RegexValidator.kt`
- **Severity**: Low (CVSS 3.3)
- **Target Milestone**: Phase 7 Security Hardening
- **Revisit Trigger**: Implementation of rule import/export or remote rule configuration.

---

## Confirmed Secure Patterns
1. **Local-First Default Drop**: All messages lacking an explicit allow rule are dropped locally with `status = FILTERED`.
2. **Zero Sensitive Logging**: OTP codes and sensitive auth tokens are never written to logcat or persisted in plaintext telemetry.
3. **Room Parameterization**: Zero dynamic SQL concatenation in Room queries.
