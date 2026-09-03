# Local Agent Flow

This file is the canonical ChatGPT-driven Local Agent workflow for `MichalMatu/tracker`. The daemon/runtime contract lives in `MichalMatu/local-agent` on `main`.

## Trigger

When the user says an equivalent of `uzyj local-agent flow`, `uzyj naszego flow`, `zrob to przez local agenta`, or `use local-agent flow`, read this file and follow it without asking the user to restate the architecture.

If the user also requests `autopilot`, autonomous iteration, `do skutku`, or `until green`, also read `LOCAL_AGENT_AUTOPILOT.md`.

## Roles

ChatGPT is the architect/programmer. It inspects source and repository rules, chooses exact changes and verification, queues deterministic tasks, reads real result evidence, diagnoses failures and publishes only validated source.

`local-agent` is a deterministic executor. It synchronizes repository-scoped tasks, prepares the disposable work checkout, applies declared changes, runs exact commands under bounded watchdogs, publishes status/run/result evidence and performs bounded cleanup/recovery. It must not invent fixes.

## Repository identity

- target repository: `MichalMatu/tracker`
- local-agent repository id: `tracker`
- agent binding: `be481b25-9d97-4205-b93f-95f5c5827441`
- normal source branch: `main`
- control branch: `agent-control`
- daemon repository: `MichalMatu/local-agent`
- production daemon branch: `main`

When Chat Bridge is active, every wake for this repository must carry exactly:

```text
[LA_AGENT=be481b25-9d97-4205-b93f-95f5c5827441]
[LA_REPO=tracker]
[LA_REPOSITORY=MichalMatu/tracker]
```

Do not infer or switch repositories from conversation history. Another repository requires explicit Chat Bridge **Rebind**. Before execution Local Agent requires the local registry binding, `.agent/binding.json` on `agent-control`, and the task's `agent_binding` to match exactly.

Machine-local registry:

```text
~/Library/Application Support/local-agent/repositories.json
```

Default Local Agent workspaces for this repository id:

```text
control clone: ~/agent-workspace/repos/tracker/control
work clone:    ~/agent-workspace/repos/tracker/work
checkpoints:   ~/agent-workspace/repos/tracker/checkpoints
daemon:        ~/local-agent
```

The disposable Local Agent work clone is not the user's normal checkout and may be reset according to Local Agent rules. Preserve ignored Gradle caches unless a clean build is specifically required.

## Repository rules

Always read root and path-specific `AGENTS.md` files before editing. The root file defines the Android architecture and coding constraints, including Kotlin-only implementation, Jetpack Compose, Hilt, Coroutines/Flow, type-safe Navigation Compose and the project theme rules.

Work directly on `main` unless the user explicitly requests another branch. Existing historical/development branches must not silently become the work branch.

## Queue and evidence

Repository-scoped control data on `agent-control`:

```text
.agent/binding.json
.agent/tasks/<task-id>.json
.agent/runs/<task-id>.json
.agent/results/<task-id>.json
.agent/status/daemon.json
```

Every executable task must contain:

```json
"agent_binding": "be481b25-9d97-4205-b93f-95f5c5827441"
```

Task ids and payloads are immutable within this repository. Use a new unique id for changed work. Interrupted claimed tasks are never silently replayed.

## Resource contract

Every task must declare `resources` explicitly.

- use `resources: []` for normal Android/Kotlin source work, Gradle builds, tests, lint, Detekt, ktlint and repository-local Git operations;
- use `resources: ["device:android-phone"]` for ADB/install/logcat or tests requiring the connected physical phone;
- use `resources: ["machine"]` only for genuine whole-host operations such as host-global toolchain mutation;
- `memory_limit_mb` is independent from resource classification.

A software-only task may use a larger RSS watchdog without becoming machine-exclusive.

## Java and Gradle

The project requires Java 17. Prefer the configured Java 17 toolchain. On the established Mac environment, if the default `java` is newer, Java 17 has been available at:

```text
~/.local/jdks/jdk-17.0.19+10/Contents/Home
```

Do not mutate the host-global JDK merely to run a repository build.

Use the Gradle wrapper from the repository. Typical verification commands include:

```bash
./gradlew <affected-module>:testDebugUnitTest
./gradlew <affected-module>:detekt
./gradlew <affected-module>:ktlintCheck
./gradlew qualityCheck
./gradlew :app:assembleDebug
```

Choose the narrowest realistic gate first. `qualityCheck` is the broad repository gate; do not repeatedly rerun it while a focused failure remains unresolved.

## Preferred task shape

For substantial work prefer `workflow_policy: "efficient-verification-v1"` with explicit verification levels. Software-only work normally uses `resources: []` and an appropriate memory watchdog, for example:

```json
{
  "id": "example-tracker-change",
  "agent_binding": "be481b25-9d97-4205-b93f-95f5c5827441",
  "mode": "commands",
  "work_branch": "main",
  "allow_write": true,
  "resources": [],
  "memory_limit_mb": 4096,
  "workflow_policy": "efficient-verification-v1",
  "steps": [
    {
      "name": "implement-and-focused-check",
      "command": "./gradlew <affected-task>",
      "verification_level": "focused"
    }
  ],
  "verify_steps": [
    {
      "name": "final-verification",
      "command": "./gradlew qualityCheck",
      "verification_level": "full"
    }
  ]
}
```

Adapt commands to the actual affected modules. Exactly one final `full` stage is required under this workflow policy.

## Standard development flow

1. Read `AGENTS.md` and relevant project documentation.
2. Inspect current `agent-control` binding/status and the exact run/result evidence for any active task.
3. Confirm repository id, repository and agent binding match this file.
4. Inspect source on the intended work branch, normally `main`.
5. Diagnose the requested change in ChatGPT and prepare the smallest deterministic edit.
6. Classify resources conservatively.
7. Queue a unique task with the exact `tracker` agent binding.
8. Follow the same task attempt while it runs; do not queue duplicates.
9. Inspect terminal exit codes, output, `git_status` and `git_diff`.
10. On failure, fix the smallest evidenced problem and rerun the focused gate first.
11. Broaden verification only after focused checks are green.
12. Review the exact final diff and publish validated source to `main` unless the user requested another branch/PR.
13. Treat source publication and physical-phone runtime verification as separate gates.

## Branch hygiene

Long-lived branches are not automatically disposable. Before merging or deleting a branch, compare it with `main`, inspect unique commits/files and verify its intended work. A branch containing unmerged product work must not be deleted merely because it is old.

## Source of truth

1. real Local Agent command/device output;
2. target source and tests;
3. remote run/result/status evidence;
4. ChatGPT analysis.

## Failure handling

On failure inspect the exact stage, command, exit code/output, `git_diff` and `git_status`. Distinguish source failures from Gradle/JDK/cache/ADB/infrastructure failures. Do not weaken quality gates or add suppressions merely to make a build green unless the suppression is explicitly justified by repository policy.

Binding failures are safety evidence. Do not change or guess `agent_binding` to make a task pass; correct the explicit registry/control/bridge configuration.
