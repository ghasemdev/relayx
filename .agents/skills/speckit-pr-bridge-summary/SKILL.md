---
name: speckit-pr-bridge-summary
description: Generate a concise change summary comparing spec intent with actual implementation
compatibility: Requires spec-kit project structure with .specify/ directory
metadata:
  author: github-spec-kit
  source: pr-bridge:commands/speckit.pr-bridge.summary.md
---

# PR Change Summary

Generate a concise, high-level summary that compares what the spec intended with what was actually implemented. Highlights alignment, deviations, and any gaps — useful for stakeholders, tech leads, and reviewers who need to quickly understand the PR without reading every file.

## User Input

```text
$ARGUMENTS
```

You **MUST** consider the user input before proceeding (if not empty). The user may specify a feature name, request a specific audience (e.g., "for product manager", "for tech lead"), or format (e.g., "one-paragraph", "bullet-points").

## Prerequisites

1. Verify a spec-kit project exists by checking for `.specify/` directory
2. Verify git is available and the project is a git repository
3. Identify the current feature branch and its spec artifacts
4. Verify at least `spec.md` exists for the feature

## Outline

1. **Read all artifacts**: Load the complete picture:
   - `spec.md` — what was supposed to be built
   - `plan.md` — how it was supposed to be built
   - `tasks.md` — what steps were planned and completed
   - `git diff --stat` — what files actually changed
   - `git log --oneline` — what commits were made

2. **Compare spec intent vs implementation**:

   | Aspect | From Spec | From Code | Status |
   |--------|-----------|-----------|--------|
   | **Scope** | What features were specified | What features were built | ✅ Match / ⚠️ Deviation |
   | **Approach** | Technical approach in plan.md | Actual patterns in code | ✅ Match / ⚠️ Deviation |
   | **Completeness** | All requirements in spec.md | Requirements with matching code | ✅ Complete / ⚠️ Gaps |
   | **Testing** | Success criteria in spec.md | Test files in diff | ✅ Covered / ⚠️ Missing |

3. **Identify deviations**: For any mismatches between spec and implementation:
   - What deviated and why (if reason is apparent from commits or code comments)
   - Whether the deviation is an improvement, a compromise, or a gap
   - Whether the spec should be updated to reflect the deviation

4. **Generate summary**: Produce a clear, structured summary:

   ```markdown
   # Change Summary: {Feature Name}

   ## Intent (from spec)
   {2-3 sentences describing what the spec intended}

   ## What Was Built
   {2-3 sentences describing what was actually implemented}

   ## Alignment
   | Requirement | Status | Notes |
   |-------------|--------|-------|
   | {Req 1} | ✅ Implemented | — |
   | {Req 2} | ✅ Implemented | — |
   | {Req 3} | ⚠️ Partial | {What's missing} |
   | {Req 4} | ❌ Not implemented | {Reason if known} |

   ## Scope
   - **Planned**: {N} requirements, {M} tasks
   - **Completed**: {X} requirements, {Y} tasks ({percentage}%)
   - **Files changed**: {N} ({additions} additions, {deletions} deletions)
   - **Tests added**: {N} test files

   ## Deviations
   {If any — what changed from the original spec/plan and why}

   ## Risks
   {Any requirements not covered, missing tests, or architectural concerns}
   ```

5. **Output**: Present the summary ready for use in PR descriptions, Slack messages, or standup updates.

## Rules

- **Read-only** — this command never modifies any files
- **Honest assessment** — report gaps and deviations transparently, don't hide incomplete work
- **Evidence-based** — every claim must reference a specific artifact or git data
- **Audience-appropriate** — if the user specifies an audience, adjust detail level accordingly
- **No blame** — deviations are reported neutrally as facts, not as failures
- **Concise** — the summary should be readable in under 2 minutes