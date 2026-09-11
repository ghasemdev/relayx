# Memory Index

This is a compact routing map for durable project memory (`docs/memory/`). Keep it concise and updated.

> [!NOTE]
> High-level project governance and operating principles are stored in the **Governance Layer** at [`.specify/memory/constitution.md`](file:///Volumes/ADATASD810/Projects/Android/relayx/.specify/memory/constitution.md) and must be consulted before technical planning.

## Durable Memory Documents

| Document | Scope | Purpose |
|:---|:---|:---|
| [PROJECT_CONTEXT.md](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/memory/PROJECT_CONTEXT.md) | Product & System | System actors, core constraints, domains, and priorities. |
| [ARCHITECTURE.md](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/memory/ARCHITECTURE.md) | Technical Design | Component layout, Go server package design, SQLite schema, REST API, and MCP tools. |
| [DECISIONS.md](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/memory/DECISIONS.md) | ADRs | Architectural decision records (ADR-001 through ADR-011). |
| [BUGS.md](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/memory/BUGS.md) | Quality & Security | Known regression traps, doze mode risks, sensitive data leaks, and race conditions. |
| [WORKLOG.md](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/memory/WORKLOG.md) | Milestones | Record of major technical migrations and roadmap checkpoints. |
| [roadmap.md](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/roadmap.md) | Execution Plan | 7-phase implementation roadmap and Definition of Done. |

## Security Reviews

| Document | Type | Date | Overall Risk | Findings | OWASP |
|:---|:---|:---|:---|:---|:---|
| [2026-09-07-feature-001-server-foundation.md](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/security-reviews/2026-09-07-feature-001-server-foundation.md) | branch | 2026-09-07 | MODERATE | C:0 H:0 M:1 L:2 | A04,A05,A07 |
| [2026-09-07-feature-001-server-foundation-followup.md](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/security-reviews/2026-09-07-feature-001-server-foundation-followup.md) | followup | 2026-09-07 | MODERATE | C:0 H:0 M:1 L:2 | A04,A05,A07 |
| [2026-09-12-feature-002-android-gateway-foundation.md](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/security-reviews/2026-09-12-feature-002-android-gateway-foundation.md) | branch | 2026-09-12 | MODERATE | C:0 H:0 M:1 L:2 | A02,A03,A05 |
| [2026-09-12-feature-002-android-gateway-foundation-followup.md](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/security-reviews/2026-09-12-feature-002-android-gateway-foundation-followup.md) | followup | 2026-09-12 | MODERATE | C:0 H:0 M:1 L:2 | A02,A03,A05 |
| [2026-09-12-feature-002-android-gateway-foundation-tasks.md](file:///Volumes/ADATASD810/Projects/Android/relayx/docs/security-reviews/2026-09-12-feature-002-android-gateway-foundation-tasks.md) | tasks | 2026-09-12 | LOW | C:0 H:0 M:0 L:3 | A02,A03,A05 |

## Workflow & Operating Rules
- [Workflow Rules](file:///Volumes/ADATASD810/Projects/Android/relayx/.specify/memory/workflow.md): Authoritative SDD memory-first execution procedure.
- [Security Constitution](file:///Volumes/ADATASD810/Projects/Android/relayx/.specify/memory/security_constitution.md): Comprehensive security baseline and review gates.
