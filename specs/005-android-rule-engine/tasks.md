# Tasks: Android Rule & Filtering Engine

**Feature**: `specs/005-android-rule-engine`  
**Date**: 2026-09-14  
**Plan**: [`specs/005-android-rule-engine/plan.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/specs/005-android-rule-engine/plan.md)  
**Spec**: [`specs/005-android-rule-engine/spec.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/specs/005-android-rule-engine/spec.md)  

---

## Phase 1: Setup & Data Model Foundations

**Purpose**: Define the core domain entities, enums, and evaluation structures.

- [x] T001 [P] Define `RuleAction` and `SenderMatchType` enums in `relayx-android/src/main/java/com/parsomash/relayx/domain/model/Rule.kt`
- [x] T002 [P] Define `Rule` domain model in `relayx-android/src/main/java/com/parsomash/relayx/domain/model/Rule.kt`
- [x] T003 [P] Define `RuleEvaluationResult` in `relayx-android/src/main/java/com/parsomash/relayx/domain/model/RuleEvaluationResult.kt`
- [x] T004 Define `RegexValidator` utility with syntax validation and group extraction in `relayx-android/src/main/java/com/parsomash/relayx/domain/engine/RegexValidator.kt`

---

## Phase 2: Foundational (Storage & Room Persistence)

**Purpose**: Establish database tables, DAO, repository, and migration before user stories execute.

- [x] T005 Create `RuleEntity` with composite index `[priority, enabled]` in `relayx-android/src/main/java/com/parsomash/relayx/data/local/RuleEntity.kt`
- [x] T006 Create `RuleDao` with reactive flows and direct suspend queries in `relayx-android/src/main/java/com/parsomash/relayx/data/local/RuleDao.kt`
- [x] T007 Define `RuleRepository` domain interface in `relayx-android/src/main/java/com/parsomash/relayx/domain/repository/RuleRepository.kt`
- [x] T008 Implement `RuleRepositoryImpl` with default starter rule seeding in `relayx-android/src/main/java/com/parsomash/relayx/data/local/RuleRepositoryImpl.kt`
- [x] T009 Update `RelayDatabase.kt` to version 2, add `RuleEntity::class`, and implement `MIGRATION_1_2` in `relayx-android/src/main/java/com/parsomash/relayx/data/local/RelayDatabase.kt`
- [x] T010 Update `AppModule.kt` to bind `RuleDao` and `RuleRepository` in `relayx-android/src/main/java/com/parsomash/relayx/di/AppModule.kt`

---

## Phase 3: User Story 1 - Core Rule Engine & Secure Default Drop (Priority: P1) 🎯 MVP

**Goal**: Evaluate incoming SMS messages locally in deterministic priority order and silently drop any message that does not match an explicit allow rule, preventing personal SMS leakage.

**Independent Test**: Inject a test message from an unmatched sender and verify that it is saved in Room with `status = FILTERED` without triggering network dispatch.

### Tests for User Story 1
- [x] T011 [P] [US1] Unit test `RuleEngineTest` verifying exact, prefix, regex matching and default `DROP` fallback in `relayx-android/src/test/java/com/parsomash/relayx/domain/engine/RuleEngineTest.kt`

### Implementation for User Story 1
- [x] T012 [US1] Implement `RuleEngine` interface and `RuleEngineImpl` matching logic in `relayx-android/src/main/java/com/parsomash/relayx/domain/engine/RuleEngine.kt`
- [x] T013 [US1] Integrate `RuleEngine` into `IngestSmsUseCase.kt` to evaluate rules, mark dropped messages as `FILTERED`, and prevent WorkManager dispatch in `relayx-android/src/main/java/com/parsomash/relayx/domain/usecase/IngestSmsUseCase.kt`
- [x] T014 [US1] Unit test `IngestSmsUseCaseTest` verifying `FILTERED` status and outbox behavior in `relayx-android/src/test/java/com/parsomash/relayx/domain/usecase/IngestSmsUseCaseTest.kt`

