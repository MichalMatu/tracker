# BlueEye Tracker

Android app for observing nearby Bluetooth/BLE devices, keeping a watchlist, and showing alerts when selected devices reappear.

This repository is a Kotlin / Jetpack Compose Android project. The current code is usable for development, but detection confidence and architecture still need hardening before the app should present strong claims to the user.

## Current Stabilization Status

Feature development is frozen while the runtime is reduced to a verified BLE-only Stable Core. The canonical execution checklist is [docs/STABILITY_RECOVERY_GUIDE.md](docs/STABILITY_RECOVERY_GUIDE.md), and the exact Phase 1 boundary is [docs/STABLE_CORE_PREIMPLEMENTATION_AUDIT.md](docs/STABLE_CORE_PREIMPLEMENTATION_AUDIT.md).

Work directly on `main`; `agent-control` is reserved for Local Agent infrastructure. Historical MVP/audit documents remain useful context, but they do not override the recovery checklist while the feature freeze is active.

## Engineering Execution Model

During stabilization, use the compute workers by role instead of treating the Mac as the default CI machine:

- **ChatGPT sandbox** — source inspection, patch preparation, static work, test selection, pure JVM/Kotlin probes and Android Gradle work when an offline cache pack is restored.
- **GitHub Actions** — canonical JDK 21 `qualityCheck`, debug APK build, dependency/network work and reusable sandbox pack generation.
- **Local Agent / Mac** — ADB, physical phone, lock-screen, Bluetooth runtime and other Mac/device-specific evidence.

The canonical bootstrap/cache policy, sandbox memory profile and fresh-session steps are in [docs/SANDBOX_EXECUTION_FLOW.md](docs/SANDBOX_EXECUTION_FLOW.md). Portable sandbox bootstrap assets are kept in ChatGPT Library under `/Tracker/Sandbox/`.

## Current Scope

- Passive BLE and Bluetooth observation.
- Radar screen with currently observed devices.
- Device details, parsed metadata, RSSI history, and raw data views.
- Watchlist alerts for known/reappearing devices.
- Foreground scanning service.
- Decoder library for common BLE ecosystems and sensors.

The app must not claim that a person, agency, or intent was identified from Bluetooth alone. It can only show evidence, confidence, and reasons.

## Project Shape

- `app`: single Android app module, `MainActivity`, Navigation Compose, Hilt module wiring.
- `core:model`: shared domain-ish models.
- `core:domain`: repository contracts and use cases.
- `core:data`: Room, Bluetooth scanning, scanner service, classification, tracking/session logic.
- `core:decoders`: BLE manufacturer/service data decoders.
- `core:ui`: Compose theme, design tokens, shared UI utilities.
- `feature:*`: Compose screens and ViewModels.

Known architecture gaps are documented in [docs/ARCHITECTURE_CURRENT.md](docs/ARCHITECTURE_CURRENT.md).

## Quality Gate

Use JDK 21 as the build runtime/toolchain. Android/Kotlin bytecode remains targeted at JVM 17 during stability recovery:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew qualityCheck
./gradlew :app:assembleDebug
```

Secret scanning is configured in GitHub Actions. To run it locally:

```bash
gitleaks git --config .gitleaks.toml --redact --verbose
```

Details, current exceptions, and baseline policy are in [docs/QUALITY_GATE.md](docs/QUALITY_GATE.md).

## Documentation

- [Stability recovery guide](docs/STABILITY_RECOVERY_GUIDE.md)
- [Stable Core preimplementation audit](docs/STABLE_CORE_PREIMPLEMENTATION_AUDIT.md)
- [Sandbox-first execution flow](docs/SANDBOX_EXECUTION_FLOW.md)
- [Product goal](docs/PRODUCT_GOAL.md)
- [Current architecture](docs/ARCHITECTURE_CURRENT.md)
- [Pipeline audit](docs/PIPELINE_AUDIT.md)
- [Quality gate](docs/QUALITY_GATE.md)
- [Detection confidence](docs/DETECTION_CONFIDENCE.md)
- [Evidence model](docs/EVIDENCE_MODEL.md)

## Git Workflow

Work directly on `main`. Do not create new branches unless the user explicitly asks for it.
