# Engineering execution flow

`main` is the source of truth. Choose the worker that produces the needed evidence with the least machine-specific work.

## Worker roles

### ChatGPT sandbox

Default for source inspection, patch preparation, static checks, test selection, pure JVM work and Android Gradle tasks when a compatible offline cache is present.

Fresh sandboxes may not have Android dependencies/network access. Do not improvise random tool installs when a reproducible cache/CI path exists.

Persistent bootstrap/cache assets, when needed, live under ChatGPT Library `/Tracker/Sandbox/`; verify checksums and exact source SHA before using them. Never put secrets/signing material in cache packs.

### GitHub Actions

Canonical networked JDK 21 Android worker for dependency resolution, `qualityCheck`, `:app:assembleDebug`, secret scanning and exact-SHA CI evidence.

### Local Agent / Mac

Reserve for ADB, physical Android/Bluetooth behavior, install/logcat, screen-off/permission tests and genuinely Mac-specific reproduction. Do not use the Mac as default CI when sandbox/GitHub can provide equivalent evidence.

Repository-specific Local Agent binding/task rules are in `AGENTS.md`; the Local Agent runtime contract lives in `MichalMatu/local-agent`.

## Toolchain

JDK 21 runs Gradle and compilers everywhere. Android/Java/Kotlin output remains JVM 17. Do not require a separate JDK 17 installation merely to target JVM 17 bytecode.

## Normal sequence

```text
inspect exact main SHA
  -> prepare smallest change
  -> focused static/module checks
  -> publish/review exact diff
  -> GitHub Actions broad gate
  -> physical device only if runtime/hardware behavior requires it
  -> update active plan/status when warranted
```

For code work, broaden verification only after focused checks are green. See `QUALITY_GATE.md`.

## Sandbox rules

- Use an exact source SHA; cached source is transport, never source of truth.
- Use isolated Gradle/cache state instead of changing project build settings for the sandbox.
- Prefer repository wrappers under `tools/sandbox/` when a compatible offline pack exists.
- A missing offline dependency is a cache invalidation/CI issue, not permission to silently change dependency versions.
- Avoid overlapping broad lint/test/build memory peaks; run broad `qualityCheck` and app build sequentially when resources are constrained.

## Local Agent rules

Before local execution verify the tracker repository binding and daemon state on `agent-control`. Do not race a healthy active task or create a duplicate. Local Agent is the executor, not the planner: changes/commands must be bounded and evidence-driven.

A GitHub commit proves publication only. A Local Agent result proves only the commands/device evidence it actually reports. Keep those claims separate.

## Completion record

For substantial changes record:

- exact final SHA;
- focused checks actually run;
- broad CI/build result when applicable;
- physical-device evidence when applicable;
- active-plan update only when the verified project state changed.

Do not create a new dated handoff document for ordinary continuation; update the canonical plan or issue instead.