**Checkpoint**: User Story 1 complete — on-device pre-filtering active with secure `DROP` default.

---

## Phase 4: User Story 2 - Regex OTP Extraction & Transformation (Priority: P2)

**Goal**: Extract verification codes via regex groups when action is `FORWARD_TRANSFORMED`, setting `transformedBody` and omitting raw message bodies from network dispatch.

**Independent Test**: Configure a regex rule `code is (\d{6})` with `FORWARD_TRANSFORMED`. Verify `transformedBody` contains the 6-digit code and the network dispatcher sends only the transformed text.

### Tests for User Story 2
- [x] T015 [P] [US2] Unit test regex extraction, capture group fallback, and transform policies in `relayx-android/src/test/java/com/parsomash/relayx/domain/engine/RegexTransformationTest.kt`

### Implementation for User Story 2
- [x] T016 [US2] Add regex transformation group extraction logic in `relayx-android/src/main/java/com/parsomash/relayx/domain/engine/RuleEngine.kt`
- [x] T017 [US2] Update `MessageDispatchWorker` to prefer `transformedBody` over `rawBody` when dispatching `POST /api/v1/messages` in `relayx-android/src/main/java/com/parsomash/relayx/data/worker/MessageDispatchWorker.kt`

**Checkpoint**: User Story 2 complete — data minimization via regex transformation active.

---

## Phase 5: User Story 3 - Rule Persistence & Priority Ordering (Priority: P3)

**Goal**: Persist user rules durably across device restarts and execute in deterministic order (`ORDER BY priority ASC, id ASC`).

**Independent Test**: Create two conflicting rules with different priorities and verify the higher-priority rule matches first.

### Tests for User Story 3
- [x] T018 [P] [US3] Unit test Room DAO queries, ordering, and default rule seeding in `relayx-android/src/test/java/com/parsomash/relayx/data/local/RuleDaoTest.kt`

### Implementation for User Story 3
- [x] T019 [US3] Implement `GetRulesUseCase`, `SaveRuleUseCase`, and `DeleteRuleUseCase` in `relayx-android/src/main/java/com/parsomash/relayx/domain/usecase/RuleUseCases.kt`
- [x] T020 [US3] Wire priority reordering logic in `RuleRepositoryImpl.kt`

**Checkpoint**: User Story 3 complete — Room storage and priority execution validated.

---

## Phase 6: User Story 4 - Rule Management Compose UI (Priority: P4)

**Goal**: Provide an intuitive Compose UI in the Android app to view active rules, create new rules, toggle their state, edit criteria, and delete obsolete rules.

**Independent Test**: Open the Rules screen, tap "+ Add Rule", create a rule, toggle it off, and verify the Dashboard active rules count updates reactively.

### Implementation for User Story 4
- [x] T021 [P] [US4] Add `AppRoute.Rules` to `RelayNavGraph.kt` and integrate into bottom navigation items in `relayx-android/src/main/java/com/parsomash/relayx/ui/navigation/RelayNavGraph.kt`
- [x] T022 [P] [US4] Create `RuleItemCard` with priority pill, sender badge, action tag, and enable/disable switch in `relayx-android/src/main/java/com/parsomash/relayx/ui/rules/components/RuleItemCard.kt`
- [x] T023 [P] [US4] Create `RuleEditDialog` modal with live regex validation in `relayx-android/src/main/java/com/parsomash/relayx/ui/rules/components/RuleEditDialog.kt`
- [x] T024 [US4] Create `RulesViewModel` managing `RulesUiState` and `RulesUiEvent` in `relayx-android/src/main/java/com/parsomash/relayx/viewmodel/RulesViewModel.kt`
- [x] T025 [US4] Create `RulesScreen` composing rule list, empty states, and add rule action in `relayx-android/src/main/java/com/parsomash/relayx/ui/rules/RulesScreen.kt`
- [x] T026 [US4] Connect `activeRulesCount` in `DashboardViewModel` to `RuleRepository.getActiveRulesCount()` in `relayx-android/src/main/java/com/parsomash/relayx/viewmodel/DashboardViewModel.kt`

