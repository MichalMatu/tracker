# Stable Core Preimplementation Audit

Date: 2026-09-06
Status: PHASE 1 IMPLEMENTED AND SOFTWARE-VERIFIED
Canonical plan: `docs/STABILITY_RECOVERY_GUIDE.md`

## Purpose

This audit narrows the repository to the first implementation milestone of the stability recovery plan. It is intentionally not a general architecture audit and it does not authorize unrelated cleanup.

The immediate goal is to make the normal runtime path a predictable BLE-only passive collector before lifecycle, queue, alert, identity, and UI-history work is tackled in later phases.

## Repository organization

During stabilization the repository uses the smallest possible Git structure:

- `main` — the only source-code development line; work directly here unless the maintainer explicitly asks for a separate branch.
- `agent-control` — Local Agent control/status/tasks only; never use it for application source.
- temporary implementation branches — do not create by default.
- old development snapshots — preserve as tags rather than keeping inactive branches alive.

Current recovery tags:

- `stability-baseline-2026-09-06` — commit `61f9f29a028fc31f6e1640daf4cfa12fdc1badeb`, the verified code baseline before recovery work.
- `archive-field-mvp-2026-07-07` — archived Field MVP branch snapshot.
- `stability-plan-v1` — commit containing the accepted recovery guide on `main`.

`main` is intentionally unprotected during the stabilization cycle so the repository's documented direct-main workflow and Local Agent can operate without artificial PR friction. GitHub Actions still run on pushes and the local quality gate remains mandatory before a stabilization milestone is considered complete.

## Baseline verification

The exact `main` used for this audit passed:

- targeted Stable Core source audit,
- `:core:data:testDebugUnitTest`,
- `qualityCheck`,
- `:app:assembleDebug`,
- `git diff --check`.

A green build does not prove runtime stability. It only establishes a clean implementation starting point.

## Phase 1 confirmed findings

### 1. No Stable Core runtime/profile contract exists

No `StableCore`, `RuntimeProfile`, `CollectionProfile`, or `BLE_ONLY` runtime contract currently gates scanner behavior.

Consequence: advanced paths are controlled independently and can drift back into the normal runtime.

Required Phase 1 action:

- introduce one explicit runtime profile/policy whose active stabilization mode is BLE-only passive collection,
- expose the active profile in scanner diagnostics,
- make advanced-path checks depend on that profile rather than scattered UI preferences.

### 2. Starting passive BLE also starts Classic discovery

`BleScanner.performPassiveBleScan()` starts BLE and then unconditionally calls `startClassicDiscovery()`.

Relevant files:

- `core/data/src/main/java/io/blueeye/core/scanner/manager/BleScanner.kt`
- `core/data/src/main/java/io/blueeye/core/scanner/source/ClassicScanSource.kt`

Consequence: the current normal scanner is not BLE-only. Classic radio work, SDP activity, Classic persistence and BLE/Classic shared-queue pressure remain present in every normal run.

Required Phase 1 action:

- gate Classic startup through the Stable Core profile,
- Stable Core must never call `ClassicScanSource.start()`,
- focused/manual behavior must not accidentally restart Classic,
- add a regression test proving Classic is not started in Stable Core.

Do not redesign Classic deduplication in Phase 1. It remains disabled and is revisited later.

### 3. Auto active GATT being default-false is not a sufficient safety boundary

`WatchlistPreferences.autoActiveProbeEnabled` defaults to `false`, but it is persisted in DataStore. A device on which the user previously enabled automatic probing can therefore retain `true` across a new build.

`BleScanHandler` still calls `AutoActiveProbeCoordinator.enqueueCandidate()` for eligible persisted scan results; the coordinator decides whether to act based on its remembered preference state.

Relevant files:

- `core/data/src/main/java/io/blueeye/core/data/preferences/WatchlistPreferences.kt`
- `core/data/src/main/java/io/blueeye/core/connectivity/manager/AutoActiveProbeCoordinator.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/handler/ble/BleScanHandler.kt`

Required Phase 1 action:

- Stable Core must hard-block automatic active probing regardless of a previously persisted preference,
- a stored `autoActiveProbeEnabled=true` must not produce a GATT connection while Stable Core is active,
- UI may show the old preference, but it must be clearly unavailable/ineffective in Stable Core or be normalized off,
- add a regression test with the stored preference deliberately set to `true`.

Prefer one central runtime-policy gate over resetting a preference as the only defense. A persisted setting must never bypass the selected runtime profile.

### 4. Phase 1 must isolate collection, not repair every known P0 at once

The following defects are confirmed but belong to later phases and should not be mixed into the first Stable Core commit unless required to make BLE-only gating compile or test:

