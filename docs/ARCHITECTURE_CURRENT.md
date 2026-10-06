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
  -> classification + enrichment + identity / Follow-Me analysis
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
- Alert-relevant events and Follow-Me observations have durable history; ordinary latest-state evidence may still be derived from persisted device state.

See [DETECTION_MODEL.md](DETECTION_MODEL.md) for evidence/confidence semantics.

## Active collection under `STABLE_CORE`

The active GATT implementation is retained; the stabilization profile suppresses only **automatic** collection.

- `ScannerRuntimePolicy.profile` remains `STABLE_CORE`.
- Explicit opt-in automatic GATT collection is enabled again through `allowsAutomaticActiveProbe=true`; automatic RFCOMM probing and opportunistic Classic discovery remain disabled.
- `ActiveCollectionRepositoryImpl` exposes and persists the user's explicit automatic-GATT preference again. Passive scanning remains the default until the user turns the master switch on.
- `AutoActiveProbeCoordinator` keeps the existing bounded sequential queue: only connectable candidates are admitted, one device is probed at a time, duplicate/recent candidates are suppressed, probes time out after 12 seconds, and a successfully/failed recently probed device is cooled down for 15 minutes.
- The primary Settings/Radar surfaces may enable automatic active collection only through the explicit confirmation flow; disabling the switch clears the queue and disconnects the active automatic probe.
- The explicit per-device Details action is a separate path and remains implemented: `DetailsViewModel.connect()` calls `DeviceConnectionController.connect()`; `BleConnectionManager` connects, discovers GATT services, reads characteristics that advertise the READ property one at a time, and persists the resulting active evidence. This manual path does not write characteristics.
- Periodic RFCOMM probing remains disabled independently.

Do not describe the current profile as "GATT removed" or "active probing not implemented." The precise statement is: **automatic active collection is disabled during core stabilization; explicit per-device read-oriented GATT inspection still exists.**

## Privacy-safe field reference: Lime legacy BLE family

A September 2026 field capture provides a useful regression/reference case without committing private location, exact MAC, raw capture files or full vehicle identifiers:

- 11 devices used names matching `lime-931303XXXXXX`.
- 232 persisted BLE samples shared one advertisement schema.
- Every Android `ScanRecord` was 59 bytes and is best interpreted as 31 bytes of legacy advertising data plus 28 bytes of scan-response data.
- The legacy advertising portion is exactly 31 bytes: Flags (`0x01`, value `0x06`) followed by a 26-byte AD structure using unassigned/reserved type `0x00`.
- The scan-response portion contains Complete Local Name (`0x09`, 17 bytes), Peripheral Connection Interval Range (`0x12`, 4 bytes) and Tx Power (`0x0A`, value 0 dBm).
- For each individual device the 59-byte record stayed byte-for-byte stable across the capture. Across all 11 devices only byte positions 44-49 varied, corresponding to the final six digits of the local name. No dynamic battery/lock/status field was visible in passive advertising.
- No advertised service UUIDs, Service Data or Manufacturer Specific Data were present in these records.
- All observed devices were connectable on LE 1M PHY. The captured device rows had `connectionAttempts = 0` and no persisted GATT services/characteristics, so the session is passive-only evidence; it does **not** imply that GATT is absent.
- Five observed address prefixes map publicly to Texas Instruments. Together with the `lime-931303XXXXXX` name pattern and public teardown/reference material, the working hardware identification is the older Lime **LBCAT-S family**, likely European LBCAT-S/LBCATSL revisions using TI CC2540-class BLE. Treat this as high-confidence family identification, not proof of the exact CCU revision or scooter chassis.
- Different scooter chassis may share this CCU/BLE family. Do not infer a vehicle generation solely from this radio fingerprint.

This case is useful for parser/regression work because it separates three claims that must remain distinct: passive advertisement structure, probable CCU family, and active GATT evidence. Future authorized field validation can compare the same passive fingerprint with explicit per-device GATT discovery without enabling automatic fleet-wide probing.

A1 parser/data hardening is closed around this boundary: privacy-safe Lime-shaped scan fixtures, service-data truncation tests and advertisement-evidence boundary tests prevent malformed/reserved records from fabricating structured evidence. Existing carryover regressions preserve the coexistence-vs-rotation and identity-conflict safeguards.

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
