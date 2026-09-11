# Quality Gate

Use JDK 21 before running Gradle. The build runtime/toolchain is 21; generated Android/JVM bytecode remains JVM 17 during stability recovery:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
```

On macOS, resolve JDK 21 through `/usr/libexec/java_home` instead of hard-coding a Homebrew path. The Phase 2 closure gate on 2026-09-07 used Eclipse Temurin 21.0.2 successfully.

Local Agent currently sees Oracle JDK 22 as the shell default while Temurin 21 is also installed. Detekt 1.23.x rejects JVM target 22, so every Local Agent Gradle/Detekt command must explicitly set `JAVA_HOME="$(/usr/libexec/java_home -v 21)"`. The final Phase 3 read-only audit reproduced the JDK-22 failure and then passed all-module Detekt on Temurin 21.0.2.

## Main Command

```bash
./gradlew qualityCheck
```

`qualityCheck` runs:

- ktlint checks.
- detekt static analysis.
- Android lint for Android modules.
- unit tests for Android and JVM modules.

Run build separately:

```bash
./gradlew :app:assembleDebug
```

Run secret scanning locally when `gitleaks` is installed:

```bash
gitleaks git --config .gitleaks.toml --redact --verbose
```

## Tooling Audit

| Tool | Status | Problem | Recommendation |
| --- | --- | --- | --- |
| Gradle | Configured | JDK 21 is the single installed build runtime/toolchain; bytecode target remains JVM 17 | Keep JDK 21 consistent across sandbox, CI and Mac |
| ktlint | Configured globally | `core:data/src` and `core:decoders/src` are excluded due existing large formatting debt | Pay down one excluded module at a time |
| detekt | Configured globally | Existing debt is baseline-gated in multiple modules | Treat baseline reduction as explicit refactor work |
| Android lint | Available via `lintDebug` | Must be run with Android toolchain available | Keep inside `qualityCheck` |
| Unit tests | Available | Coverage is uneven and mostly backend/classifier focused | Add tests with each behavior change |
| assembleDebug | Works as build verification target | Not part of `qualityCheck` to keep feedback shorter | Run after `qualityCheck` before installing |
| gitleaks | Configured in GitHub Actions | Local binary may not be installed on every machine | Run locally before history rewrite or release |
| adb/device smoke test | Optional | Requires unlocked connected phone | Run after debug APK build |
| Android emulator UI smoke | Configured in GitHub Actions | Runtime UI coverage is slower than unit/static gates | Require it for pre-field golden candidates; use physical ADB afterward for BLE hardware evidence |

## Current Baselines

- `core/data/detekt-baseline.xml`
- `core/decoders/detekt-baseline.xml`
- `feature/details/detekt-baseline.xml`
- `feature/radar/detekt-baseline.xml`
- `feature/settings/detekt-baseline.xml`
- `feature/watchlist/detekt-baseline.xml`

Baseline means: existing issues are acknowledged, but new issues should still fail detekt.

## ktlint Exceptions

The root ktlint configuration skips source files in:

- `core:data/src`
- `core:decoders/src`

Reason: these modules have large existing formatting debt. Formatting them should be a dedicated change, not a side effect of unrelated product work.

## Before Push

Minimum:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./gradlew qualityCheck
./gradlew :app:assembleDebug
gitleaks git --config .gitleaks.toml --redact --verbose
git diff --check
```

In the ChatGPT sandbox, use the restored offline Android/Gradle pack and `tools/sandbox/run-sandbox-gradle.sh`. Focused module gates may use 3 workers; broad `qualityCheck` and `:app:assembleDebug` use the bounded 2-worker sandbox profile and should be run sequentially.
## Pre-field Golden Release Gate

Before a Phase 3 field build is handed to the phone:

1. exact `main` SHA passes **Quality** and **Secret Scan**,
2. Android UI Smoke passes on production code and is explicitly rerun on the final documentation SHA,
3. rolling `latest-tester` is refreshed from the exact final SHA,
4. immutable `v1.0.0-phase3-pre-field.1` is created on that SHA,
5. versioned Tester Release reruns `qualityCheck` and `:app:assembleDebug`,
6. immutable APK and `.sha256` are present in GitHub Releases,
7. final checkpoint tag is created only after those publication gates succeed.

The emulator gate complements rather than replaces physical ADB/BLE validation in `PHASE3_FIELD_COLLECTION.md`.
