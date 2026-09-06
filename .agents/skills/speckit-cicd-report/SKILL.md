---
name: speckit-cicd-report
description: Generate Markdown report of local CI/CD execution results with pass/fail per step and diff-bounded summary
compatibility: Requires spec-kit project structure with .specify/ directory
metadata:
  author: ghasemdev
  source: cicd:commands/specify-cicd-report.md
---

# Generate Local CI/CD Report

## Behavior

Produces a human-readable Markdown report summarizing local CI/CD pipeline execution.

## Execution

Run the report script:

```bash
.specify/extensions/cicd/scripts/bash/cicd-report.sh [--output path/to/report.md]
```

## Output format

Generates a Markdown document with:

```markdown
# Local CI/CD Report

**Project**: kodEx
**Branch**: feature/002-cicd-extension
**Timestamp**: 2026-05-23T17:30:45+03:00
**Pipeline Result**: ❌ FAILED (2/5 passed, 1 warn, 2 skipped)

## Summary

| Step    | Status  | Duration |
|---------|---------|----------|
| assemble| ✅ PASS | 45s      |
| lint    | ✅ PASS | 22s      |
| test    | ❌ FAIL | 120s     |
| coverage| ⏭️ SKIP | —        |

## Failures

### test (FAIL)
Command: `./gradlew test jvmTest --no-daemon`

## Recommendations

1. Fix failing steps and re-run: `speckit.cicd.run`
2. Commit fix and re-push to trigger remote CI
```

## Logging references

The report includes clickable links to per-step logs:
```
.specify/extensions/cicd/logs/<step_name>-<YYYYMMDDHHmmss>.log
```

## Graceful Degradation

- If no previous run logs exist → generates empty report with instructions
- If report was already generated → appends a "Second Run" section with diff comparison
