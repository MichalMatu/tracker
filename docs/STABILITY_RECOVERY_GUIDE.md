# Tracker Stability Recovery Guide

> Canonical working document for stabilizing `MichalMatu/tracker` before further feature development.
>
> Update this file after every completed stabilization task. Future chats should read this file first and continue from `NEXT ACTION` instead of rebuilding the plan from memory.

## 0. Project recovery status

- Baseline branch: `main`
- Baseline commit before stabilization work: `61f9f29a028fc31f6e1640daf4cfa12fdc1badeb`
- Baseline state verified on local agent:
  - [x] `:core:data:testDebugUnitTest`
  - [x] `qualityCheck`
  - [x] `:app:assembleDebug`
- Runtime stability: **NOT YET ACCEPTED**
- Feature freeze: **ACTIVE**

### NEXT ACTION

- [ ] Phase 1 — create the Stable Core runtime profile: BLE-only passive collection, with Classic discovery and automatic active probing disabled from the normal runtime path.

When the next task is completed, update this line to the next unfinished item and add an entry to the Work Log at the bottom of this file.

---

## 1. Recovery objective

The goal is not to keep patching individual symptoms. The goal is to obtain one small, observable, repeatable version of Tracker that can run for long periods without:

- losing observations silently,
- creating a flood of duplicate devices after sleep/wake,
- continuing hidden/background scans after Stop,
- generating repeated or uncontrollable alarms,
- confusing temporary UI absence with actual database loss,
- corrupting device identity through aggressive merge heuristics,
- making it impossible to tell where data was lost.

Only after this stable version passes the defined stability gate should existing advanced features be reintroduced one at a time.

---

## 2. Non-negotiable rules during stabilization

### Feature freeze

Until Phase 7 is complete:

- [ ] Do not add new decoders.
- [ ] Do not add new device classifiers.
- [ ] Do not tune Follow-Me scoring unless a test proves a specific scoring defect.
- [ ] Do not add new active Bluetooth collection modes.
- [ ] Do not expand tactical/public-safety matching rules.
- [ ] Do not redesign UI unrelated to observability or stability.
- [ ] Do not remove advanced code only because it is disabled; disconnect it first and delete only when evidence shows it is obsolete.

### Change discipline

Every stabilization change must:

1. solve one clearly named failure mode,
2. have a regression test where practical,
3. expose diagnostics when failure can occur only on real Android hardware,
4. pass the repository quality gate,
5. update this document.

Do not combine unrelated cleanup with a P0 runtime fix.

### No destructive identity guesses

During stabilization, uncertain device correlation must never silently destroy source history. Prefer observation records, alias candidates, reversible relationships and explicit evidence over irreversible merges based on weak heuristics.

---

## 3. Current confirmed risk map

### P0 — Scanner lifecycle / wake behavior

Relevant files:

- `core/data/src/main/java/io/blueeye/service/ScannerService.kt`
- `core/data/src/main/java/io/blueeye/service/ScannerServiceController.kt`
- `core/data/src/main/java/io/blueeye/core/scanner/manager/BleScanner.kt`
- `core/data/src/main/java/io/blueeye/core/data/scanner/AndroidScannerRuntimeController.kt`

Known risks:

- `ScannerService` currently returns `START_NOT_STICKY`.
- `BleScanner` is a singleton with its own coroutine scope and lifecycle separate from the service.
- `ScannerService.onDestroy()` does not currently guarantee `bleScanner.stopScanning()`.
- scan restart resets Follow-Me session memory through `bleScanHandler.resetSession()`.
- service state, scanner state and diagnostics state are separate sources and can drift.

Required outcome:

- there is one explicit runtime lifecycle,
- Start is idempotent,
- Stop fully stops BLE/Classic/probes/background jobs,
- service destruction cannot leave ghost scanning,
- expected Android process/service recovery behavior is explicit and tested.

### P0 — Silent queue loss

Relevant files:

- `core/data/src/main/java/io/blueeye/core/scanner/manager/BleScanner.kt`
- `core/data/src/main/java/io/blueeye/core/data/scanner/ScannerRuntimeDiagnosticsStore.kt`

Known risks:

