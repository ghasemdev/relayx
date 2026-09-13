# Implementation Plan: Android Rule & Filtering Engine

**Branch**: `feature/005-android-rule-engine` | **Date**: 2026-09-14 | **Spec**: [spec.md](file:///Volumes/ADATASD810/Projects/Android/relayx/specs/005-android-rule-engine/spec.md)

**Input**: Feature specification from [`specs/005-android-rule-engine/spec.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/specs/005-android-rule-engine/spec.md)

---

## Summary

Implement the client-side pre-filtering and rule transformation engine for `relayx-android`. Intercepts all incoming SMS messages (both real SIM and developer mock SMS) on the Android device *prior* to network dispatch. Enforces the secure default policy (`DROP`) so that unmatched messages remain strictly on the device. Supports deterministic priority evaluation, sender matching (exact, prefix, contains, regex, any), content regex extraction (`FORWARD_TRANSFORMED`), Room persistence, a dedicated Compose Rule Management UI in top-level navigation, and an interactive testing sandbox.

---

## Technical Context

**Language/Version**: Kotlin 2.1.20 / Android SDK 37 (minSdk 24)  
**Primary Dependencies**: Jetpack Compose (Material 3), Navigation 3, Koin 4.2 (with annotations), AndroidX Room 2.7, Coroutines & StateFlow  
**Storage**: Android Room SQLite (`relayx_gateway.db`, table `filter_rules`, version 2)  
**Testing**: JUnit 4, Kotlinx Coroutines Test, MockK, AndroidX Room Test  
**Target Platform**: Android 7.0 (API 24) through Android 15+ (API 37)  
**Project Type**: Android Native Application Module (`relayx-android`)  
**Performance Goals**: Sub-millisecond rule evaluation (< 1ms per SMS) for up to 100 active rules without blocking SMS receiver broadcast thread  
**Constraints**: Zero sensitive logging (no raw OTP or message bodies recorded in logs); offline-first persistence; offline rule evaluation before network dispatch  
**Scale/Scope**: 1-50 active rules per gateway; deterministic first-match evaluation; 1 dedicated Compose screen + dialogs  

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status | Notes |
|:---|:---|:---|:---|
| **P-01 (Local-First)** | Pure local Android evaluation; no server dependency for filtering | **PASS** | Evaluated on-device. |
| **P-02 (Security Domains)** | No change to network auth credentials | **PASS** | Operates before network layer. |
| **P-03 (Strict Privacy)** | No raw bodies or OTPs recorded in Android logs during rule matching | **PASS** | Enforced via `RelayLogger` masking. |
| **P-04 (Device-Side Pre-Filtering)** | Default `DROP` policy; unapproved SMS never leaves the device | **PASS** | Core goal of Phase 5. |
| **P-05 (Durable Delivery)** | Rules and outbox decisions stored atomically in Room | **PASS** | Persisted across reboots. |
| **P-06 (Event-Driven MCP)** | Downstream server/agent unaffected | **PASS** | Server receives filtered/clean payloads. |
| **P-07 (Mock SMS Parity)** | Mock SMS traverses the exact same `RuleEngine` pipeline | **PASS** | Shared ingestion path. |
| **P-08 (Clean Architecture)** | Clean domain models, repository pattern, Koin DI, Compose M3 | **PASS** | Matches project standard. |
| **P-09 (Safety Limits)** | No OS permission bypasses; standard broadcast reception | **PASS** | Standard Android SMS permissions. |

---

## Project Structure

### Documentation (this feature)

```text
specs/005-android-rule-engine/
├── memory.md            # Active feature context & governance constraints
├── memory-synthesis.md  # Synthesized AI guidance
├── spec.md              # Feature specification & user stories (P1-P5)
├── plan.md              # This implementation plan
├── research.md          # Phase 0 architectural decisions & trade-offs
├── data-model.md        # Phase 1 domain entities & Room schema
├── quickstart.md        # Phase 1 developer verification guide
├── contracts/           # Phase 1 interface contracts
│   └── rule-engine-contracts.md
└── tasks.md             # Phase 2 actionable task breakdown (via /speckit-tasks)
```

### Source Code (repository root)

```text
relayx-android/src/
├── main/java/com/parsomash/relayx/
│   ├── data/
│   │   └── local/
│   │       ├── RelayDatabase.kt              # Bump to version 2, add RuleEntity & MIGRATION_1_2
│   │       ├── RuleEntity.kt                 # Room entity for filter_rules table
│   │       ├── RuleDao.kt                    # Room DAO for filter rules
│   │       └── RuleRepositoryImpl.kt         # Concrete implementation of RuleRepository
│   ├── domain/
│   │   ├── engine/
│   │   │   ├── RuleEngine.kt                 # Evaluation engine interface & implementation
│   │   │   └── RegexValidator.kt             # Safe regex validation and group extraction
│   │   ├── model/
│   │   │   ├── Rule.kt                       # Domain models: Rule, RuleAction, SenderMatchType
│   │   │   └── RuleEvaluationResult.kt       # Evaluation result wrapper
│   │   ├── repository/
│   │   │   └── RuleRepository.kt             # Domain interface for rules repository
│   │   └── usecase/
│   │       ├── IngestSmsUseCase.kt           # Integrated with RuleEngine before outbox insertion
│   │       ├── GetRulesUseCase.kt            # Flow<List<Rule>>
│   │       ├── SaveRuleUseCase.kt            # Validate & save rule
│   │       ├── DeleteRuleUseCase.kt          # Delete rule
│   │       └── TestRulesUseCase.kt           # Execute sandbox test
│   ├── di/
│   │   └── AppModule.kt                      # Provide RuleDao and RuleRepository
│   ├── ui/
│   │   ├── navigation/
│   │   │   └── RelayNavGraph.kt              # Add AppRoute.Rules to BottomNav & NavDisplay
│   │   └── rules/
│   │       ├── RulesScreen.kt                # Compose screen: rule list, active toggle, sandbox
│   │       ├── components/
│   │       │   ├── RuleItemCard.kt           # Rule item card with enable switch & badges
│   │       │   ├── RuleEditDialog.kt         # Create/Edit rule modal dialog
│   │       │   └── RuleSandboxCard.kt        # Interactive test sandbox
│   │       └── RulesViewModel.kt             # MVI state holder for rules & sandbox
│   └── util/
│       └── RelayLogger.kt                    # Zero-leakage rule execution logging
└── test/java/com/parsomash/relayx/
    ├── domain/
    │   ├── engine/
    │   │   └── RuleEngineTest.kt             # Comprehensive tests: EXACT, PREFIX, REGEX, DROP, TRANSFORM
    │   └── usecase/
    │       └── IngestSmsUseCaseTest.kt       # Tests verifying FILTERED status on drop
    └── data/
        └── local/
            └── RuleDaoTest.kt                # Room DAO & ordering tests
```

**Structure Decision**: Clean Architecture layers within `relayx-android`:
- `domain/engine`: Pure business logic for rule matching and OTP extraction.
- `data/local`: Room entity, DAO, and repository implementation.
- `ui/rules`: Modern Compose Material 3 screens, cards, and ViewModel.

---

## Complexity Tracking

*No constitutional violations or unjustified complexity. Clean modular implementation utilizing existing Room, Koin, and Compose infrastructure.*
