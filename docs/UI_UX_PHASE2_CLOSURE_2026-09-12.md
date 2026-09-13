# UI/UX Phase 2 Closure — 2026-09-12

Status: **CLOSED / ACCEPTED**

Working branch: `ui/radar-details-redesign`

Accepted `main` baseline: `093b4257859abdbcc683f1620969b06195ade7a6`

Accepted Phase 2 code/test checkpoint: `47fe5306b0d5bfa4b3769ebd4353e4c2bfaf7ab4`

## Scope

Phase 2 reduced Radar presentation and upstream data-path cost without intentionally changing BLE scanning, parsing, identity, Follow-Me scoring, alert policy, persistence semantics, Radar filtering, ordering, section classification, or visible decision status.

## Accepted implementation sequence

- P2A moved combined Radar presentation transformation off the UI thread with `flowOn(Dispatchers.Default)`.
- P2B removed dead compact-card presentation work while preserving section semantics.
- P2C replaced the Radar `SELECT * -> full DeviceEntity -> full Device -> complete DeviceEvidenceFactory` path with a Radar-specific lightweight projection/domain contract.

P2C functional checkpoint:

- `d8bde8c0727926e4a860b0ebae54fcbe651c55f6` — `perf: add lightweight radar device path`

Style-only follow-up:

- `d107dad886894f0e8e663f53006d11e0f9910f4f` — `style: satisfy radar ktlint`

Controlled dense-list fixture update after the flattened Radar contract:

- `47fe5306b0d5bfa4b3769ebd4353e4c2bfaf7ab4` — `test: update radar dense fixture for summary model`

The fixture-only commit does not change production behavior.

## P2C behavior contract preserved

The lightweight path preserves the decision inputs required by Radar:

- legacy RSSI normalization;
- technology/type/vendor/connectability filtering;
- stable fingerprint identity and list keys;
- watchlist state and watchlist evidence;
- calibration labels;
- tracking status and Follow-Me score thresholds;
- tracker-like, public-safety-like, attention and Follow-Me attention signals;
- exact Radar section priority and classification semantics;
- existing Radar card ordering inputs;
- fingerprint-only UI callbacks.

The generic scanned-device use case remains available; Radar uses its dedicated lightweight path.

## Verification

### Local / contract verification

P2C focused and local gates passed before the accepted checkpoint:

- `DeviceEvidenceFactoryRadarSignalsTest` PASS;
- Radar mapper/formatter/order/section contract tests PASS;
- `:core:model:detekt` PASS;
- `:core:data:detekt` PASS;
- `:core:domain:detekt` PASS;
- `:feature:radar:detekt` PASS;
- `:feature:radar:compileDebugKotlin` PASS;
- `:app:assembleDebug` PASS;
- full local `qualityCheck` PASS;
- `git diff --check` PASS.

### GitHub CI on accepted checkpoint

For `47fe5306b0d5bfa4b3769ebd4353e4c2bfaf7ab4`:

- Quality run `34699534594` — PASS;
- Secret Scan run `34699534682` — PASS.

## Controlled 120-item S22+ acceptance

Device: Samsung `SM-S906B`.

The deterministic `RadarDenseListStabilityTest` uses 120 stable synthetic Radar items, performs live-value updates, verifies card-height stability, and scrolls to the final item. After updating the fixture to the flattened P2C `RadarUiItem` contract:

- androidTest compilation PASS;
- Radar ktlint/detekt PASS;
- `connectedDebugAndroidTest` for `RadarDenseListStabilityTest` PASS on the physical S22+;
- full local `qualityCheck` PASS.

The controlled 120-item scenario remains test-only and does not mutate the real field database.

## Physical field soak and comparable Radar benchmark

The exact APK built from the accepted checkpoint was installed on the S22+ and byte-verified against the installed package before field use.

Extended field-use capture after the walk:

- total rendered frames: `23,504`;
- janky frames: `2,598` = `11.05%`;
- p50: `5 ms`;
- p90: `16 ms`;
- p95: `24 ms`;
- p99: `53 ms`;
- fatal exception count: `0`;
- OutOfMemoryError count: `0`;
- app ANR count: `0`;
- exit-info crash/ANR/resource count: `0`.

This long capture is a soak/stability signal, not the directly comparable P0 benchmark because it covers uncontrolled field interaction over a much longer period.

The final directly comparable P0 procedure was then repeated on the S22+: reset `gfxinfo`, perform 8 upward plus 8 downward Radar swipes, then collect the summary.

Final Phase 2 result:

- total frames rendered: `1,265`;
- janky frames: `57` = **`4.51%`**;
- p50: `6 ms`;
- p90: `10 ms`;
- p95: `12 ms`;
- p99: `36 ms`;
- fatal exception count: `0`;
- OutOfMemoryError count: `0`;
- app ANR count: `0`;
- total PSS at capture: `187,504 KiB`.

P0 comparison on the same phone/procedure:

- P0 jank: `13.51%` -> Phase 2 `4.51%`;
- P0 p95: `22 ms` -> Phase 2 `12 ms`;
- P0 p99: `44 ms` -> Phase 2 `36 ms`.

This is a 9.00 percentage-point reduction in janky-frame rate versus P0, about a 66.6% relative reduction.

P2A's earlier 3.69% result remains useful directional evidence but had only 5 fresh live rows; the Phase 2 closure relies on the combined controlled 120-item device test plus the final comparable physical benchmark and field soak.

## Database / live-scan sanity

Read-only pre/post database snapshots around the final benchmark both returned `PRAGMA integrity_check = ok`.

Privacy-safe aggregate counts:

- devices: `4,428` -> `4,429`;
- signal samples: `86,418` -> `86,621`;
- Follow-Me observations: `14,462` -> `14,479`;
- recent devices inside the final 180 s of each dataset: `14` -> `12`.

These changes confirm that the physical run was operating against a live-updating field dataset. No raw database, MAC, GPS, screenshot, or private telemetry is committed.

## SQLiteException diagnosis

The final benchmark log contained two textual `SQLiteException` occurrences. A bounded stack-context inspection showed both are the same Android/system media database error involving `files._id`, `scene`, `bucket_id` and a system `DatabaseUtils` query. They contain no `io.blueeye` or AndroidX Room application stack and are not a Tracker database failure.

Tracker database integrity remained `ok` before and after the benchmark, and no application fatal exception/ANR occurred. These system-media log entries are therefore not a Phase 2 blocker.

## Cadence / conflate decision

No `conflate()` or presentation cadence change was added for closure. The structural upstream optimization produced a strong physical result without needing to hide work behind additional dropping/throttling. The existing cadence can be revisited only if a future measured regression justifies it.

## Closure decision

**Phase 2 is CLOSED / ACCEPTED.**

Acceptance basis:

- lightweight Radar projection/domain path landed;
- complete full-Device/full-Evidence construction is no longer required solely for Radar;
- section/filter/order behavior is protected by focused contract tests;
- controlled 120-item test passes on the physical S22+;
- full local quality gate passes;
- accepted checkpoint CI is green;
- extended field use shows no Tracker crash/ANR/OOM;
- comparable S22+ Radar jank improved substantially versus P0;
- database integrity is preserved.

No production change is required from the two unrelated system-media SQLite log entries.

The next UI/UX implementation phase is **Phase 3 — Details redesign around information priority**. Do not reopen Phase 2 unless a concrete regression is observed.