- BLE and Classic share one sequential `Channel`.
- capacity is `4096`.
- overflow policy is `DROP_OLDEST`.
- processing can perform classification, Room operations, location work, scoring and alert work.
- a diagnostics field for dropped queue events exists, but the scanner drop counter is not fully connected to it.

Required outcome:

- no silent drop is possible,
- dropped/coalesced events are counted explicitly,
- raw input rate and persisted output rate are separately visible,
- repeated advertisements from one device do not force full heavy processing every time.

### P0 — Alert cannot be reliably stopped

Relevant files:

- `core/data/src/main/java/io/blueeye/core/alert/AndroidAlertDispatcher.kt`
- `core/data/src/main/java/io/blueeye/core/alert/TrackerAlertService.kt`
- `core/data/src/main/java/io/blueeye/core/alert/TacticalAlertService.kt`
- `core/data/src/main/java/io/blueeye/core/alert/TacticalVibrationHandler.kt`

Known risks:

- alert sound is started through `Ringtone.play()` without retaining ownership of the active ringtone.
- there is no single `stopSound()`, `cancelVibration()`, `acknowledge()` or `cancelAll()` contract.
- turning sound off changes policy for future alerts but does not necessarily stop a currently playing alert.
- cooldown keys can depend on current MAC and therefore fail across MAC rotation.
- several tactical evidence paths may request delivery for one physical observation.

Required outcome:

- one controller owns all alert side effects,
- one active alert can always be acknowledged/stopped,
- global alert OFF immediately stops current sound and vibration,
- one logical event creates at most one user-facing alert,
- cooldown uses a stable logical key where one exists.

### P0/P1 — Continuous Classic discovery increases instability

Relevant files:

- `core/data/src/main/java/io/blueeye/core/scanner/source/BleScanSource.kt`
- `core/data/src/main/java/io/blueeye/core/scanner/source/ClassicScanSource.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/handler/classic/ClassicDevicePersister.kt`

Known risks:

- BLE normal scan uses `SCAN_MODE_LOW_LATENCY` and `reportDelay(0)`.
- Classic discovery restarts after every `ACTION_DISCOVERY_FINISHED`.
- generic Classic devices can trigger SDP UUID fetches.
- Classic and BLE compete for radio and share downstream processing.
- Classic persistence contains aggressive name/UUID/RSSI merge paths.

Required outcome:

- Stable Core runs BLE-only by default,
- Classic becomes explicit enrichment, not permanent parallel discovery,
- its later reintroduction is rate-limited and independently observable.

### P1 — Duplicate identities after sleep/wake

Relevant files:

- `core/data/src/main/java/io/blueeye/core/data/tracker/AddressCarryoverTracker.kt`
- `core/data/src/main/java/io/blueeye/core/data/tracker/strategy/DeviceCorrelationStrategy.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/handler/ble/MacAddressResolver.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/handler/ble/DevicePersister.kt`

Known risks:

- correlation window is short relative to a real screen-off interruption.
- a rotating device may wake with a new MAC after the previous target aged outside the matching window.
- OUI-based assumptions may classify some addresses too confidently as public/stable.
- rescue/merge logic can merge on payload/name/RSSI evidence that is not always unique.

Required outcome:

- observed MAC is treated as an observation identifier, not guaranteed physical identity,
- weak correlation creates a candidate relationship rather than destructive merge,
- persistent logical identity requires strong multi-signal evidence,
- sleep/wake does not multiply one known test device into uncontrolled rows.

### P1 — UI can look like data loss

Relevant files:

- `feature/radar/src/main/java/io/blueeye/feature/radar/presentation/RadarViewModel.kt`
- `core/domain/src/main/java/io/blueeye/core/domain/usecase/GetScannedDevicesUseCase.kt`

Known behavior:

- Radar currently requests devices from a rolling `180` second window.
- the query window refreshes every 10 seconds.

Required outcome:

The UI must distinguish at least:

- scanner is running and receiving observations,
- scanner is running but has received nothing recently,
- scanner stopped/error,
- no devices in the current recent window,
- database contains historical devices but none were seen recently.

A blank Radar must never be interpreted as proof that Room data was deleted.

