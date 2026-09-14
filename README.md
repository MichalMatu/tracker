# BlueEye Tracker

Android Bluetooth/BLE situational-awareness app focused on local-first collection, explainable evidence and conservative decisions.

## What it does

- observes nearby BLE and Classic Bluetooth devices;
- keeps device, signal and session evidence locally;
- supports watchlist returns and cautious Follow-Me/identity analysis;
- reduces protocol noise before it reaches the main Radar view;
- keeps technical/raw Bluetooth data inspectable without making it the default UI;
- can export structured session evidence for deeper review.

BlueEye reports observations and evidence. It does **not** claim to identify a person, infer malicious intent from Bluetooth presence, turn RSSI into precise distance, or treat the phone's GPS observation point as the exact location of another device.

## Current status

- Source of truth: `main`.
- Phase 3 scanner/ingest field reacceptance: **closed / accepted**.
- Compact Radar cards and lightweight Radar projection: accepted.
- Details progressive disclosure and collapsed Technical Details: implemented.
- Recent field work reduced false positives/protocol flooding and added the Live Nearby signal view.
- Active product work: field validation, Radar/Details quality and the deterministic analysis pipeline.
- Planned developer workstream: optional Drive + Gmail telemetry/AI feedback bridge.

Current plans:

- [UI/UX and analysis plan](docs/UI_UX_REDESIGN_PLAN.md)
- [Telemetry & AI feedback bridge plan](docs/TELEMETRY_AI_FEEDBACK_BRIDGE_PLAN.md)

## Architecture

```text
Bluetooth observations
  -> normalize / parse
  -> bounded ingest
  -> local persistence
  -> identity / classification / Follow-Me analysis
  -> evidence + alerts
  -> Radar / Details / history
  -> optional reduced external analysis
```

Modules:

```text
app            entry point, navigation, Hilt wiring
core:model     shared domain data types
core:domain    contracts and use cases
core:data      Room, scanning, service, classification, sessions/alerts
core:decoders  Bluetooth decoders
core:ui        shared Compose design system
feature:*      Radar, Details, Settings, Watchlist UI/ViewModels
```

See [Current Architecture](docs/ARCHITECTURE_CURRENT.md) and [Detection Model](docs/DETECTION_MODEL.md).

## Local-first rule

Scanning, persistence, deterministic classification and baseline alerts must work without cloud/AI access. Optional external analysis receives bounded structured evidence and remains separate from the local verdict.

## Build and verify

Requirements: Android SDK and **JDK 21**. Generated Android/JVM bytecode remains targeted at **JVM 17**.

```bash
./gradlew qualityCheck
./gradlew :app:assembleDebug
git diff --check
```

When available:

```bash
gitleaks git --config .gitleaks.toml --redact --verbose
```

The canonical networked build is GitHub Actions. Physical BLE/runtime behavior is verified separately on a real Android device.

## Tester build

- [Latest tester release](https://github.com/MichalMatu/tracker/releases/tag/latest-tester)
- [All releases](https://github.com/MichalMatu/tracker/releases)

Tester APKs are engineering/debug artifacts unless a release explicitly says otherwise.

## Documentation

Start at [docs/README.md](docs/README.md). The repository intentionally keeps only current documentation plus one concise history file; detailed old phase audits/handoffs remain recoverable from Git history instead of living beside active instructions.

Key documents:

- [Product Goal](docs/PRODUCT_GOAL.md)
- [Current Architecture](docs/ARCHITECTURE_CURRENT.md)
- [Detection Model](docs/DETECTION_MODEL.md)
- [Quality Gate](docs/QUALITY_GATE.md)
- [Engineering Execution Flow](docs/SANDBOX_EXECUTION_FLOW.md)
- [Field Session Checklist](FIELD_SESSION_CHECKLIST.md)

## Contributing and security

Read [CONTRIBUTING.md](CONTRIBUTING.md) before substantial changes. Never commit private field telemetry such as exact GPS, MAC addresses, Room/WAL/SHM databases, HCI snoops or bugreports.

Security reporting guidance: [SECURITY.md](SECURITY.md).

No license file is currently present; reuse rights are therefore not explicitly granted.
