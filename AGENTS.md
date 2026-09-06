# RelayX - Agent Guidelines & Project Architecture

This document is the authoritative guide for AI coding agents (specifically **Google
Antigravity / `agy`**) working in the **RelayX** repository.

---

## 1. Project Overview

- **Repository**: `relayx` (`/Volumes/ADATASD810/Projects/Android/relayx`)
- **Package Name**: `com.parsomash.relayx`
- **Platform**: Android
- **Language**: Kotlin (2.x)
- **UI Framework**: Jetpack Compose (Material 3)
- **SDK Target**: `compileSdk = 37`, `minSdk = 24`, `targetSdk = 37`
- **Build System**: Gradle Kotlin DSL with version catalogs (`gradle/libs.versions.toml`)

### Directory Layout

```
relayx/
├── relayx-android/
│   ├── build.gradle.kts           # Module-level build configuration
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/parsomash/relayx/
│       │   │   ├── MainActivity.kt
│       │   │   └── ui/theme/      # Theme, typography, color palette
│       │   └── res/               # Android drawables, mipmaps, strings, xml configs
│       └── test/                  # Unit and instrumentation tests
├── gradle/
│   ├── libs.versions.toml         # Dependency & plugin version catalog
│   └── wrapper/                   # Gradle wrapper binaries & properties
├── docs/
│   └── memory/                    # Durable project memory (INDEX, ARCHITECTURE, etc.)
├── specs/                         # Feature specifications & SDD artifacts
├── .agents/                       # Google Antigravity (AGY) configuration
│   ├── rules/                     # Active agent rules (RTK, codebase-memory)
│   └── skills/                    # Spec Kit & project skills
├── .specify/                      # Spec Kit configuration & extensions (ignored in git)
├── .codebase-memory/              # Persistent knowledge graph cache (ignored in git)
└── AGENTS.md                      # This file (authoritative project instructions)
```

---

## 2. Codebase Knowledge Graph (`codebase-memory-mcp`)

This project uses `codebase-memory-mcp` to maintain a persistent knowledge graph of symbols, ASTs,
and dependencies.

- **Project Identifier**: `Volumes-ADATASD810-Projects-Android-relayx`
- **Root Path**: `/Volumes/ADATASD810/Projects/Android/relayx`
- **Graph Cache**: `.codebase-memory/graph.db.zst`

### Priority Order for Code Discovery

1. `search_graph` — Find functions, classes, routes, variables by regex pattern
   ```json
   {"name_pattern": ".*MainActivity.*"}
   ```
2. `trace_path` — Trace callers and dependencies (inbound/outbound)
   ```json
   {"function_name": "onCreate", "direction": "both"}
   ```
3. `get_code_snippet` — Read precise function or class source code
   ```json
   {"qualified_name": "com.parsomash.relayx.MainActivity"}
   ```
4. `query_graph` — Run Cypher queries for complex structural patterns
5. `get_architecture` — High-level project summary and subsystem decomposition

### Fallback to Text Search

Use text search (grep / ripgrep) only when:

- Searching for raw string literals, error messages, config values, or XML resources
- Inspecting non-code config files (`build.gradle.kts`, `gradle.properties`, `AndroidManifest.xml`)
- Verifying code inside partially parsed files reported by `index_status`

---

## 3. RTK (Rust Token Killer) - Token-Optimized CLI Proxy

**Golden Rule**: Always prefix shell commands with `rtk` to filter and compress output before it
consumes LLM context window tokens.

### Common Development Commands

```bash
# Gradle Commands (Android wrapper via RTK)
rtk ./gradlew assembleDebug       # Build debug APK with compressed output
rtk ./gradlew test                # Run unit tests (failures highlighted)
rtk ./gradlew lint                # Run Android lint checks
rtk ./gradlew check               # Run all verification tasks

# Git Commands
rtk git status                    # Compact git status
rtk git log -n 10                 # Condensed commit history
rtk git diff                      # Ultra-compact diff (changed lines only)
rtk git add . && rtk git commit -m "feat: description"

# File & Search Operations
rtk ls relayx-android/src/main/java          # Compact tree listing
rtk grep "pattern" relayx-android/src/       # Grouped grep output
rtk find "*.kt" relayx-android/src/          # Find files by pattern
rtk read <filepath>               # Intelligent filtered file read

# RTK Meta & Analytics
rtk gain                          # View token savings
rtk gain --history                # Command history with savings
rtk proxy <cmd>                   # Run raw command without filtering (debugging only)
rtk --version                     # Check RTK version
```

---

## 4. Spec Kit (Specify) & SDD Workflow

RelayX follows **Spec-Driven Development (SDD)** with a **memory-first workflow**.

### Memory Layers

| Layer      | Path                        | Purpose                                                         |
|------------|-----------------------------|-----------------------------------------------------------------|
| Governance | `.specify/memory/`          | Constitution, security policy, workflow rules                   |
| Durable    | `docs/memory/`              | Cross-feature decisions, architectural boundaries, bug patterns |
| Active     | `specs/<feature>/memory.md` | Feature-local constraints, open questions, watchpoints          |
| Ephemeral  | prompt / terminal / diff    | Temporary execution context (never committed to durable memory) |

### Memory-First Execution Rule