---

# PHASED RECOVERY PLAN

## Phase 0 — Freeze and baseline

Purpose: create a stable reference before runtime simplification.

- [x] Record baseline commit `61f9f29a028fc31f6e1640daf4cfa12fdc1badeb`.
- [x] Verify `:core:data:testDebugUnitTest`.
- [x] Verify `qualityCheck`.
- [x] Verify debug APK build.
- [x] Add recovery tags (`stability-baseline-2026-09-06`, `stability-plan-v1`) and archive old Field MVP state as a tag.
- [ ] Preserve a known APK/export from the pre-recovery baseline if useful for A/B comparison.
- [x] Establish sandbox-first execution infrastructure so routine analysis/build/test work does not depend on the Mac.
- [x] Standardize installed build runtime/toolchain on JDK 21 while keeping generated bytecode target at JVM 17.
- [x] Build a reusable offline Android SDK/Gradle cache transport and store bootstrap assets in ChatGPT Library.

Exit criterion: baseline is reproducible and recoverable.

---

## Phase 1 — Establish BLE Stable Core

Purpose: reduce the runtime to the minimum system that can prove data collection.

Target runtime path:

`BLE callback -> lightweight ingest -> identity observation -> Room -> Radar/diagnostics`

Disable from normal automatic operation:

- [ ] continuous Classic discovery,
- [ ] automatic active GATT probing,
- [ ] automatic RFCOMM probing,
- [ ] nonessential tactical/public-safety alert emission,
- [ ] any enrichment that can block or significantly delay raw ingest.

Keep code available behind explicit disabled flags or diagnostic actions where practical.

Implementation checklist:

- [ ] Define a single Stable Core runtime/profile contract.
- [ ] BLE-only is the default collection path.
- [ ] Verify disabling advanced paths does not delete existing data/features from source.
- [ ] Expose active runtime profile in diagnostics.
- [ ] Add tests proving Classic/probe are not started in Stable Core.
- [ ] Run quality gate.
- [ ] Build APK.

Exit criterion: app can continuously collect BLE-only observations without automatically starting Classic or active probes.

---

## Phase 2 — Fix lifecycle and screen-off recovery

Purpose: Start/Stop/restart behavior must be deterministic.

Checklist:

- [ ] Define one owner of scanner lifecycle.
- [ ] Make Start idempotent.
- [ ] Make Stop idempotent.
- [ ] `ScannerService.onDestroy()` guarantees scanner shutdown.
- [ ] No singleton scanner continues scanning after service teardown.
- [ ] Decide and document intentional service restart policy (`START_STICKY`, explicit user restart, or another justified design).
- [ ] Bluetooth OFF moves runtime to a clear state and stops underlying scan resources.
- [ ] Bluetooth ON does not create duplicate scan callbacks.
- [ ] Screen lock/unlock does not create a second scanner instance.
- [ ] Process recreation has explicit behavior.
- [ ] Follow-Me session reset is separated from low-level scanner technical restart.
- [ ] Diagnostics record service start/stop/destroy/recovery transitions.

Required regression scenarios:

- [ ] Start -> Start.
- [ ] Stop -> Stop.
- [ ] Start -> Stop -> Start x20.
- [ ] Bluetooth ON -> OFF -> ON.
- [ ] lock screen -> unlock.
- [ ] app background -> foreground.
- [ ] swipe/kill process -> relaunch.

Exit criterion: one scanner exists, Stop stops it, restart never silently resets unrelated logical session state.

---

## Phase 3 — Make ingest loss observable and bounded

Purpose: no observation may disappear without a counter explaining why.

Checklist:

- [ ] Wire queue drop counter into `ScannerRuntimeDiagnosticsStore`.
- [ ] Add raw BLE callbacks/minute.
- [ ] Add accepted/coalesced events/minute.
- [ ] Add persisted device updates/minute.
- [ ] Add signal samples written/minute.
- [ ] Add queue depth/high-water mark if feasible.
- [ ] Add processing latency metric.
- [ ] Replace `DROP_OLDEST` as an invisible correctness strategy.
- [ ] Introduce per-device coalescing/debounce where repeated advertisements carry no new useful state.
- [ ] Keep latest observation timestamps even when heavy enrichment is throttled.
- [ ] Keep persistence work off the Bluetooth callback path.
- [ ] Add synthetic burst test exceeding expected city-density traffic.

