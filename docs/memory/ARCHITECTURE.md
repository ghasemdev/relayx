# Architecture

Last reviewed: 2026-09-07

## System Overview
RelayX is structured as a modern Android Gradle project with Jetpack Compose as its presentation engine.

```
relayx/
├── app/                           # Main application module
│   ├── src/main/java/com/parsomash/relayx/
│   │   ├── MainActivity.kt        # Entry point Compose Activity
│   │   └── ui/theme/              # Compose Theme, Type, Color
│   └── src/main/res/              # XML resources, drawables, manifests
├── docs/memory/                   # Durable project knowledge & architectural history
├── specs/                         # SDD specifications, plans, and tasks
└── .agents/                       # Antigravity agent configuration and skills
```

## Major Components
- **`MainActivity`**: Root Android entry point initializing Compose UI hierarchy.
- **`Theme`**: Material 3 theming system with dynamic color and theme typography.
- **Gradle Build Pipeline**: Managed via Foojay toolchains and version catalog `libs.versions.toml`.

## Boundaries
- Code logic resides under `app/src/main/java/com/parsomash/relayx`.
- UI presentation is restricted to Jetpack Compose components.
- Agent and workflow assets are encapsulated in `.agents/` and `.specify/`.

## Integrations
- **Codebase Memory MCP**: Knowledge graph indexing with root at project base and cache in `.codebase-memory/graph.db.zst`.
- **RTK (Rust Token Killer)**: Proxies all shell execution for Gradle wrapper and Git.
- **Spec Kit**: Drives feature planning and verification.
