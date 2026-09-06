---
name: speckit-pr-bridge-checklist
description: Generate a reviewer checklist that maps each spec requirement to its
  implementing files
compatibility: Requires spec-kit project structure with .specify/ directory
metadata:
  author: github-spec-kit
  source: pr-bridge:commands/speckit.pr-bridge.checklist.md
---

# PR Review Checklist

Generate a structured review checklist for pull request reviewers. Maps each spec requirement to the files that implement it, the tests that verify it, and the acceptance criteria that confirm it — giving reviewers a clear, traceable path through the code changes.

## User Input

```text
$ARGUMENTS
```

You **MUST** consider the user input before proceeding (if not empty). The user may specify a feature name, branch, or request a specific format (e.g., "compact", "grouped-by-file").

## Prerequisites

1. Verify a spec-kit project exists by checking for `.specify/` directory
2. Verify git is available and the project is a git repository
3. Identify the current feature branch and its spec artifacts
4. Verify at least `spec.md` exists for the feature

## Outline

1. **Load spec artifacts**: Read spec.md to extract:
   - User scenarios and their acceptance criteria
   - Requirements (functional and non-functional)
   - Success criteria
   - Assumptions that should be verified

2. **Load plan context**: If plan.md exists, extract:
   - Technical decisions that reviewers should validate
   - Architecture patterns that should be followed
   - Complexity factors that need careful review

3. **Map requirements to code**: For each requirement:
   - Identify the source files that implement it
   - Identify the test files that verify it
   - Determine if the requirement is fully, partially, or not implemented
   - Note any edge cases mentioned in the spec that need review

4. **Generate review checklist**: Produce a structured checklist organized by spec section:

   ```markdown
   # Review Checklist: {Feature Name}

   ## Spec Requirement Verification

   ### User Scenario 1: {Title}
   - [ ] **Requirement**: {Requirement text from spec.md}
     - **Files**: `src/auth/login.ts` (L12-L45)
     - **Tests**: `src/auth/__tests__/login.test.ts`
     - **Verify**: {What the reviewer should check}

   - [ ] **Requirement**: {Requirement text}
     - **Files**: `src/auth/signup.ts` (L8-L32)
     - **Tests**: `src/auth/__tests__/signup.test.ts`
     - **Verify**: {What to check}

   ### User Scenario 2: {Title}
   - [ ] **Requirement**: {Requirement text}
     - **Files**: `src/middleware/auth.ts`
     - **Tests**: ⚠️ No test found
     - **Verify**: {What to check}

   ## Technical Decisions (from plan.md)
   - [ ] JWT tokens use RS256 signing (not HS256) as specified in plan
   - [ ] Token refresh uses sliding window expiration
   - [ ] Password hashing uses bcrypt with cost factor 12

   ## Quality Checks
   - [ ] All spec requirements have corresponding tests
   - [ ] Error handling covers edge cases from spec assumptions
   - [ ] No hardcoded values that should be configurable
   - [ ] Database migrations are reversible

   ## Gaps Found
   - ⚠️ Requirement "Rate limiting on login endpoint" — no matching implementation found
   - ⚠️ Test missing for "Token refresh after expiration" scenario
   ```

5. **Output**: Present the checklist ready for copy-paste into a PR comment.

## Rules

- **Read-only** — this command never modifies any files
- **Requirement-driven** — every checklist item must trace to a specific spec requirement, plan decision, or success criterion
- **File-specific** — always reference exact file paths and line ranges where the implementation lives
- **Flag missing coverage** — explicitly call out requirements without tests or implementations
- **Reviewer-friendly** — write verification instructions that help reviewers know what to look for
- **No opinions** — report what the spec says and what the code does, don't add subjective quality judgments