Design preference: do not process every advertisement as an independent expensive business event. Preserve raw counters and newest useful state, then perform heavier classification/persistence at a bounded rate.

Exit criterion: stress test can state exactly how many events were received, coalesced, processed, persisted or intentionally discarded.

---

## Phase 4 — Rebuild alert control as one owned subsystem

Purpose: every alert must be bounded and immediately stoppable.

Introduce one authoritative alert runtime owner, e.g. `AlertController`/`AlertRuntimeController`.

Required contract:

- [ ] `dispatch(request)`
- [ ] `acknowledge(alertId/key)`
- [ ] `stopSound()`
- [ ] `cancelVibration()`
- [ ] `cancelAll()`
- [ ] observable current alert state

Checklist:

- [ ] Retain and own active `Ringtone` reference or replace with a bounded audio mechanism.
- [ ] Stop previous sound before starting a replacement.
- [ ] Global alert OFF calls `cancelAll()` immediately.
- [ ] Sound OFF immediately stops active sound.
- [ ] Vibration OFF immediately cancels active vibration.
- [ ] Alert lifetime has a hard maximum.
- [ ] One logical detection produces at most one notification/sound/vibration event.
- [ ] Cooldown is based on logical device/event identity when available, not blindly current MAC.
- [ ] Repeated evidence sources are aggregated before delivery.
- [ ] Test alert uses exactly the same dispatcher/controller path as real alerts.

Regression tests:

- [ ] alert -> Sound OFF while playing.
- [ ] alert -> master Alerts OFF while playing.
- [ ] alert -> acknowledge.
- [ ] repeated same event x100.
- [ ] MAC changes for same logical identity during cooldown.
- [ ] simultaneous tactical evidence paths.

Exit criterion: no alert can continue beyond user cancellation or configured maximum duration.

---

## Phase 5 — Stabilize identity and deduplication

Purpose: prevent both duplicate explosion and incorrect destructive merges.

### Stage A — safe observation model

- [ ] Treat MAC as observed address, not guaranteed physical identity.
- [ ] Persist source observation/evidence before risky correlation.
- [ ] Separate `observedMac` from logical device identity everywhere important.
- [ ] Do not automatically merge on weak same-name evidence alone.
- [ ] Do not automatically merge on UUID overlap alone.
- [ ] Do not automatically merge only because RSSI is close.

### Stage B — conservative correlation

- [ ] Define evidence tiers: strong / medium / weak.
- [ ] Require multiple independent strong features for automatic carryover.
- [ ] Extend/rework sleep-gap handling based on evidence rather than only a 30s window.
- [ ] Re-evaluate OUI/public/random MAC assumptions.
- [ ] Persist correlation reason and confidence.
- [ ] Low-confidence match becomes candidate alias, not destructive merge.
- [ ] Make user correction/rejection possible without losing source evidence.

Required scenarios:

- [ ] one known phone/device with rotating MAC over long session.
- [ ] sleep gap longer than current carryover window.
- [ ] two same-model devices near each other.
- [ ] two Apple devices with similar RSSI.
- [ ] identical generic names.
- [ ] same service UUID on different physical devices.

Exit criterion: known-device test no longer creates uncontrolled duplicate rows, while two nearby similar devices remain distinct.

---

## Phase 6 — Separate recent visibility from stored history

Purpose: prevent false reports of data loss.

Checklist:

- [ ] Radar explicitly labels its recent-device window.
- [ ] Add scanner last BLE result timestamp to visible diagnostics.
- [ ] Add database total device count to diagnostics or relevant UI.
- [ ] Add recent-window count separately.
- [ ] Add clear scanner status: Starting / Running / Stalled / Error / Stopped.
- [ ] Define `Stalled` from elapsed time since last callback rather than assuming Running means healthy.
- [ ] Historical data view remains available even if recent Radar window is empty.
- [ ] Export includes scanner diagnostics and key ingest counters.

