# Quality Gate

Use Java 17 before running Gradle locally:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

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
| Gradle | Configured | Java 17 is required and not always auto-discovered | Keep the `JAVA_HOME` command documented |
| ktlint | Configured globally | `core:data/src` and `core:decoders/src` are excluded due existing large formatting debt | Pay down one excluded module at a time |
| detekt | Configured globally | Existing debt is baseline-gated in multiple modules | Treat baseline reduction as explicit refactor work |
| Android lint | Available via `lintDebug` | Must be run with Android toolchain available | Keep inside `qualityCheck` |
| Unit tests | Available | Coverage is uneven and mostly backend/classifier focused | Add tests with each behavior change |
| assembleDebug | Works as build verification target | Not part of `qualityCheck` to keep feedback shorter | Run after `qualityCheck` before installing |
| gitleaks | Configured in GitHub Actions | Local binary may not be installed on every machine | Run locally before history rewrite or release |
| adb/device smoke test | Optional | Requires unlocked connected phone | Run after debug APK build |

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
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew qualityCheck
./gradlew :app:assembleDebug
gitleaks git --config .gitleaks.toml --redact --verbose
git diff --check
```