- `ScannerService` returns `START_NOT_STICKY`.
- scanner startup calls `bleScanHandler.resetSession()` and therefore couples technical restart to logical Follow-Me session reset.
- `ScannerService.onDestroy()` does not explicitly call `bleScanner.stopScanning()`.
- `BleScanner` uses a 4096-event `DROP_OLDEST` channel.
- `recordDroppedQueueEvents()` exists in diagnostics but currently has no producer wiring from `BleScanner`.
- alert sound is started with `Ringtone.play()` without retained ownership or a stop/cancel contract.
- Radar uses a rolling 180-second visibility window and can look empty even when Room still contains history.

These findings remain blockers for the later stability gate, but separating them keeps Phase 1 small and reviewable.

### 5. Automatic user-facing alert side effects should not contaminate Stable Core collection tests

The goal of the first runtime profile is to prove collection. Evidence/classification may continue to be computed where cheap, but automatic sound/vibration delivery should not be allowed to make Phase 1 hardware testing unsafe or ambiguous.

Phase 1 should therefore define an explicit policy for automatic alert side effects while Stable Core is active. Preferred stabilization behavior:

- preserve evidence/history where practical,
- suppress automatic public-safety/tactical side effects,
- do not add new alert behavior,
- keep manual diagnostic/test alert work separate until the alert-control phase.

Do not attempt the full alert-controller rewrite in Phase 1.

## Existing test gap

The current test inventory contains tests touching auto-probe/watchlist behavior and Radar UI state, but there is no dedicated regression suite proving the desired Stable Core contract:

- Classic is never started,
- automatic GATT cannot start even with a persisted `true` preference,
- BLE passive collection still starts normally,
- diagnostics report the active runtime profile.

Phase 1 is not complete without these tests.

## Exact Phase 1 implementation order

1. Define the runtime profile/policy in the domain-facing scanner contract or another small central boundary.
2. Select Stable Core as the stabilization default.
3. Gate `BleScanner` Classic startup with the profile.
4. Gate automatic active-probe enqueue/execution with the profile, independently of persisted user preference.
5. Define/suppress automatic nonessential alert side effects for Stable Core without rewriting the alert subsystem.
6. Expose the active profile in scanner diagnostics/export-facing diagnostics if the existing model can carry it without broad redesign.
7. Add focused tests for BLE start, Classic suppression, persisted auto-probe suppression, and profile diagnostics.
8. Run touched-module tests.
9. Run `qualityCheck`.
10. Run `:app:assembleDebug`.
11. Run `git diff --check`.
12. Update `docs/STABILITY_RECOVERY_GUIDE.md` and tag the completed Phase 1 milestone only after the acceptance criteria pass.

## Phase 1 acceptance criteria

All conditions below must be true:

- normal Start initiates passive BLE collection,
- normal Start does not initiate Classic discovery,
- normal Start does not initiate automatic GATT or RFCOMM,
- a previously persisted auto-probe `true` cannot bypass Stable Core,
- no new decoder/classifier behavior is added,
- no unrelated lifecycle/identity cleanup is bundled into the change,
- the active profile is observable,
- focused regression tests pass,
- full repository quality gate passes,
- debug APK builds.

## Explicit non-goals for the first implementation

Do not in the same Phase 1 change:

- change Follow-Me thresholds,
- redesign MAC carryover,
- remove the Classic implementation from source,
- refactor the entire scanner lifecycle,
- replace the scan queue,
- rewrite alert ownership,
- redesign Radar/history,
- add new background/watchlist scan strategies,
- add new decoders or detection rules.

Those belong to subsequent recovery phases already defined in the canonical guide.

## Documentation authority during stabilization

Use documents in this order when instructions conflict:

1. `docs/STABILITY_RECOVERY_GUIDE.md` — canonical execution state and checklist.
2. `docs/STABLE_CORE_PREIMPLEMENTATION_AUDIT.md` — exact current Phase 1 implementation boundary.
3. `docs/QUALITY_GATE.md` — build/test requirements.
4. `docs/PRODUCT_GOAL.md` — product intent and claims boundary.
5. `AGENTS.md` — coding and Local Agent operating rules.

`MVP_GOAL_PROMPT.md`, `docs/PIPELINE_AUDIT.md`, and `FIELD_SESSION_CHECKLIST.md` contain useful historical context but their old execution order must not override the recovery guide while feature freeze is active.

## Completion evidence

Phase 1 implementation commit: `59e594952a9bd436e5af85f7d4ea741cd0bf5726`.

The acceptance criteria were software-verified with focused regression tests, the full repository `qualityCheck`, debug APK assembly, and `git diff --check`. The implementation keeps later recovery work out of scope: lifecycle remains Phase 2, ingest/queue pressure remains Phase 3, alert ownership remains Phase 4, identity remains Phase 5, and Radar/history semantics remain Phase 6. Physical-device stability acceptance remains Phase 7.

## Next action

Proceed to Phase 2 exactly as defined in `docs/STABILITY_RECOVERY_GUIDE.md`: establish deterministic scanner/service lifecycle ownership and Start/Stop/restart behavior.
