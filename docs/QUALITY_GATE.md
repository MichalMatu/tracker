# Quality gate

## Toolchain

- Gradle/compiler runtime: **JDK 21**.
- Generated Android/JVM bytecode target: **JVM 17**.
- Android build baseline: **compileSdk 36 / targetSdk 36**, Android Gradle Plugin **8.9.1**, Gradle **8.11.1**.
- On macOS use `export JAVA_HOME="$(/usr/libexec/java_home -v 21)"` before Gradle when the shell default differs.

## Standard gate

```bash
./gradlew qualityCheck
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
git diff --check
```

`qualityCheck` is the broad repository gate for static checks/lint/tests. The debug build remains the tester artifact; the release build is a configuration smoke test until production signing is configured. Run secret scanning when available:

```bash
gitleaks git --config .gitleaks.toml --redact --verbose
```

GitHub Actions is the canonical networked exact-SHA Android verification.

## Evidence ladder

Use the cheapest realistic evidence first:

1. changed-file/static invariant checks;
2. affected module tests and detekt/ktlint/lint;
3. repository `qualityCheck`;
4. `:app:assembleDebug`;
5. emulator/device validation when behavior depends on Android/Bluetooth runtime.

Do not repeatedly run the full gate while a focused failure is still unresolved.

A source commit is not device evidence. A device smoke test is not a replacement for source/CI verification.

## Existing debt

Detekt baselines currently exist in several modules, including `core:data`, `core:decoders` and feature modules. Root ktlint also excludes `core:data/src` and `core:decoders/src` because of existing formatting debt.

Rules:

- baselines/exclusions acknowledge existing debt only;
- new unrelated violations should still fail;
- do not expand a baseline or suppression just to make a change green;
- formatting/baseline reduction should be deliberate, reviewable work.

## Docs-only changes

For documentation-only cleanup, verify at minimum:

- no stale/missing document references remain;
- `git diff --check`/equivalent whitespace validation passes;
- canonical docs index and root README agree;
- normal CI may still run automatically, but an Android rebuild is not required solely to prove prose correctness unless the change also touches workflow/build configuration.

## Production and privacy release gate

Before calling any artifact production-ready, verify the exact candidate SHA with the normal quality/build gates plus all of the following:

- build a release artifact deliberately; tester/debug APKs are not production artifacts;
- keep production signing keys and signing properties outside Git;
- use a monotonically increasing `versionCode` and an intentional `versionName`;
- verify current Play/Android target-SDK and build-tool requirements before release;
- review Bluetooth/location/notification permissions and Play Data safety against actual runtime behavior;
- keep local BLE/location observation data out of Android backup and repository history;
- review global cleartext-network allowance before production distribution;
- run release-variant checks plus physical-device BLE/background acceptance on the exact release candidate.

Repository hardening rules: `.gitignore` must cover signing material, local environment secrets and private capture/database artifacts; `CODEOWNERS` may define default review ownership. Dependency-update bots are intentionally not enabled.

## Physical debug installation and validation

- CI debug APKs may be signed with a different ephemeral debug key from the APK already on a phone. GitHub CI success does **not** guarantee signature-compatible `adb install -r`.
- For an update preserving app data, verify the target device, exact source SHA, installed certificate and candidate APK signing certificate. If signatures differ, rebuild the exact SHA with the existing compatible local key before using `adb install -r`.
- **Never uninstall, clear app data, downgrade or change the signing key merely to bypass `INSTALL_FAILED_UPDATE_INCOMPATIBLE`**. A failed update leaves existing app data intact.
- Keep separate verdicts for APK build, ADB installation, explicit activity launch/process health and actual BLE/background/Follow-Me field behavior. If an ADB launcher command fails, check manifest activity resolution and start the explicit component before claiming a runtime failure.
- Use a real phone for BLE, permissions, background/screen-off, alerts, location and device-specific behavior. Record exact source SHA, APK provenance and privacy-safe observations; keep serials, exact GPS, Bluetooth addresses and raw logcat out of Git.
