---
name: speckit-pr-bridge-generate
description: Generate a complete PR description with title, summary, and requirement
  checklist from spec artifacts
compatibility: Requires spec-kit project structure with .specify/ directory
metadata:
  author: github-spec-kit
  source: pr-bridge:commands/speckit.pr-bridge.generate.md
---

# Generate PR Description

Auto-generate a complete pull request description by reading the feature's spec.md, plan.md, and tasks.md artifacts. Produces a ready-to-paste PR body with title, summary, implementation details, requirement traceability, and test coverage — closing the gap between SDD and code review.

## User Input

```text
$ARGUMENTS
```

You **MUST** consider the user input before proceeding (if not empty). The user may specify a feature branch name (e.g., "003-user-auth"), a target base branch (e.g., "main"), or formatting preferences (e.g., "compact", "detailed").

## Prerequisites

1. Verify a spec-kit project exists by checking for `.specify/` directory
2. Verify git is available and the project is a git repository
3. Identify the current feature branch (or use the branch specified by the user)
4. Verify at least `spec.md` exists for the feature in `specs/{feature}/`

## Outline

1. **Identify feature and branch**: Determine which feature's PR to generate:
   - If user specifies a branch name, use that
   - If no input, detect the current branch and find its spec artifacts
   - Locate the feature directory in `specs/{feature}/`

2. **Read spec artifacts**: Load all available artifacts for the feature:

   | Artifact | Required | What It Provides |
   |----------|----------|-----------------|
   | `spec.md` | Yes | Feature description, user scenarios, requirements, success criteria |
   | `plan.md` | No | Technical approach, architecture decisions, complexity assessment |
   | `tasks.md` | No | Task completion status, implementation phases |
   | `bugs/*.md` | No | Known bugs and patches (if bugfix extension was used) |

3. **Analyze git changes**: Compare the feature branch against the base branch:
   - Run `git diff --stat {base}...HEAD` to get changed file summary
   - Run `git log --oneline {base}...HEAD` to get commit history
   - Categorize changed files by type (source, test, config, docs)
   - Count additions and deletions

4. **Map requirements to changes**: For each requirement in spec.md:
   - Identify which changed files implement that requirement
   - Determine if tests exist for that requirement
   - Flag any requirements with no corresponding code changes

5. **Generate PR description**: Produce a complete, ready-to-paste PR body:

   ```markdown
   ## Summary

   {2-3 sentence description extracted from spec.md's purpose and user scenarios}

   ## What Changed

   | Category | Files | Changes |
   |----------|-------|---------|
   | Source | {N} files | +{additions} / -{deletions} |
   | Tests | {N} files | +{additions} / -{deletions} |
   | Config | {N} files | +{additions} / -{deletions} |
   | Docs | {N} files | +{additions} / -{deletions} |

   ## Spec Requirements

   {Checklist of requirements from spec.md, each linked to implementing files}

   - [x] {Requirement 1} → `src/auth/login.ts`, `src/auth/login.test.ts`
   - [x] {Requirement 2} → `src/auth/signup.ts`
   - [ ] {Requirement 3} → ⚠️ No matching changes found

   ## Implementation Approach

   {Brief technical summary from plan.md — key decisions, patterns used}

   ## Task Completion

   {Progress from tasks.md}

   - **Phase 1**: {N}/{M} tasks ✅
   - **Phase 2**: {N}/{M} tasks ✅
   - **Total**: {N}/{M} ({percentage}%)

   ## Test Plan

   {List of test files added or modified, with what they cover}

   - `src/auth/__tests__/login.test.ts` — Login flow, token generation, error cases
   - `src/auth/__tests__/signup.test.ts` — Registration, validation, duplicate detection

   ## Spec Artifacts

   - [`spec.md`](specs/{feature}/spec.md)
   - [`plan.md`](specs/{feature}/plan.md)
   - [`tasks.md`](specs/{feature}/tasks.md)
   ```

6. **Generate PR title**: Create a concise title following common conventions:
   - Format: `{type}: {short description} (#{feature-number})`
   - Types: `feat`, `fix`, `refactor`, `docs`, `chore`
   - Example: `feat: add JWT authentication with login and signup (#003)`

7. **Output**: Present the generated PR title and description:

   ```markdown
   # PR Ready

   **Title**: feat: add JWT authentication with login and signup (#003)

   **Description**: (copy everything below)

   ---
   {full PR body from step 5}
   ---

   ## How to Use
   - Copy the title and description above
   - Create a PR on GitHub targeting `{base-branch}`
   - Or run: `gh pr create --title "{title}" --body "{description}"`
   ```

## Rules

- **Read-only** — this command never creates or modifies PRs directly, only generates text
- **Artifact-driven** — every section of the PR must trace to a spec artifact or git data
- **Graceful degradation** — if plan.md or tasks.md are missing, generate what you can from spec.md and git
- **No fabrication** — never invent requirements or changes not found in artifacts or git
- **Flag gaps** — if a spec requirement has no matching code change, mark it with ⚠️
- **Respect conventions** — if the project has a PR template (`.github/PULL_REQUEST_TEMPLATE.md`), adapt output to match its structure