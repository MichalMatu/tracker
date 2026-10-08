# Tracker engineering instructions

These rules are authoritative for AI-assisted work in `MichalMatu/tracker`.

## Current sources of truth

Before substantial work read:

1. `docs/README.md`;
2. `docs/PRODUCT_GOAL.md`;
3. `docs/ARCHITECTURE_CURRENT.md` when changing boundaries/data flow;
4. `docs/DETECTION_MODEL.md` when changing classification, identity or risk semantics;
5. `docs/QUALITY_GATE.md` and `docs/SANDBOX_EXECUTION_FLOW.md` before execution.

Do not reconstruct current work from old commits or deleted phase handoffs. Git history is provenance, not an execution queue.

## Hard technical constraints

- Kotlin only for application code.
- Jetpack Compose + Material 3; do not add XML UI, Fragments, ViewBinding/DataBinding or additional Activities.
- Hilt for DI.
- Coroutines + Flow/StateFlow for concurrency/state; do not introduce RxJava, AsyncTask or LiveData.
- Navigation Compose with serializable type-safe routes; no string-route graphs for new work.
- Kotlin Serialization for first-party JSON contracts.
- Gradle Kotlin DSL + version catalog.
- JDK 21 runs Gradle/compilers; generated Android/JVM bytecode stays JVM 17 until an explicit migration changes that policy.

## Architecture

- `core:model`: shared domain data types.
- `core:domain`: Android-light contracts/use cases; do not leak `core:data` implementations through it.
- `core:data`: Room, Bluetooth/scanner/service, classification, persistence, session/alert implementation.
- `core:decoders`: decoder library.
- `core:ui`: shared Compose theme/tokens/components.
- `feature:*`: presentation and feature ViewModels; features should depend on domain-facing contracts, not directly on `core:data`.

Do not expose `BluetoothGatt`, `BluetoothDevice`, Room entities or raw Android data-layer types to presentation. Preserve the local-first scan -> parse -> persist -> analyze -> UI direction.

Passive observation is the default. Active GATT collection remains explicit opt-in; do not silently re-enable periodic RFCOMM or other active probing.

## Product/evidence rules

- Bluetooth observations are evidence, not proof of identity, intent, ownership or exact location.
- RSSI is signal context, not exact ranging.
- High-attention UI must explain the evidence that caused it.
- Active evidence must remain distinguishable from passive evidence.
- AI/cloud features are optional and may not become dependencies of local scanning/alerts.
- Never commit private field telemetry: exact GPS, MACs, raw private captures, Room/WAL/SHM, HCI snoop or bugreports.

## Compose/design rules

- Use `MaterialTheme` and the project semantic/extended color roles; do not hardcode feature colors.
- Reuse `Dimens`/design tokens instead of scattering magic dimensions.
- Keep live list geometry stable where values update frequently.
- Add previews for meaningful reusable/new UI surfaces where practical.
- Remove unused imports, dead/commented code and incidental leftovers in files you modify.

## Git policy

Work directly on `main` unless the user explicitly requests a branch/PR. `agent-control` is infrastructure only and must never carry application source changes.

Use direct GitHub writes for bounded source/config/docs changes when the exact diff plus CI can verify them. A commit proves publication, not execution.

## Verification ladder

Use the narrowest useful evidence first:

1. diff/static invariants (`git diff --check`, targeted inspection);
2. affected module unit/static checks;
3. `./gradlew qualityCheck`;
4. `./gradlew :app:assembleDebug`;
5. physical-device validation only when Android/Bluetooth runtime behavior requires it.

Do not broaden baselines or suppress real failures merely to get green output.

## Worker selection

- **ChatGPT sandbox:** source analysis, patches, static checks, focused JVM/Gradle work when dependencies are available.
- **GitHub Actions:** canonical networked JDK 21 Android build/quality/secret scan for exact source SHA.
- **Local Agent / Mac:** ADB, physical phone, Bluetooth/screen-off runtime evidence and genuinely Mac-specific reproduction.

Do not use the Mac as generic CI when sandbox/GitHub evidence is equivalent.

## Local Agent contract

Repository registration:

```text
repository:    MichalMatu/tracker
repository id: tracker
agent binding: be481b25-9d97-4205-b93f-95f5c5827441
source branch: main
control branch: agent-control
workspace:     ~/agent-workspace/repos/tracker/{control,work,checkpoints}
```

Before execution read `.agent/binding.json` and `.agent/status/daemon.json` on `agent-control`; registry, control binding and task `agent_binding` must match exactly. Declare resources honestly: software-only repository work uses `resources: []`; physical Android work uses the named `android:s22` resource so another task cannot operate the phone concurrently.

Physical Android validation uses the Samsung Galaxy S22+ (`SM-S906B`) over **wireless ADB** by default. Do not store device serials, private IP addresses or rotating wireless-debugging ports in repository documentation. Reconnect with `adb start-server`, then use the currently discoverable `Android-2.local:<port>` endpoint or `adb mdns services`. Verify `ro.product.model=SM-S906B` before installation, launch, BLE, location or UI actions. Wireless debugging ports rotate, so discovery is authoritative and any remembered endpoint is only a temporary local hint.

Task ids/payloads are immutable. Inspect `.agent/runs/<id>.json` and terminal `.agent/results/<id>.json`. Do not edit the daemon control clone, launch local Codex from a task, restart the shared supervisor from repository work, or queue a duplicate while a healthy task is active.

If exact evidence proves an active task cannot succeed, cancel that exact task, wait for terminal cancellation/result evidence, then queue a new uniquely-id'd replacement. Source publication and physical-device verification remain separate gates.

Canonical Local Agent runtime policy lives in `MichalMatu/local-agent`; this repository keeps only the binding/repository-specific rules above.
