# BlueEye Tracker

Android app for observing nearby Bluetooth/BLE devices, keeping a watchlist, and showing alerts when selected devices reappear.

This repository is a Kotlin / Jetpack Compose Android project. The current code is usable for development, but detection confidence and architecture still need hardening before the app should present strong claims to the user.

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

Use Java 17:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew qualityCheck
./gradlew :app:assembleDebug
```

Secret scanning is configured in GitHub Actions. To run it locally:

```bash
gitleaks git --config .gitleaks.toml --redact --verbose
```

Details, current exceptions, and baseline policy are in [docs/QUALITY_GATE.md](docs/QUALITY_GATE.md).

## Documentation

- [Product goal](docs/PRODUCT_GOAL.md)
- [Current architecture](docs/ARCHITECTURE_CURRENT.md)
- [Pipeline audit](docs/PIPELINE_AUDIT.md)
- [Quality gate](docs/QUALITY_GATE.md)
- [Detection confidence](docs/DETECTION_CONFIDENCE.md)
- [Evidence model](docs/EVIDENCE_MODEL.md)

## Git Workflow

Work directly on `main`. Do not create new branches unless the user explicitly asks for it.