1. **Before planning/specifying**:
    - Run `/speckit.memory-md.prepare-context` or use skill `speckit-memory-md-prepare-context`.
    - Read `docs/memory/INDEX.md` + `memory-synthesis.md` before modifying durable files.
    - Gated on hard conflicts: do not proceed if there is an unresolved conflict with architecture
      boundaries.
2. **During implementation**:
    - Treat watchpoints in `memory-synthesis.md` as hard requirements.
    - Verify changes do not violate module boundaries defined in `docs/memory/ARCHITECTURE.md`.
3. **After completion**:
    - Capture durable lessons via skill `speckit-memory-md-capture` or
      `speckit-memory-md-capture-from-diff`.

### Core Spec Kit Lifecycle

1. **Specify**: Define requirements and specifications
    - Skill: `speckit-specify` | Command: `speckit.specify`
2. **Clarify**: Identify gaps and ask targeted clarification questions
    - Skill: `speckit-clarify` | Command: `speckit.clarify`
3. **Plan**: Architecture planning, technical design, and module breakdown
    - Skill: `speckit-plan` | Command: `speckit.plan`
4. **Tasks**: Dependency-ordered, actionable implementation tasks
    - Skill: `speckit-tasks` | Command: `speckit.tasks`
5. **Implement**: Execute implementation tasks sequentially with quality gates
    - Skill: `speckit-implement` | Command: `speckit.implement`
6. **Converge**: Audit unbuilt requirements against spec and add remaining tasks
    - Skill: `speckit-converge`

### Installed Extensions & Skills Reference

All 10 Spec Kit extensions are active in `.specify/extensions/` and available directly as AGY
skills:

| Extension           | Skills & Commands                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
|---------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **git**             | `speckit-git-feature`, `speckit-git-commit`, `speckit-git-initialize`, `speckit-git-remote`, `speckit-git-validate`                                                                                                                                                                                                                                                                                                                                                       |
| **memory-md**       | `speckit-memory-md-init`, `speckit-memory-md-specify`, `speckit-memory-md-plan-with-memory`, `speckit-memory-md-capture`, `speckit-memory-md-capture-from-diff`, `speckit-memory-md-audit`, `speckit-memory-md-repair-index`, `speckit-memory-md-log-finding`, `speckit-memory-md-token-report`, `speckit-memory-md-prepare-context`, `speckit-memory-md-index-docs`, `speckit-memory-md-init-project`, `speckit-memory-md-share-lesson`, `speckit-memory-md-sync-shared` |
| **security-review** | `speckit-security-review-init`, `speckit-security-review-audit`, `speckit-security-review-plan`, `speckit-security-review-tasks`, `speckit-security-review-staged`, `speckit-security-review-branch`, `speckit-security-review-followup`, `speckit-security-review-apply`, `speckit-security-review-export`                                                                                                                                                               |
| **qa**              | `speckit-qa-run`                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| **diagram**         | `speckit-diagram-workflow`, `speckit-diagram-status`, `speckit-diagram-dependencies`                                                                                                                                                                                                                                                                                                                                                                                      |
| **bugfix**          | `speckit-bugfix-report`, `speckit-bugfix-patch`, `speckit-bugfix-verify`                                                                                                                                                                                                                                                                                                                                                                                                  |
| **changelog**       | `speckit-changelog-generate`, `speckit-changelog-release`, `speckit-changelog-diff`, `speckit-changelog-notify`                                                                                                                                                                                                                                                                                                                                                           |
| **pr-bridge**       | `speckit-pr-bridge-generate`, `speckit-pr-bridge-checklist`, `speckit-pr-bridge-summary`                                                                                                                                                                                                                                                                                                                                                                                  |
| **specify-cicd**    | `speckit-cicd-setup`, `speckit-cicd-run`, `speckit-cicd-dry-run`, `speckit-cicd-report`                                                                                                                                                                                                                                                                                                                                                                                   |
| **preview**         | `speckit-preview-html`                                                                                                                                                                                                                                                                                                                                                                                                                                                    |

### Automated Event Hooks

Configured in `.specify/extensions.yml`:

- `before_specify` -> `speckit.git.feature`, `speckit.memory-md.specify`
- `before_plan` -> `speckit.git.commit`, `speckit.memory-md.plan-with-memory`
- `after_plan` -> `speckit.git.commit`, `speckit.security-review.plan`
- `after_tasks` -> `speckit.git.commit`, `speckit.security-review.tasks`,
  `speckit.diagram.dependencies`
- `before_implement` -> `speckit.git.commit`, `speckit.cicd.setup`
- `after_implement` -> `speckit.git.commit`, `speckit.cicd.run`, `speckit.memory-md.capture`,
  `speckit.security-review.branch`, `speckit.qa.run`, `speckit.bugfix.verify`,
  `speckit.changelog.generate`, `speckit.pr-bridge.generate`

---

## 5. Agent Environment & Antigravity (AGY) Integration

- **Primary Agent**: Google Antigravity (`agy`).
- **Skill Discovery**: Automatically discovers all 57 skills from
  `.agents/skills/<skill-name>/SKILL.md`.
- **Rules**: Automatically loads active rules from `.agents/rules/` (`antigravity-rtk-rules.md`,
  `codebase-memory-rules.md`) and project instructions from `AGENTS.md`.
- **Spec Kit Integration**: Configured for `agy` (`.specify/integration.json`), executing SDD
  workflows natively.