Exit criterion: after a screen-off collection gap, a tester can determine whether data was lost, hidden by recent-window filtering, or scanning actually stopped.

---

## Phase 7 — Stability gate on a real phone

No advanced feature may be re-enabled before this phase passes.

### Basic run

- [ ] clean install / controlled upgrade.
- [ ] permissions granted.
- [ ] Stable Core active.
- [ ] 30 minutes screen on.
- [ ] 30 minutes mostly screen off.
- [ ] no crash.
- [ ] no scanner stall without diagnostic evidence.

### Lifecycle torture

- [ ] 20x Start/Stop.
- [ ] 10x screen lock/unlock.
- [ ] 5x app background/foreground.
- [ ] Bluetooth OFF/ON cycles.
- [ ] process kill/relaunch.

### Data integrity

- [ ] database count never unexpectedly decreases.
- [ ] recent Radar window behavior matches documented semantics.
- [ ] known stationary device remains historically present.
- [ ] known rotating test device does not explode into uncontrolled duplicates.
- [ ] dropped/coalesced counters explain all intentional loss.

### Alert safety

- [ ] test alert can always be silenced.
- [ ] Alerts OFF stops active sound/vibration immediately.
- [ ] repeated event does not spam.
- [ ] no alert survives app/user cancellation unexpectedly.

### Load test

- [ ] synthetic or real dense BLE environment.
- [ ] queue/high-water metrics reviewed.
- [ ] no OOM.
- [ ] no unbounded coroutine growth.
- [ ] no silent event drop.

### Acceptance criteria

- [ ] 0 unexplained data-loss events.
- [ ] 0 ghost scanner instances.
- [ ] 0 unstoppable alarms.
- [ ] 0 uncontrolled duplicate-device explosions in the controlled test set.
- [ ] Stop always stops all collection work.
- [ ] diagnostics identify any temporary scanner gap.
- [ ] quality gate passes.
- [ ] APK builds.
- [ ] **STABLE CORE ACCEPTED**

---

## Phase 8 — Controlled feature reintroduction

Re-enable only one subsystem at a time. After each subsystem, rerun the relevant part of Phase 7.

Order:

1. [ ] Watchlist return detection.
2. [ ] Follow-Me scoring without aggressive identity merge changes.
3. [ ] Conservative MAC carryover.
4. [ ] Classic enrichment in bounded windows.
5. [ ] Manual active GATT probe.
6. [ ] Automatic active probe with strict limits.
7. [ ] Tactical/public-safety classification and alert aggregation.
8. [ ] Additional advanced decoders/enrichers.

For every item:

- [ ] enable only that subsystem,
- [ ] add/confirm tests,
- [ ] repeat lifecycle test,
- [ ] repeat alert test if relevant,
- [ ] repeat data integrity test,
- [ ] observe diagnostics,
- [ ] revert/disable if stability regresses,
- [ ] document result in Work Log.

---

## 4. Test matrix to preserve permanently

| Scenario | Expected result | Automated | Device test |
|---|---|---:|---:|
| Start twice | one scanner | [ ] | [ ] |
| Stop twice | no error, scanner remains stopped | [ ] | [ ] |
| Start/Stop x20 | no ghost jobs/callbacks | [ ] | [ ] |
| Screen off | scan continuity or explicit stall diagnostic | [ ] | [ ] |
| Process recreation | documented recovery | [ ] | [ ] |
| Bluetooth OFF | scanner stops cleanly | [ ] | [ ] |
| Bluetooth ON | no duplicate callback registration | [ ] | [ ] |
| Queue overload | loss/coalescing fully counted | [ ] | [ ] |
| Alert repeat | cooldown/dedup works | [ ] | [ ] |
| Alert mute during sound | sound stops immediately | [ ] | [ ] |
| Alert master OFF | all current output stops | [ ] | [ ] |
| Rotating MAC after sleep | no uncontrolled duplicate | [ ] | [ ] |
| Two similar nearby devices | no false destructive merge | [ ] | [ ] |
| Empty recent Radar | history remains accessible | [ ] | [ ] |

---

## 5. Diagnostics required before declaring stability

