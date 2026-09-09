# Phase 3 Code Quality Review

Status: **STRUCTURAL HARDENING COMPLETE IN SOURCE; FIELD VALIDATION STILL REQUIRED**
Review baseline: `52e386fef1010553648ced4e690267179fce0830`
Scope: BLE Stable Core / Phase 3 ingest path only. This review does not authorize Phase 4 alerts, Phase 5 identity tuning, or unrelated UI cleanup.

## Goal

Phase 3 already made BLE ingest bounded and observable. This pass tightens responsibility boundaries before field validation without changing the accepted Phase 2 lifecycle contract or the Phase 3 accounting schema.

The target is not a repository-wide aesthetic rewrite. The target is a small critical runtime path where each class has one explainable reason to change, loss accounting remains explicit, and future field evidence can be interpreted without reading a God object.

## Architecture audit

Automated checks against the exact baseline source found:

- no Android SDK imports in `core:domain`,
- no feature-module dependency on `io.blueeye.core.data.*`,
- no feature Kotlin use of `Color(0xFF...)` or `R.color.*`,
- no XML layout files,
- no Fragment / FragmentManager UI path,
- no AsyncTask, LiveData or RxJava use in the scanned Kotlin source.

These checks confirm that the high-level Clean Architecture direction is healthy. They do not imply that every historical class is small or fully decomposed.

## Critical-path responsibility map

After this hardening pass, the Phase 3 path is intentionally split as follows:

| Component | Single responsibility |
| --- | --- |
| `BleScanner` | Android BLE/Classic hardware scan lifecycle and scan-mode transitions only |
| `ScanIngestPipeline` | callback timestamp capture, bounded enqueue/coalescing, sequential processing and processing-result accounting |
| `LatestPerKeyScanBuffer` | bounded latest-per-key pending buffer semantics |
| `ScannerRuntimeDiagnosticsStore` | scanner/lifecycle state publication plus compatibility rates |
| `ScannerIngestDiagnosticsReducer` | deterministic ingest counter/rate/latency state transitions |
| `BleScanHandler` | orchestration of BLE business processing; Phase 4/5 concerns remain intentionally untouched |
| `DevicePersister` | canonical device/tracking persistence |
| `SignalSamplePersister` | throttled time-series signal-sample persistence |

The split keeps Bluetooth callbacks lightweight: observation timestamps are captured before queue delay, persistence remains off the callback path, and queue accounting stays independent from Room persistence accounting.

## God-object review and decisions

### Refactored now

- `ScannerRuntimeDiagnosticsStore`: previously combined lifecycle state, seven rolling queues, all ingest counter mutations and publication behind one global object and required `@Suppress("TooManyFunctions")`. Detailed ingest state transitions now live in a deterministic reducer; the store is a small synchronized facade.
- `BleScanner`: previously combined Android scan lifecycle with queue ownership, callback accounting, event processing and repository dispatch. Queue/processor work now lives in `ScanIngestPipeline`; scanner lifecycle remains in `BleScanner` to protect the accepted Phase 2 contract.
- `DevicePersister`: previously owned canonical device writes, tracking writes, carryover rescue/merge and signal-sample construction/insertion. Signal sample persistence is now a separate `SignalSamplePersister`, guaranteeing one explicit sample outcome per non-provisional processed scan.

### Explicitly deferred

The audit also confirms broader pre-existing hotspots such as `DeviceEvidenceFactory`, `DeviceCorrelationStrategy`, `DatabaseExporter`, `SettingsViewModel`, `ScannerService`, large Compose screens, and baseline/tooling debt. They are real debt, but changing them now would cross Phase 3 boundaries or destabilize already accepted behavior.

In particular:

- `BleScanHandler` remains a large coordinator because splitting alert/evidence/identity responsibilities now would collide with Phases 4 and 5.
- `ScannerService` remains the authoritative scanner lifecycle owner from Phase 2. Structural relocation or decomposition is postponed until its runtime contract no longer needs stabilization protection.
- repository-wide Compose `@Preview` coverage is below the desired long-term standard. This is recorded as UI quality debt, not a Phase 3 field-validation blocker.
- declarative DAOs and Hilt binding modules may legitimately have many functions; a `TooManyFunctions` suppression there is not treated automatically as a God-object finding.

## Behavioral invariants preserved

This pass must preserve all of the following:

- `ScannerService` remains the only owner of global scanner lifecycle.
- Start/Stop remains idempotent; Stop tears down hardware scan resources.
- `START_NOT_STICKY`, Bluetooth OFF/ON policy, relaunch Idle policy and focused -> passive behavior are unchanged.
- ingest diagnostics are process-lifetime and are not reset by ordinary scanner Start.
- raw BLE observation time is captured at callback time, before queue delay.
- Stable Core remains BLE-only by default.
- `raw = queued + coalesced + rejected` remains the primary BLE reconciliation equation.
- accepted entries are never silently evicted; `queueDroppedTotal` must remain zero in the live latest-per-key design.
- processing success and failure are distinct outcomes.
- every non-provisional processed BLE observation yields exactly one signal-sample outcome: written, throttled or failed.
- no field-export schema is changed by this structural refactor.

## Prepublication verification

On the exact reviewed source with the hardening diff applied, the restored offline sandbox ran on JDK 21 / JVM 17 target and produced:

- `git diff --check` — PASS,
- `:core:data:detekt` — PASS (251 Kotlin files analyzed),
- `:core:data:testDebugUnitTest` — PASS (`BUILD SUCCESSFUL`, 49 s),
- full `qualityCheck` — PASS (`BUILD SUCCESSFUL`, 491 tasks, 1m38s),
- `:app:assembleDebug` — PASS (`BUILD SUCCESSFUL`, 212 tasks, 22 s).

Canonical GitHub exact-SHA gates remain the publication authority and are required again after the hardening commit is created.

## Golden gate for Phase 3

Before this hardening is considered the checkpoint source:

1. `git diff --check` passes.
2. focused `core:data` unit tests pass on JDK 21.
3. `core:data` Detekt passes without adding a broad suppression for the extracted runtime components.
4. full `qualityCheck` passes.
5. `:app:assembleDebug` passes.
6. the exact committed SHA passes canonical GitHub Quality and Secret Scan.
7. rolling Tester Release is built from the same exact SHA.
8. Android UI Smoke passes an emulator click-through for the pre-field production code and is explicitly rerun on the final documentation SHA before tagging.
9. immutable versioned tester release `v1.0.0-phase3-pre-field.1` reruns `qualityCheck` + `:app:assembleDebug` and publishes APK + checksum.
10. immutable checkpoint tag `checkpoint-phase3-pre-field-golden-2026-09-09` records that exact green SHA after the release succeeds.

These software gates do **not** close Phase 3. The field walk and reconciliation described in `PHASE3_FIELD_COLLECTION.md` remain mandatory before Phase 4.
