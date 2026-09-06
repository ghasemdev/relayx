# RelayX - Agent Guidelines

Authoritative instructions for AI coding agents (**Google Antigravity / `agy`**) in **RelayX**.

---

## 1. Project Overview

- **Repository**: `relayx` (`/Volumes/ADATASD810/Projects/Android/relayx`)
- **App Module**: `relayx-android` (`com.parsomash.relayx`) | Kotlin 2.x, Jetpack Compose, Material 3, SDK 37 (minSdk 24).
- **Server Module**: `relayx-server` | Standalone Go binary, embedded SQLite, embedded migrations, MCP server.
- **Documentation**: See [README.md](file:///Volumes/ADATASD810/Projects/Android/relayx/README.md), [docs/roadmap.md](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/roadmap.md), and [docs/memory/INDEX.md](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/memory/INDEX.md).

---

## 2. Codebase Knowledge Graph (`codebase-memory-mcp`)

Project: `Volumes-ADATASD810-Projects-Android-relayx` | Cache: `.codebase-memory/graph.db.zst`

### Priority Order for Code Discovery
1. `search_graph` — Find functions, classes, routes, variables by regex pattern
2. `trace_path` — Trace callers and dependencies (inbound/outbound/both)
3. `get_code_snippet` — Read precise function or class source code
4. `query_graph` — Run Cypher queries for complex structural patterns
5. `get_architecture` — High-level project summary and subsystem decomposition

*Fallback*: Use ripgrep only for raw strings, XML resources, Gradle configs, or unindexed files.

---

## 3. RTK (Rust Token Killer) - CLI Proxy

**Rule**: Always prefix shell commands with `rtk` to compress LLM context usage.

```bash
# Android Gradle Commands
rtk ./gradlew assembleDebug       # Build debug APK
rtk ./gradlew test                # Run unit tests
rtk ./gradlew check               # Run verification checks

# Server Go Commands (when working on server)
rtk go build ./...                # Compile Go server
rtk go test ./...                 # Run Go tests

# Git & File Operations
rtk git status && rtk git diff    # Compact git operations
rtk git log -n 10                 # Condensed history
rtk ls relayx-android/src/        # Compact tree listing
rtk grep "pattern" relayx-android # Grouped search
```

---

## 4. Spec Kit & SDD Memory-First Workflow

RelayX enforces Spec-Driven Development (SDD) governed by [`.specify/memory/constitution.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/.specify/memory/constitution.md).

### Memory Layers & Paths
- **Governance**: [`.specify/memory/workflow.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/.specify/memory/workflow.md), [`.specify/memory/constitution.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/.specify/memory/constitution.md), [`.specify/memory/security_constitution.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/.specify/memory/security_constitution.md)
- **Durable**: [`docs/memory/INDEX.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/memory/INDEX.md), [`ARCHITECTURE.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/memory/ARCHITECTURE.md), [`DECISIONS.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/memory/DECISIONS.md), [`BUGS.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/memory/BUGS.md), [`PROJECT_CONTEXT.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/memory/PROJECT_CONTEXT.md)
- **Active Feature**: `specs/<feature>/memory.md` and `memory-synthesis.md`

### SDD Phase Commands & Skills
- **Specify**: `speckit-specify` | Prepare context with `speckit-memory-md-prepare-context` first
- **Clarify**: `speckit-clarify` | Resolve ambiguities
- **Plan**: `speckit-plan` | Architecture planning gated against memory constraints
- **Tasks**: `speckit-tasks` | Dependency-ordered actionable tasks
- **Implement**: `speckit-implement` | Sequential task execution with quality gates
- **Review / QA**: `speckit-security-review-audit`, `speckit-qa-run`, `speckit-memory-md-capture`

---

## 5. Agent Environment

- **Skills**: Discovered automatically from `.agents/skills/<skill-name>/SKILL.md` (57 skills).
- **Rules**: Discovered automatically from `.agents/rules/` (`antigravity-rtk-rules.md`, `codebase-memory-rules.md`).
- **Integration**: Native `agy` integration configured in `.specify/integration.json`.