The application should expose or export at least:

- [ ] scanner runtime state,
- [ ] scanner service start timestamp,
- [ ] last BLE callback timestamp,
- [ ] BLE raw callbacks/min,
- [ ] accepted/coalesced events/min,
- [ ] persisted updates/min,
- [ ] signal samples/min,
- [ ] dropped events total,
- [ ] queue high-water mark or equivalent pressure indicator,
- [ ] last scan error,
- [ ] Bluetooth adapter state,
- [ ] active scan profile,
- [ ] Classic active yes/no,
- [ ] active probe active yes/no,
- [ ] database total device count,
- [ ] recent-window count,
- [ ] current alert state,
- [ ] last alert delivery result,
- [ ] alert policy state.

Diagnostics are part of the product during recovery, not temporary logging.

---

## 6. Files most likely to be modified first

Scanner/lifecycle:

- `core/data/src/main/java/io/blueeye/service/ScannerService.kt`
- `core/data/src/main/java/io/blueeye/service/ScannerServiceController.kt`
- `core/data/src/main/java/io/blueeye/core/scanner/manager/BleScanner.kt`
- `core/data/src/main/java/io/blueeye/core/scanner/source/BleScanSource.kt`
- `core/data/src/main/java/io/blueeye/core/scanner/source/ClassicScanSource.kt`
- `core/data/src/main/java/io/blueeye/core/data/scanner/AndroidScannerRuntimeController.kt`
- `core/data/src/main/java/io/blueeye/core/data/scanner/ScannerRuntimeDiagnosticsStore.kt`

Ingest/persistence:

- `core/data/src/main/java/io/blueeye/core/data/repository/DeviceRepositoryImpl.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/handler/ble/BleScanHandler.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/handler/ble/DevicePersister.kt`
- `core/data/src/main/java/io/blueeye/core/scanner/throttle/ScanThrottler.kt`

Identity:

- `core/data/src/main/java/io/blueeye/core/data/repository/handler/ble/MacAddressResolver.kt`
- `core/data/src/main/java/io/blueeye/core/data/tracker/AddressCarryoverTracker.kt`
- `core/data/src/main/java/io/blueeye/core/data/tracker/strategy/DeviceCorrelationStrategy.kt`
- `core/data/src/main/java/io/blueeye/core/data/repository/handler/classic/ClassicDevicePersister.kt`

Alerts:

- `core/data/src/main/java/io/blueeye/core/alert/AndroidAlertDispatcher.kt`
- `core/data/src/main/java/io/blueeye/core/alert/TrackerAlertService.kt`
- `core/data/src/main/java/io/blueeye/core/alert/TacticalAlertService.kt`
- `core/data/src/main/java/io/blueeye/core/alert/TacticalVibrationHandler.kt`

UI/observability:

- `feature/radar/src/main/java/io/blueeye/feature/radar/presentation/RadarViewModel.kt`
- `feature/settings/src/main/java/io/blueeye/feature/settings/SettingsViewModel.kt`
- `core/domain/src/main/java/io/blueeye/core/domain/usecase/GetScannedDevicesUseCase.kt`

---

## 7. Protocol for future ChatGPT / Local Agent sessions

At the start of every new chat working on stabilization:

1. Read `AGENTS.md`.
2. Read this file completely.
3. Read `docs/SANDBOX_EXECUTION_FLOW.md` and restore the compatible Library sandbox/source/cache assets for substantial work.
4. Read `.agent/binding.json` and live `.agent/status/daemon.json` only before Local Agent/Mac execution.
5. Check `NEXT ACTION`.
6. Inspect current `main` HEAD and changes since the last Work Log entry.
7. Do not begin a later phase while an earlier P0 checklist remains open unless the task is specifically diagnostic.
8. Implement the smallest coherent step.
9. Run targeted tests.
10. Run repository quality gate for published code changes.
11. Build APK when runtime code changed.
12. Update this file in the same work cycle: mark completed checkbox(es), update `NEXT ACTION`, add Work Log entry, and record commit/test evidence.

### Required response format after a stabilization task

A future assistant should report:

