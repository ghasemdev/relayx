# Spec Kit Memory Workflow

Authoritative memory-first workflow for KodEx. All AI agents MUST follow these rules.

## Memory Layers

| Layer | Path | Purpose |
|---|---|---|
| Governance | `.specify/memory/` | Constitution, principles, security, tech-stack — stable operating rules |
| Durable | `docs/memory/` | Cross-feature decisions, architecture boundaries, bug patterns |
| Active | `specs/<feature>/memory.md` | Feature-local constraints, open questions, watchpoints |
| Ephemeral | prompt / terminal / diff | Temporary — NEVER commit to durable memory |

## Required Workflow

### Before Starting (Specify / Plan / Tasks)

1. **Prepare context** — use the best available path:
   - MCP: call `speckit_memory_refresh_cache` → `speckit_memory_search` → `speckit_memory_synthesize`
   - Spec Kit: run `/speckit.memory-md.prepare-context`
   - Fallback: read `docs/memory/INDEX.md` + `memory-synthesis.md` manually
2. Read `docs/memory/INDEX.md` and `memory-synthesis.md` before touching raw durable files.
3. **Conflict check**: do not proceed if there is an unresolved hard conflict with project memory or architecture boundaries.

### During Implementation

- Treat watchpoints in `memory-synthesis.md` as requirements, not suggestions.
- Verify new changes don't violate module boundaries defined in `docs/memory/ARCHITECTURE.md`.
- Check `security_constitution.md` for any security pattern that applies to the current task.

### After Completion (Implement / Verify)

1. Evaluate if any architectural lesson, security pattern, or decision is worth preserving.
2. **Capture** — use the best available path:
   - Spec Kit: `/speckit.memory-md.capture` or `/speckit.memory-md.capture-from-diff`
   - MCP: `speckit_memory_register` (only after explicit user approval)
3. Update `docs/memory/INDEX.md` routing row if a new durable entry is added.
4. **Never write directly to `docs/memory/` files** — always go through the capture flow so INDEX.md and the SQLite cache stay in sync.

## Maintenance Rules

- Keep INDEX.md entries concise (one line per entry, max 20 entries).
- Refuse routine implementation details in durable memory (wrong layer).
- Keep `memory-synthesis.md` under the 900-word retrieval budget.
- Fall back to markdown-first if the SQLite optimizer is unavailable.
- Capture flow requires explicit user approval before writing.
