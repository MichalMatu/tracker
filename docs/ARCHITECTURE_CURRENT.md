# Current architecture

BlueEye is a local-first Android/Kotlin application. `main` is the source of truth.

## Modules

| Module | Responsibility |
| --- | --- |
| `app` | Android entry point, Hilt wiring, Navigation Compose |
| `core:model` | Shared domain/evidence models, deterministic analysis models and versioned Analysis Bundle schema |
| `core:domain` | Repository/runtime contracts/use cases plus pure deterministic analysis reduction and bundle building |
| `core:data` | Room, Bluetooth scanning, foreground service, classification, persistence, sessions and alerts |
| `core:decoders` | BLE/Bluetooth decoders |
| `core:ui` | Theme, dimensions and shared Compose UI |
| `feature:radar` | Radar projection/presentation |
| `feature:details` | Device evidence/history/technical Details |
| `feature:settings` | Settings and session/export controls |
| `feature:watchlist` | Watchlist UI |

Features should consume domain-facing contracts rather than `core:data` implementations.

## Runtime data flow

```text
ScannerService
  -> BleScanner (BLE + opportunistic Classic discovery)
  -> ScanIngestPipeline (bounded ingest/coalescing)
  -> BLE / Classic handlers
  -> physical classification + protocol capabilities + identity / Follow-Me analysis
  -> DevicePersister / SignalSamplePersister / event history
  -> domain Device/evidence projections
  -> Radar / Details / Watchlist / export / alerts
```

Radar uses a lightweight projection rather than loading the full technical/evidence object graph for every list row. Technical/raw evidence stays available in Details.

## Accepted runtime boundaries

- Passive observation is the default.
- Automatic active GATT collection is an explicit opt-in through domain-facing settings/UI.
- Periodic RFCOMM probing remains disabled unless a future product decision adds a separate explicit boundary.
- Direct `BleScanner` lifecycle ownership belongs to `ScannerService`; feature code controls scanning through domain-facing contracts.
- BLE hardware lifecycle and ingest processing are separated so queue/accounting behavior is testable without changing scanner ownership.
- Device/tracking persistence and signal-sample persistence are separated.
- High-attention classifications must surface evidence; final device labels alone are not sufficient.
- Physical device type, protocol capability and tracking risk are separate state. Protocol support must not silently turn headphones/TVs/phones into tracker hardware.
- Alert-relevant events and Follow-Me observations have durable history; ordinary latest-state evidence may still be derived from persisted device state.

See [DETECTION_MODEL.md](DETECTION_MODEL.md) for evidence/confidence semantics.

## Device identity and protocol capability boundary

- `Device.deviceType` describes physical form factor when known.
- `ProtocolCapability` records independently observed protocols such as `FIND_HUB`, `DULT`, `SMARTTHINGS_FIND`, `EDDYSTONE` and `FAST_PAIR`.
- FEAA is ambiguous until its service-data frame is validated. FHN-160 and FHN-256 are distinct identity shapes; truncated/unknown-width Find Hub frames are not accepted as valid fingerprints.
- Samsung manufacturer `0x0075` and SmartThings Find capability do not by themselves identify SmartTag hardware.
- Active GATT model/service data may refine an unknown physical type, but generic GATT service-list equality never triggers automatic identity merge.
- Address carryover remains sequential/corroborated. Concurrent aliases and incompatible Find Hub frame widths are hard merge guards.
- Radar protocol-noise grouping must not hide a known physical device merely because it also emits a high-volume protocol.

## Active collection under `STABLE_CORE`

The active GATT implementation is retained under `STABLE_CORE`; passive observation remains the default, while automatic GATT collection is available only through an explicit user opt-in.

- `ScannerRuntimePolicy.profile` remains `STABLE_CORE`.
- Explicit opt-in automatic GATT collection is enabled again through `allowsAutomaticActiveProbe=true`; automatic RFCOMM probing and opportunistic Classic discovery remain disabled.
- `ActiveCollectionRepositoryImpl` exposes and persists the user's explicit automatic-GATT preference again. Passive scanning remains the default until the user turns the master switch on.
- `AutoActiveProbeCoordinator` keeps the existing bounded sequential queue: only connectable candidates are admitted, one device is probed at a time, duplicate/recent candidates are suppressed, probes time out after 12 seconds, and a successfully/failed recently probed device is cooled down for 15 minutes. Cooldown is per device/fingerprint, so a newly seen eligible device can still be queued immediately.
- The primary Settings/Radar surfaces may enable automatic active collection only through the explicit confirmation flow; disabling the switch clears the queue and disconnects the active automatic probe.
- The explicit per-device Details action is a separate path and remains implemented: `DetailsViewModel.connect()` calls `DeviceConnectionController.connect()`; `BleConnectionManager` connects, discovers GATT services, reads characteristics that advertise the READ property one at a time, and persists the resulting active evidence. This manual path does not write characteristics.
- Periodic RFCOMM probing remains disabled independently.

