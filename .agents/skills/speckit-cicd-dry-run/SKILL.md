---
name: speckit-cicd-dry-run
description: Show which CI steps would execute locally without actually running them
compatibility: Requires spec-kit project structure with .specify/ directory
metadata:
  author: ghasemdev
  source: cicd:commands/specify-cicd-dry-run.md
---

# Local CI/CD Dry Run

## Behavior

Parses `cicd-config.yaml` and simulates the pipeline execution locally.
Shows which steps would run, what commands they'd execute, and whether they'd be
diff-bounded based on current staged/unstaged changes.

## Execution

```bash
.specify/extensions/cicd/scripts/bash/cicd-dry-run.sh
```

## Output

```
Local CI/CD Pipeline Simulation
─────────────────────────────────────────────────
Step       Command                              Diff-Bounded  Status
──────────-┼───────────────────────────────────-──┼──────────────┼──────────
assemble   │ ./gradlew assemble --no-daemon       │ ✅ matched   │ would run
lint       │ ./gradlew detekt                     │ ✅ matched   │ would run
test       │ ./gradlew test jvmTest --no-daemon   │ ⏭️ excluded  │ would skip
coverage   │ ./gradlew koverVerify --no-daemon    │ ⏭️ excluded  │ would skip
benchmark  │ ./gradlew fastBenchmark --no-daemon  │ ⏭️ excluded  │ would skip
─────────────────────────────────────────────────
Total: 5 steps, 2 would run, 3 would skip (diff-bounded)
```

## Dry-run modes

- `--all` — ignore diff-bounded, show what would run if all steps were executed
- `--bypass <pattern>` — exclude steps whose `diff_bounded.patterns` match
- `--step <name>` — show only one step's simulation
