# Project Context

Last reviewed: 2026-09-07

## Product / Service
RelayX is an Android application developed with Jetpack Compose and Material 3, focusing on modern reactive architecture, modularity, and high-performance Android UX.

## Key Constraints
- **Platform**: Android SDK 37 (compileSdk = 37, targetSdk = 37, minSdk = 24).
- **Tooling**: Gradle Kotlin DSL (`build.gradle.kts`) with version catalog (`gradle/libs.versions.toml`).
- **Development & Token Efficiency**: All CLI and build operations must use `rtk` (e.g. `rtk ./gradlew ...`, `rtk git ...`).
- **AI Pairing**: Google Antigravity (`agy`) is the exclusive AI assistant for development, leveraging native `.agents/skills/` and `codebase-memory-mcp`.
- **Workflow**: Spec-Driven Development (SDD) with strict memory-first preparation (`docs/memory/` and `.specify/`).

## Important Domains
- `com.parsomash.relayx`
- `ui.theme`: Compose Material 3 theme styling, typography, and color palette.
- Feature modules and core components under `relayx-android/src/main/java/com/parsomash/relayx`.

## Current Priorities
- Establish robust SDD foundation and Spec Kit extensions.
- Maintain knowledge graph index integrity via `codebase-memory-mcp`.
- Implement clean, test-driven Compose UI and business logic.
