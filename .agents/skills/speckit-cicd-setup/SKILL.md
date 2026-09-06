---
name: speckit-cicd-setup
description: Scan CI workflow files and generate local cicd-config.yaml from detected jobs/steps
compatibility: Requires spec-kit project structure with .specify/ directory
metadata:
  author: ghasemdev
  source: cicd:commands/specify-cicd-setup.md
---

# Set Up Local CI/CD Configuration

## Behavior

Parses CI workflow files from GitHub Actions, GitLab CI, CircleCI, and Bitbucket Pipelines.
Extracts jobs/steps, command lines, timeouts, and conditions. Generates `cicd-config.yaml`.

## Execution

Run the setup script:

```bash
.specify/extensions/cicd/scripts/bash/cicd-setup.sh
```

Or specify a provider explicitly:

```bash
.specify/extensions/cicd/scripts/bash/cicd-setup.sh --provider github-actions
.specify/extensions/cicd/scripts/bash/cicd-setup.sh --provider gitlab-cli
.specify/extensions/cicd/scripts/bash/cicd-setup.sh --provider circleci-cli
```

## Auto-detection priority

1. `.github/workflows/*.yml` → GitHub Actions
2. `.gitlab-ci.yml` → GitLab CI
3. `.circleci/config.yml` → CircleCI
4. `bitbucket-pipelines.yml` → Bitbucket Pipelines

## Output

Generates `.specify/extensions/cicd/cicd-config.yaml` with:

```yaml
sources:
  github_actions:
    enabled: true
    paths:
      - ".github/workflows/*.yml"

pipeline:
  steps:
    - name: "assemble"
      cmd: "./gradlew assemble --no-daemon"
      timeout_seconds: 600
      diff_bounded:
        enabled: true
        patterns:
          - "**/*.kt"
          - "build.gradle*"
    - name: "lint"
      cmd: "./gradlew detekt"
      timeout_seconds: 300
      diff_bounded:
        enabled: true
        patterns:
          - "**/*.kt"
    - name: "test"
      cmd: "./gradlew test jvmTest --no-daemon"
      timeout_seconds: 900
      diff_bounded:
        enabled: false
    - name: "coverage"
      cmd: "./gradlew koverVerify koverXmlReport --no-daemon"
      timeout_seconds: 600
      diff_bounded:
        enabled: false
    - name: "benchmark"
      enabled: false
      cmd: "./gradlew fastBenchmark --no-daemon"
      timeout_seconds: 1200
      diff_bounded:
        enabled: true
        patterns:
          - "**/*Benchmark*.kt"
```

## Diff-bounded extraction

For each job in the CI file, the parser extracts:
- Job name
- Command from `run:` directive
- Timeout from `timeout-minutes:`
- Condition from `if:` (stored as a filter)
- Dependencies from `needs:`

## Graceful Degradation

- If no CI workflow files found → error with instructions to create one
- If workflow uses matrix or conditional steps → warns and includes only top-level commands
- If `cicd-config.yaml` already exists → prompts for overwrite or append
