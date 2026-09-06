# Tracker Sandbox-First Execution Flow

Status: **ACTIVE during stability recovery**  
Canonical product/recovery plan: `docs/STABILITY_RECOVERY_GUIDE.md`

## 1. Goal

Use ChatGPT's sandbox as the default compute/analysis worker, GitHub Actions as the canonical networked Android build worker, and the Mac Local Agent only for operations that genuinely require the physical Mac/Android phone.

This flow exists to:

- reduce load and long Gradle runs on the MacBook,
- make new chats/sessions reproducible without rebuilding the environment from memory,
- preserve tool/bootstrap assets in ChatGPT Library,
- keep one source of truth (`main`) and avoid branch drift,
- run the narrowest useful test first, then broaden only when evidence warrants it,
- separate source correctness, Android build correctness and physical-device correctness.

## 2. Worker roles

### A. ChatGPT sandbox — default engineering worker

Use for:

- repository/source inspection after materialization,
- grep/search, code generation and patch preparation,
- static consistency checks,
- `git diff --check`, source inventory and architecture audits,
- test selection based on changed paths,
- pure JVM/Kotlin probes when dependencies are available,
- Android Gradle tasks only when an offline Android/Gradle cache pack has been restored,
- producing reports, fixtures, generated data and other non-device artifacts.

Current measured sandbox baseline (2026-09-06):

- Debian 13 x86_64,
- 5 vCPU,
- about 5.8 GiB RAM,
- about 30 GiB usable disk,
- OpenJDK 21 available,
- standalone Kotlin 1.9.0 available,
- git, Python, cmake, ninja, zstd, rsync and unzip available,
- direct shell internet/DNS is not reliable/available,
- Android SDK is not present in a fresh sandbox.

Therefore a new sandbox must restore tool/cache assets from Library rather than attempting ad-hoc internet installation.

### Java/JVM standard

During stability recovery there is exactly one installed JDK baseline:

- **JDK 21** runs Gradle and the Kotlin/Java compilers in sandbox, GitHub Actions and Local Agent/Mac.
- JVM modules use JDK 21 toolchains.
- Android/Java `sourceCompatibility` and `targetCompatibility` stay at **17**.
- Kotlin `jvmTarget` stays at **17**.
- Do not install JDK 17 merely to produce JVM 17 bytecode.
- Do not raise application bytecode to JVM 21 during the recovery freeze; that is a separate post-stability modernization task.

This keeps one runtime/toolchain version without changing the Android compatibility surface while runtime bugs are being isolated.

### B. GitHub Actions — canonical full Android build worker

Use for:

- JDK 21 canonical verification,
- dependency resolution/network downloads,
- `qualityCheck`,
- `:app:assembleDebug`,
- secret scan,
- generation of source/cache snapshots for sandbox bootstrap.

GitHub Actions is the canonical answer when a fresh sandbox does not yet contain a compatible Android SDK/Gradle dependency pack. Do not push a full Android build back onto the Mac just because the sandbox is missing network dependencies.

### C. Local Agent on Mac — hardware/machine evidence worker

Reserve for:

- ADB,
- install/uninstall APK,
- logcat,
- lock-screen/screen-off tests,
- Bluetooth permission/system-behavior tests,
- physical-device runtime verification,
- Mac-specific filesystem/tool evidence.

Normal Kotlin edits, static audits and repeatable Gradle verification should not consume Mac resources by default.

## 3. Persistent Library layout

Persistent ChatGPT Library folder:

`/Tracker/Sandbox/`

Required files:

- `tracker-sandbox-kit-2026-09-06.tar.zst`
- `tracker-sandbox-kit-2026-09-06.tar.zst.sha256`
- `README-FIRST.md`
- `sandbox-bootstrap-manifest.json`

Future cache packs use these names:

- `tracker-source-<git-sha>.tar.zst`
- `tracker-source-<git-sha>.tar.zst.sha256`
- `tracker-android-offline-<dependency-key>/manifest/*`
- `tracker-android-offline-<dependency-key>/part-00` ... `part-N`

The full offline pack is split into transport chunks below 512 MiB because connector artifact downloads have a per-file size limit. Reassemble chunks and verify both per-part and full-pack SHA-256 before extraction.

Never put GitHub tokens, signing keys, Android keystores, account cookies or other secrets in Library cache packs.

## 4. Fresh-chat bootstrap protocol

A future chat that needs substantial Tracker work should do this before expensive execution:

1. Read `AGENTS.md`.
2. Read `docs/STABILITY_RECOVERY_GUIDE.md` and its `NEXT ACTION`.
3. Read this file.
4. Inspect current `main` SHA and Local Agent state.
5. Search ChatGPT Library under `/Tracker/Sandbox/`.
6. Materialize the newest compatible sandbox kit and verify its SHA-256.
7. Extract it to `/mnt/data/tracker-sandbox-kit` (or equivalent).
8. Run:

   ```bash
   bin/bootstrap-sandbox.sh /mnt/data/tracker-sandbox
   source /mnt/data/tracker-sandbox/env.sh
   ```

9. Restore a source snapshot matching the intended SHA, or generate a new source snapshot when the cached one is stale.
10. Run `bin/sandbox-doctor.sh`.
11. If a matching offline Android/Gradle pack is present, run focused Android tests in `--offline` mode before the broad gate.
12. Use GitHub Actions as the canonical networked cross-check; use Local Agent only for device/Mac evidence.

Do not silently use a source snapshot with a different SHA than the task target.

## 5. Sandbox Gradle resource profile

The project-level `gradle.properties` is intentionally conservative because it was tuned for an 8 GB Mac. The sandbox must use an isolated `GRADLE_USER_HOME` profile instead of rewriting the project file for every environment.

