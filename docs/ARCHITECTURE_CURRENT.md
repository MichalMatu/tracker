# Current Architecture

> **Current-status note (2026-09-12):** Phase 3 field reacceptance is **ACCEPTED / CLOSED** and Phase 4 is **UNBLOCKED**. The final Phase 3 authority is `PHASE3_FIELD_REACCEPTANCE_CLOSURE_2026-09-11.md`. Current execution order comes from `README.md` and `UI_UX_REDESIGN_PLAN.md`; the older stability-recovery guide remains historical provenance. The accepted scanner/ingest behavior must not be changed incidentally during the UI/UX workstream.

Status after tooling review: the project is modern Android in framework choices, but not yet clean in dependency direction.

Pipeline-specific issues are tracked in [PIPELINE_AUDIT.md](PIPELINE_AUDIT.md).

## Current Modules

| Module | Current role |
| --- | --- |
| `app` | Android entry point, Hilt wiring, Navigation Compose |
| `core:model` | Shared models |
| `core:domain` | Repository contracts and use cases |
| `core:data` | Room, Bluetooth scanning, foreground service, classification, session/tracking logic |
| `core:decoders` | BLE decoder library |
| `core:ui` | Theme, dimensions, shared UI utilities |
| `feature:radar` | Radar UI and ViewModel |
| `feature:details` | Device details UI and ViewModel |
| `feature:settings` | Settings UI and ViewModel |
| `feature:watchlist` | Watchlist UI and ViewModel |

## Positive Signals

- Kotlin-first Android project.
- Compose screens, no Fragment UI workflow.
- Hilt is already used.
- `core:domain` is physically separate now and has no Android imports in the checked source.
- `feature:radar` no longer depends on `core:data`; scanner control goes through `ScannerRuntimeController`.
- `feature:watchlist` no longer depends on `core:data`; public-safety-style signals go through `PublicSafetySignalMonitor`.
- `feature:details` and `feature:settings` now depend on domain-facing repository contracts instead of `core:data`.
- Phase 2 established one scanner lifecycle owner: direct `BleScanner` start/focused/passive/stop calls are confined to `ScannerService`, while feature modules use domain-facing scanner contracts.
- Phase 3 separates `BleScanner` hardware lifecycle from `ScanIngestPipeline` queue/processing work; bounded latest-per-key buffering is independently testable.
- Ingest metric semantics are isolated in `ScannerIngestDiagnosticsReducer`; `ScannerRuntimeDiagnosticsStore` is the synchronized publication facade rather than the counter God object.
- Canonical device/tracking persistence remains in `DevicePersister`, while time-series RSSI/sample writes are isolated in `SignalSamplePersister`.
- Evidence is a domain contract carried into Radar, Details, watchlist, export, and alert-history UI, including passive name/model/appearance/Class-of-Device/service/manufacturer context.
- Navigation Compose uses serializable route types.
- Basic Gradle quality gate exists.

## Main Problems

### P0: Active Probing Must Stay Explicit

Passive BLE/Classics observation is the default. Active GATT collection is behind the domain-facing `ActiveCollectionRepository`, has explicit Radar/Settings UI, and requires confirmation before enabling automatic collection. The remaining risk is periodic RFCOMM or broader active probing: do not re-enable it without a separate opt-in boundary and visible active-evidence labeling.

### P2: Scanner Service Boundary Is Controlled but Still Structurally Leaky

Phase 2 resolved runtime ownership: direct `BleScanner` lifecycle calls are confined to `ScannerService`, and feature modules control scanning through domain-facing contracts. The remaining debt is structural: `ScannerService` still lives in `core:data` under package `io.blueeye.service` and combines service lifecycle, notification, wake-lock, Bluetooth receiver and cleanup orchestration. At 423 LOC / 25 functions it is a refactor candidate, but the behavior is already field-accepted and this debt is not part of the current Radar/Details redesign unless a measured defect requires touching it.

### P1: `core:data` Is Too Broad

`core:data` still owns scanning, repositories, database, classification, tracking sessions, alerts, and foreground service control. This remains too broad for long-term change safety. The Phase 3 critical ingest path is decomposed internally, which reduces immediate runtime coupling without pretending the entire module boundary is solved.

### P1/P2: Oversized Responsibilities Are Recorded Debt

The closure/hardening audits identify several large historical files, led by `DeviceEvidenceFactory`, `DeviceCorrelationStrategy`, `DatabaseExporter`, `SettingsViewModel`, `ScannerService` and multiple large Compose screens. `RfcommConnectionManager` also retains `GlobalScope`, and a Bose RFCOMM handler contains `Thread.sleep`. These are explicit debt, not hidden findings. The Phase 3 runtime hotspots were decomposed where doing so was behavior-preserving; unrelated areas remain deferred. See `PHASE2_CLOSURE_AUDIT.md` and `PHASE3_CODE_QUALITY_REVIEW.md`.

### P2: Tooling Debt Is Baseline-Gated

Detekt baselines exist for current debt. Ktlint excludes `core:data/src` and `core:decoders/src` for now. This is acceptable as a temporary quality gate, but not as the final standard.

## Recommended Next Refactor Order

The list below is architectural follow-up. It must be reconciled with the active execution plan in `UI_UX_REDESIGN_PLAN.md`; do not let broad cleanup preempt measured UX/performance work or the later parser/reducer milestones.

1. Keep active probing explicit and add a separate RFCOMM opt-in boundary only if product evidence shows it is worth the cost.
2. Use real session review outcomes for Follow-Me and identity/carryover tuning before raising confidence thresholds.
3. Continue converting general classifier outputs into explicit evidence where the source can be preserved.
4. Split oversized `core:data` areas when the active product/data-flow work provides a clear boundary and regression coverage.
5. Pay down detekt/ktlint baselines module by module.
