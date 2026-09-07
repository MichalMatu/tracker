# Phase 2 Closure Re-Audit

Status: **PASS WITH RECORDED TECHNICAL DEBT**
Date: **2026-09-07**
Repository: `MichalMatu/tracker`
Runtime implementation commit: `84b31d08bac81b6f46e44d11ccfe06071438b66a`
Closure-audit source baseline: `53c56fa54e92a713e555979f2d1778dc44dfaf98`

## Decision

Phase 2 is complete. The scanner/service lifecycle has one owner, deterministic Start/Stop/restart behavior, green source/build gates and physical Android acceptance. The re-audit found important architectural debt, but no finding requires reopening Phase 2 or blocks starting Phase 3.

This is **not** a claim that the entire application is fully recovered. Overall runtime stability remains gated by later recovery phases and the Phase 7 physical stability campaign.

## Evidence reviewed

### Software and build

- Phase 2 implementation: `84b31d08bac81b6f46e44d11ccfe06071438b66a`.
- Focused lifecycle regression tests: PASS.
- Full `:core:data:testDebugUnitTest`: PASS.
- `:feature:settings:testDebugUnitTest`: PASS.
- Sandbox `qualityCheck`: PASS.
- Sandbox `:app:assembleDebug`: PASS.
- Canonical GitHub Quality for the implementation SHA: PASS; Secret Scan and Sandbox Pack also passed.
- Final closure gate on exact `main=53c56fa54e92a713e555979f2d1778dc44dfaf98` using Eclipse Temurin JDK 21.0.2: `qualityCheck` PASS, `:app:assembleDebug` PASS, `git diff --check` PASS and final worktree clean.

One non-fatal compiler warning remains in `FilterDialog.kt`: `availableVendors` is unused. It is cleanup debt, not a lifecycle defect.

### Physical Android acceptance

Device: Samsung `SM-S906B`, Android 16 / SDK 36. Installed rolling tester APK SHA-256 during the clean R3 run: `ddd089fff800cbb0ab0d02f57e325f61e948ac4c30a89b8e302ef37a40ad12ee`.

Clean R3 verified:

- launching the Activity alone leaves scanner service Idle,
- explicit Start creates exactly one scanner service lifecycle,
- Bluetooth OFF records teardown and removes the service,
- Bluetooth ON alone does not auto-restart scanning,
- explicit restart after Bluetooth returns creates one service,
- background -> foreground preserves one process/service and does not add lifecycle starts,
- force-stop removes process/service; relaunch remains Idle until explicit Start,
- screen sleep -> wake preserves the same process/service and does not add lifecycle starts,
- `APP_FATAL_COUNT=0`,
- `APP_ANR_COUNT=0`,
- final marker `CLEAN_DEVICE_FLOW_R3=PASS`.

Database integrity remained `ok` before and after the run. Counts grew rather than regressed: `devices` 24 -> 25, `follow_me_observations` 177 -> 180 and `signal_samples` 3323 -> 3601. An earlier physical Start/Stop cycling run also passed 20 cycles without app crash or SecurityException.

## Architecture and ownership audit

### Module direction

The Gradle module graph remains appropriately one-way for feature code:

- `core:data` -> `core:model`, `core:domain`, `core:decoders`,
- `core:domain` -> `core:model`,
- `core:decoders` -> `core:model`,
- `feature:radar`, `feature:details`, `feature:settings`, `feature:watchlist` -> `core:model`, `core:domain`, `core:ui`,
- feature modules do not declare a dependency on `core:data`.

`app` remains the composition root and depends on the implementation and feature modules.

### Scanner lifecycle ownership

The closure static audit found all direct `BleScanner` lifecycle calls in `ScannerService` only: passive start, focused start, passive resume and stop/teardown. `AndroidScannerRuntimeController` delegates Start/Stop through `ScannerServiceController`; feature modules use domain-facing contracts. `BleScanSource` guards one callback with synchronized start/stop and clears the callback on teardown. This satisfies the Phase 2 one-owner requirement.

## God-object / responsibility audit

| File | LOC | Functions | Assessment |
| --- | ---: | ---: | --- |
| `DeviceEvidenceFactory.kt` | 733 | 27 | High decomposition value; split by evidence category when this area is next changed. |
| `SettingsScreen.kt` | 611 | 9 | Large Compose surface; split visual sections incrementally. |
| `DeviceCorrelationStrategy.kt` | 581 | 21 | Complex identity heuristics; intentionally defer to Phase 5. |
| `DatabaseExporter.kt` | 572 | 22 | Broad export responsibilities; refactor separately from scanner recovery. |
| `SettingsViewModel.kt` | 543 | 29 | God-object risk; do not add Phase 3 business logic here. |
| `RadarScreen.kt` | 496 | 10 | Large presentation surface; not a Phase 3 blocker. |
| `WatchlistScreen.kt` | 482 | 10 | Large presentation surface; later UI decomposition. |
| `DetailsScreen.kt` | 477 | 9 | Large presentation surface; later UI decomposition. |
| `TacticalProcessor.kt` | 456 | 18 | Advanced path; automatic side effects are suppressed in Stable Core. |
| `AddressCarryoverTracker.kt` | 456 | 16 | Identity/carryover complexity; Phase 5 concern. |
| `ScannerService.kt` | 423 | 25 | Complexity threshold, but cohesive around validated scanner runtime ownership; keep stable through Phase 3 unless instrumentation proves a need to split it. |

These sizes are debt indicators, not automatic proof that a class must be rewritten immediately. Stability evidence has priority over cosmetic churn.

## Concurrency and legacy-path debt

The static scan found two `GlobalScope` references in `RfcommConnectionManager.kt`, one `Thread.sleep` in `BoseRfcommHandler.kt`, no `runBlocking`, no `observeForever`, and no source TODO/FIXME/HACK markers in scanned main Kotlin. RFCOMM/active paths are not part of automatic Stable Core operation; fix these before controlled reintroduction rather than expanding Phase 3 scope.

## Tooling debt

Detekt baselines still exist in several modules. Root ktlint intentionally excludes `core:data/src` and `core:decoders/src` because of inherited formatting debt. Baseline reduction should be explicit refactor work rather than a side effect of Phase 3.

## Phase 3 guardrails derived from this audit

1. Do not refactor `DeviceCorrelationStrategy`, carryover heuristics, alerts or RFCOMM while instrumenting ingest.
2. Keep `ScannerService` lifecycle behavior frozen unless a Phase 3 test proves a lifecycle dependency.
3. Do not add more business logic to `SettingsViewModel`; diagnostics presentation additions should use small mappers/components.
4. Instrument first, change queue/coalescing behavior second. Establish before/after counters so no optimization can hide loss.
5. Preserve exact-SHA sandbox/GitHub/device evidence separation.

Phase 3 continuation instructions live in `PHASE3_HANDOFF.md`.