Do not describe the current profile as "GATT removed" or "active probing not implemented." The precise statement is: **passive observation is the default, explicit automatic read-oriented GATT collection is available through the master switch, and manual per-device GATT inspection remains available; RFCOMM and opportunistic Classic discovery stay disabled.**

The current field-test baseline keeps automatic GATT at concurrency 1. Physical S22+ validation on the 2026-10-07 runtime baseline persisted 22 recent probe outcomes, including 14 devices with discovered services and 19 with characteristic data; the Room snapshot passed `PRAGMA quick_check`, passive scanning continued, and no crash/ANR was observed. Any future concurrency >1 experiment must use independent GATT session state rather than sharing the current singleton connection state.

## Field-regression policy

Detailed captures do not live in the architecture document. Confirmed field defects become privacy-safe tests and concise regression anchors in [DETECTION_MODEL.md](DETECTION_MODEL.md); historical capture detail remains available in Git/PR evidence.

## Location/data-quality boundary

Location is observation metadata for the phone, never an inferred exact Bluetooth-device position.

- A usable observation requires finite latitude/longitude within valid geographic ranges.
- Reported accuracy must be finite, greater than 0 m and at most 100 m.
- Details sightings reject unusable observations before clustering and represent a cluster with a real accepted observation rather than a synthetic device coordinate.
- Deterministic analysis counts usable, rejected and missing location samples separately and exposes location-quality summaries/flags without emitting exact coordinates.
- No route/polyline is inferred from phone observation points.

## Deterministic analysis boundary

A2/A3 are implemented as an Android-light local analysis layer:

- `core:model/.../analysis/AnalysisModels.kt` defines the stable reduced analysis candidate model.
- `core:domain/.../analysis/DeterministicAnalysisReducer.kt` reduces existing device, signal, Follow-Me, alert-evidence and identity-candidate inputs without DB, Android API or wall-clock access.
- The reducer canonicalizes order, removes duplicate/noise samples, uses fixed time buckets, summarizes RSSI/location quality, segments movement, bounds representative evidence and surfaces quality flags/contradictions.
- `core:model/.../analysis/AnalysisBundleModels.kt` defines versioned `AnalysisBundleV1` JSON.
- `core:domain/.../analysis/AnalysisBundleBuilder.kt` maps reduced candidates into a deterministic privacy-bounded bundle with session-scoped aliases.
- The bundle excludes exact coordinates, raw hardware identifiers/fingerprints, raw payloads and free-form evidence text. Active GATT/RFCOMM probe evidence is excluded by default and counted as omitted.
- The bundle is not wired into `DatabaseExporter`, Drive/Gmail or a production telemetry outbox. T0 remains a debug bridge and T1+ remains intentionally blocked.

This layer consumes already accepted runtime inputs; it does not reopen scanner/ingest ownership or semantics.

## Current product direction

```text
raw observations
  -> deterministic parse/normalize
  -> local persistence
  -> local reduction / identity / encounters / movement / RSSI summaries
  -> explainable local verdict
  -> optional bounded analysis bundle/telemetry
  -> optional external analyst
```

The optional telemetry bridge is a side channel, not a new source of truth. Failure of Google/network/AI must not affect local scanning, persistence or alerts.

## Known architectural debt

- `core:data` is still broad and owns several unrelated runtime concerns.
- `ScannerService` remains a large Android lifecycle/notification/wakelock/Bluetooth orchestration surface even though runtime ownership is now controlled.
- `DeviceEvidenceFactory`, `DeviceCorrelationStrategy`, `DatabaseExporter`, `SettingsViewModel` and some Compose screens remain large responsibilities; split them when the active workstream provides a tested boundary rather than as drive-by refactors.
- Some general classifier evidence is still derived from latest persisted state instead of an append-only evidence event stream.
- Existing detekt baselines and ktlint exclusions represent acknowledged debt; do not expand them casually.
- Identity carryover/Follow-Me heuristics should be tuned from reviewed field evidence and regression fixtures, not intuition.

## Change rules

1. Do not change accepted scanner/ingest semantics incidentally during UI work.
2. Keep active probing explicit and provenance-visible.
3. Prefer privacy-safe regression fixtures before heuristic/parser changes.
4. Move repeated mechanical reasoning into deterministic local code over time; keep AI optional and higher-level.