- what checklist item was completed,
- exact commit SHA,
- exact tests/checks run,
- whether device verification is still required,
- next unchecked action from this file.

Do not claim a runtime bug fixed only because unit tests/build are green when the acceptance criterion requires a real-device test.

---

## 8. Decision log

| Date | Decision | Reason |
|---|---|---|
| 2026-09-06 | Freeze feature growth and stabilize existing architecture instead of full rewrite. | Existing codebase already passes unit/quality/build checks; observed failures are concentrated in runtime lifecycle, throughput, identity and alert side effects. |
| 2026-09-06 | Stable Core should begin BLE-only. | Continuous Classic discovery + SDP + BLE low-latency increases radio and pipeline load and complicates identity debugging. |
| 2026-09-06 | Advanced code should initially be disabled, not immediately deleted. | Allows controlled reintroduction and avoids losing useful mature functionality before root causes are isolated. |
| 2026-09-06 | Identity correlation must become conservative and reversible. | Incorrect merge is worse than a temporary duplicate because destructive correlation can corrupt history. |
| 2026-09-06 | Alert runtime needs explicit ownership and cancellation. | Current policy can prevent future alerts but does not provide a reliable owner for stopping an already playing sound/vibration. |
| 2026-09-06 | Standardize build runtime/toolchain on JDK 21 while retaining JVM 17 bytecode target during stabilization. | One installed JDK simplifies sandbox/CI/Mac bootstrap without widening Android bytecode compatibility risk during the runtime recovery freeze. |
| 2026-09-06 | Use adaptive sandbox Gradle concurrency: 3 workers focused, 2 workers broad. | The measured 3-worker full gate exceeded sandbox memory headroom; 2-worker `qualityCheck` and `assembleDebug` both passed fully offline. |

---

## 9. Work Log

Append newest entries at the top of this section.

### 2026-09-06 — Sandbox-first/JDK 21 build infrastructure

- Status: infrastructure verified; product Phase 1 remains next.
- Decision: one installed JDK 21 for Gradle/compiler execution; JVM 17 bytecode target retained.
- Sandbox evidence:
  - exact-SHA source snapshot restored,
  - Android SDK 34 + Build Tools 34 + Gradle dependency cache restored from offline pack,
  - `:core:data:testDebugUnitTest` passed with `--offline` on JDK 21 (`BUILD SUCCESSFUL`, 1m 9s on cold-ish restored cache),
  - `qualityCheck` passed fully offline on JDK 21 with the safe 2-worker sandbox profile (`BUILD SUCCESSFUL`, 40s),
  - `:app:assembleDebug` passed fully offline on JDK 21 with the safe 2-worker sandbox profile (`BUILD SUCCESSFUL`, 28s),
  - a 3-worker/2 GiB full gate caused the Gradle daemon to be killed under lint/detekt/test overlap; broad sandbox gates are therefore capped at 2 workers while focused gates may use 3.
- Mac role reduced to ADB/device/Mac-specific evidence by default.
- Next action: Phase 1 — BLE Stable Core.

---

### 2026-09-06 — Recovery guide created

- Status: complete
- Baseline: `61f9f29a028fc31f6e1640daf4cfa12fdc1badeb`
- Evidence already available:
  - `:core:data:testDebugUnitTest` passed
  - `qualityCheck` passed
  - `:app:assembleDebug` passed
- Runtime/device stability: not yet accepted
- Next action: Phase 1 — BLE Stable Core

---

## 10. Definition of project recovery complete

The recovery initiative is complete only when:

- [ ] Stable Core passed Phase 7 on a real phone.
- [ ] Watchlist was reintroduced and passed regression testing.
- [ ] Follow-Me was reintroduced and passed regression testing.
- [ ] carryover/dedup was reintroduced conservatively and validated with controlled devices.
- [ ] Classic, if retained, operates as bounded enrichment instead of uncontrolled permanent background discovery.
- [ ] active probes are bounded and never block passive collection.
- [ ] alerts are always cancellable and non-spamming.
- [ ] no important runtime loss path is silent.
- [ ] documentation and diagnostics allow a new chat/developer to determine current system health without relying on memory.

After that point the feature freeze can be lifted and normal product development resumed.