**Checkpoint**: User Story 4 complete — complete visual rule management interface live.

---

## Phase 7: User Story 5 - Interactive Rule Testing Sandbox (Priority: P5)

**Goal**: Provide a sandbox within the app where users can type sample senders and bodies to preview which rule matches and what payload would be extracted.

**Independent Test**: Enter sample sender `"BANK"` and body `"OTP: 123456"`, tap "Test Rules", and verify the UI shows match details and extracted code.

### Implementation for User Story 5
- [x] T027 [P] [US5] Implement `TestRulesUseCase` in `relayx-android/src/main/java/com/parsomash/relayx/domain/usecase/TestRulesUseCase.kt`
- [x] T028 [US5] Create `RuleSandboxCard` with sample inputs and live match preview in `relayx-android/src/main/java/com/parsomash/relayx/ui/rules/components/RuleSandboxCard.kt`
- [x] T029 [US5] Integrate `RuleSandboxCard` into `RulesScreen.kt` and wire test events to `RulesViewModel` in `relayx-android/src/main/java/com/parsomash/relayx/ui/rules/RulesScreen.kt`

**Checkpoint**: User Story 5 complete — interactive rule testing sandbox functional.

---

## Phase 8: Polish, Verification & Quality Gates

**Purpose**: Cross-cutting verification, string resources, and regression testing.

- [x] T030 [P] Add string resources for Rules screen, actions, match types, and sandbox in `relayx-android/src/main/res/values/strings.xml`
- [x] T031 [P] Ensure zero sensitive logging in rule execution and regex extraction via `RelayLogger` in `relayx-android/src/main/java/com/parsomash/relayx/domain/engine/RuleEngine.kt`
- [x] T032 Run full test suite `./gradlew test` and verify zero regressions across all Android modules
- [x] T033 Validate quickstart scenarios via `./gradlew assembleDebug`

---

## Dependencies & Execution Order

```text
Phase 1: Setup (T001-T004)
        │
        ▼
Phase 2: Foundational Storage (T005-T010)
        │
        ▼
Phase 3: US1 - Core Rule Engine & Default Drop (T011-T014) 🎯 MVP
        │
        ▼
Phase 4: US2 - Regex Transformation (T015-T017)
        │
        ▼
Phase 5: US3 - Persistence & Priority Use Cases (T018-T020)
        │
        ▼
Phase 6: US4 - Rule Management UI (T021-T026)
        │
        ▼
Phase 7: US5 - Interactive Sandbox (T027-T029)
        │
        ▼
Phase 8: Polish & Verification (T030-T033)
```

---

## Parallel Execution Opportunities

- **Phase 1 (Setup)**: `T001`, `T002`, `T003` can execute in parallel.
- **Phase 2 (Foundational)**: `T005`, `T006`, `T007` can execute in parallel.
- **Phase 3 (US1)**: `T011` unit tests written and verified before `T012` engine implementation.
- **Phase 6 (UI)**: `T021` (NavGraph), `T022` (ItemCard), `T023` (EditDialog) can execute in parallel.
- **Phase 8 (Polish)**: `T030` (strings) and `T031` (logging verification) can execute in parallel.

---

## Implementation Strategy (MVP First)

1. **MVP Scope (Phase 1 to Phase 3)**:
   - Data models + Room foundation + RuleEngine + IngestSmsUseCase integration.
   - Incoming SMS is immediately evaluated locally; unmatched SMS is dropped (`FILTERED`); matching SMS is forwarded.
2. **Transformations & Persistence (Phase 4 & 5)**:
   - Regex extraction + Room use cases.
3. **User Interface & Sandbox (Phase 6 & 7)**:
   - Compose screen + dialogs + sandbox card + navigation.
4. **Final Polish & Verification (Phase 8)**:
   - String resources + `./gradlew test` quality gate.
