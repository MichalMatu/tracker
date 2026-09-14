# Quality gate

## Toolchain

- Gradle/compiler runtime: **JDK 21**.
- Generated Android/JVM bytecode target: **JVM 17**.
- On macOS use `export JAVA_HOME="$(/usr/libexec/java_home -v 21)"` before Gradle when the shell default differs.

## Standard gate

```bash
./gradlew qualityCheck
./gradlew :app:assembleDebug
git diff --check
```

`qualityCheck` is the broad repository gate for static checks/lint/tests. Run secret scanning when available:

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

## Physical validation

Use a real phone for BLE, permission, background/screen-off, alert, location and device-specific runtime behavior. Record exact app/source SHA separately from the field observations.
