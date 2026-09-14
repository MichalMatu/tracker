# Current architecture

BlueEye is a local-first Android/Kotlin application. `main` is the source of truth.

## Modules

| Module | Responsibility |
| --- | --- |
| `app` | Android entry point, Hilt wiring, Navigation Compose |
| `core:model` | Shared domain/evidence models |
| `core:domain` | Repository/runtime contracts and use cases |
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