Sandbox base profile for 5 vCPU / ~5.8 GiB RAM:

```properties
org.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=640m -XX:+UseG1GC -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.workers.max=2
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.daemon=true
org.gradle.daemon.idletimeout=600000
org.gradle.vfs.watch=false
kotlin.incremental=true
kotlin.incremental.java=true
kotlin.daemon.jvmargs=-Xmx512m -XX:MaxMetaspaceSize=320m
```

Execution profiles:

- **Focused module tests/builds:** `tools/sandbox/run-sandbox-gradle.sh` uses 3 workers for CPU throughput.
- **Broad `qualityCheck` / `:app:assembleDebug`:** the wrapper automatically drops to 2 workers.
- Run `qualityCheck` and `:app:assembleDebug` as separate commands in the sandbox. Keeping them sequential avoids holding lint/test/build peaks at once.

Measured reason for this split (2026-09-06): a full offline gate at 3 workers with a 2 GiB Gradle heap caused the Gradle daemon to be killed while lint/detekt/tests overlapped. Reducing to 2 workers and a 1.5 GiB Gradle heap removed that memory-pressure failure mode; do not raise heap as the first response to an OOM.

Other rationale:

- file watching is disabled because sandbox workspaces are disposable and VFS watch gives little value.
- configuration/build cache stay enabled because repeated focused checks are common.
- the sandbox wrapper uses `--offline`; a missing dependency is a cache invalidation signal, not a reason to improvise network installation.

## 6. Test ladder — fastest evidence first

Every change should use the narrowest realistic gate first.

### Level 0 — zero/near-zero cost

- `git diff --check`
- targeted grep/static invariant checks
- changed-file/path inspection

### Level 1 — affected module tests

Examples:

- `:core:model:test`
- `:core:domain:test`
- `:core:decoders:testDebugUnitTest`
- `:core:data:testDebugUnitTest`
- `:feature:radar:testDebugUnitTest`
- `:feature:settings:testDebugUnitTest`
- `:feature:details:testDebugUnitTest`
- `:feature:watchlist:testDebugUnitTest`

Use `tools/sandbox/select-gate.sh` to derive a first-pass command from changed paths.

### Level 2 — focused static/tooling gate

Run the directly affected module's detekt/ktlint/lint task where applicable.

### Level 3 — repository gate

```bash
./gradlew qualityCheck
```

### Level 4 — debug application build

```bash
./gradlew :app:assembleDebug
```

### Level 5 — physical phone

Only when behavior depends on Android runtime/hardware:

- install APK,
- permissions,
- BLE start/stop,
- lock/unlock,
- Bluetooth OFF/ON,
- alert cancellation,
- logcat/session export.

A source commit is not device evidence. A green sandbox test is not device evidence. A successful device smoke test is not a substitute for the full source/quality gate.

## 7. Data flow

Preferred loop:

```text
GitHub main (source of truth)
        |
        v
source snapshot / changed files
        |
        v
ChatGPT sandbox
  - inspect
  - patch
  - focused checks
  - test selection
        |
        v
push exact source to main
        |
        v
GitHub Actions
  - JDK 21 runtime/toolchain
  - JVM 17 bytecode target
  - qualityCheck
  - assembleDebug
  - gitleaks
        |
        +---- green, no device behavior needed ----> DONE
        |
        v
Local Agent / Android phone
  - install / ADB / lock-screen / runtime evidence
        |
        v
update recovery guide + work log
```

This avoids using the Mac as a general-purpose CI server.

## 8. Cache keys and invalidation

### Sandbox kit

Invalidate only when the sandbox bootstrap/tool profile changes.

### Source snapshot

Key by exact Git commit SHA. Never reuse for another SHA.

### Android/Gradle offline pack

Dependency key must include at least hashes/versions of:

- `gradle/wrapper/gradle-wrapper.properties`,
- `gradle/libs.versions.toml`,
- root/module `build.gradle.kts`,
- `settings.gradle.kts`,
- compile SDK/build-tools requirement,
- build JDK major version (21).

A source-only Kotlin change should not invalidate the dependency pack.

When the dependency key changes, generate a new pack; do not mutate an old pack in place.

## 9. Source snapshot policy

The source snapshot is a transport/cache artifact, not a source of truth.

- Canonical source remains GitHub `main`.
- Snapshot name contains exact SHA.
- On restore, verify SHA before editing/testing.
- Do not push `.gradle`, `build/`, local Android SDK, secrets or Library bundles into the repository.

## 10. CI optimization policy

Keep a single warm job for full `qualityCheck` + `assembleDebug` unless measurements prove splitting jobs is faster. Splitting those tasks into separate clean runners duplicates Kotlin/Android compilation.

Use `gradle/actions/setup-gradle` as the primary Gradle cache mechanism. Avoid redundant competing Gradle caches when possible.

For CI, override the Mac survival limits with runner-appropriate values instead of changing project defaults solely for CI.

## 11. Mac load policy

Default rule during stability recovery:

- **No Mac full gate** if the same exact SHA already has a successful canonical GitHub Actions full gate.
- Use Mac Gradle only when local-machine-specific reproduction is part of the bug.
- Use Mac/Local Agent for phone steps after source/CI is green.

This keeps scarce local RAM/CPU available for Android Studio/ADB and interactive work.

## 12. Completion bookkeeping

After a substantial task:

1. record exact SHA,
2. record focused checks actually run,
3. record GitHub Actions result,
4. record device evidence separately if any,
5. update `docs/STABILITY_RECOVERY_GUIDE.md`, especially `NEXT ACTION` and Work Log,
6. refresh Library source/cache packs only when their keys changed.
