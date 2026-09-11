# Phase 3 captured field-data analysis — 2026-09-11

Status: **FIELD-DATA BLOCKERS FOUND; PHASE 3 REOPENED; PHASE 4 BLOCKED**

This document records the first full product-level analysis of the accepted field capture. It **supersedes the phase-boundary verdict** in `PHASE3_FIELD_REACCEPTANCE_CLOSURE_2026-09-11.md`.

The earlier closure remains valid for the narrow ingest/storage result: the queue, processing, Room persistence and process-lifetime accounting reconciled without drops or write failures. It was premature as an end-to-end field acceptance because the captured dataset reveals failures above and below that healthy ingest pipeline:

1. broad BLE scan delivery is not continuous with the screen off;
2. Follow-Me duration/movement semantics turn long observation gaps and destination devices into suspicious follow patterns;
3. short-window Apple identity carryover merges many simultaneously visible addresses into one logical device, inflating Follow-Me evidence.

No application code was changed while producing this analysis.

## Evidence source and privacy

Private checkpoint remains:

`~/agent-workspace/repos/tracker/checkpoints/phase3-field-reacceptance-20260911/20260911T142824Z`

Primary Session Export SHA-256:

`4eb23f6f0ee24220b65f7555e412dea78042d0ea5ab1685f537fbe0784ef6224`

Raw MAC addresses, exact GPS coordinates, payloads, Room files and HCI captures stay private. Device identifiers in analysis should be pseudonymized where individual records must be discussed.

## What remains accepted

The low-level ingest/storage result is still healthy:

- `55,691 raw = 51,466 accepted + 4,225 coalesced + 0 rejected`;
- queue drops: `0`;
- processing: `51,466 started / 51,466 succeeded / 0 failed`;
- provisional discarded: `1,571`;
- signal outcomes: `17,390 written / 32,505 throttled / 0 failed`;
- Room integrity: `ok`;
- app-context fatal/OOM/SecurityException/SQLite/processing failures: `0`.

Those facts show that events delivered into the application were processed reliably. They do **not** prove that Android delivered BLE results continuously while the screen was off or that higher-level identity/Follow-Me interpretation was correct.

## Captured timeline

Top-level persisted signal samples span approximately `13:28:05` to `16:03:36` local time: about `155.5 min` elapsed.

The `15,201` persisted samples are not distributed continuously. Using gaps over five minutes, the data form five observation islands:

| Island | Time | Duration | Samples | Logical devices | Observed MACs | Approx. endpoint displacement |
|---|---|---:|---:|---:|---:|---:|
| 1 | 13:28:05–13:41:01 | 12.9 min | 7,272 | 481 | 1,081 | 536 m |
| 2 | 14:36:43–14:48:28 | 11.7 min | 3,846 | 235 | 491 | 207 m |
| 3 | 15:12:46–15:19:31 | 6.8 min | 2,385 | 99 | 195 | 183 m |
| 4 | 15:31:04–15:39:50 | 8.8 min | 1,583 | 84 | 147 | 156 m |
| 5 | 16:03:20–16:03:36 | 0.3 min | 115 | 12 | 17 | effectively stationary |

The major no-sample gaps are approximately:

- `55.7 min`;
- `24.3 min`;
- `11.5 min`;
- `23.5 min`.

Only about `40.5 min` of the `155.5 min` elapsed range lies inside those observation islands. The first long gap is consistent with the phone being inside the pool locker. The later gaps occur after the phone left the locker and therefore required code/platform review rather than being dismissed as environmental shielding.

## Blocker 1 — unfiltered BLE scan stops with screen off

Accepted build `BleScanSource` starts the ordinary broad scanner as:

`BluetoothLeScanner.startScan(filters, settings, callback)`

where `filters` is `null` unless a specific MAC filter was requested.

Android's `BluetoothLeScanner` API contract explicitly states that **unfiltered scans are stopped on screen off to save power and resumed when the screen turns on**; the documented way to avoid that callback behavior is filtered scanning with an appropriate `ScanFilter`.

This matches the field shape extremely well: dense observations while the phone is actively being used, then complete multi-minute gaps while it is plausibly locked/in a pocket, and a final 16-second observation island immediately before the user paused the scanner after returning.

The persistence throttler cannot explain these gaps. A stable logical identity has a persisted signal heartbeat of `10 s` (`SIGNAL_SAMPLE_THROTTLE_MS`), with additional writes on meaningful MAC/status/RSSI changes. Therefore tens of minutes with no persisted sample across a busy BLE environment means no useful non-provisional scan stream reached the persistence path; it is not ordinary sample throttling.

### Decision

**Confirmed design/platform blocker.** The foreground service and live process are not sufficient to make the current broad, unfiltered `ScanCallback` scan screen-off safe.

Before reacceptance, the scanner needs a deliberate background strategy. The implementation choice must preserve product coverage and be tested on the target Samsung; do not assume that merely keeping `SCAN_MODE_LOW_LATENCY` or the foreground service solves screen-off delivery.

## Blocker 2 — Follow-Me duration counts wall-clock gaps as movement time

`FollowMeSessionManager` has one boolean `userHasMoved`. Once displacement from the initial reference reaches `50 m`, it becomes `true` and remains true until the entire logical tracking session is reset.

`FollowMeScoreCalculator` then enables follow-signal scoring whenever `userHasMoved == true` and computes duration as:

`lastSeenAt - firstSeenAt`

The explanation emitted is `Seen for N min while moving`.

These semantics are not equivalent. In the current implementation, after the user has moved once, a device can disappear for tens of minutes, reappear while the user is stationary, and receive duration points for the entire wall-clock gap as if it had been observed while moving.

### Field impact

There were `34` final attention-state devices (`33 SUSPICIOUS`, `1 DANGEROUS`).

- `26 / 34` had at least one observation gap greater than five minutes inside the logical record.
- Those 26 records are exactly the records spanning two separated capture islands.
- For **all 34** attention devices, subtracting the stored duration component from the final score leaves a score of `50` or lower. Therefore duration is the component that crosses the current `SUSPICIOUS` threshold for every final attention result in this capture.

Strong examples:

- five individually named Lime devices were seen before and after the pool interval with roughly `62–63 min` gaps; the two sightings remained in approximately the same area within the coarse GPS uncertainty, yet each ended at score `59` with an explanation of roughly one hour "while moving";
- a MacBook Air, Shelly Plug, Bose headphones, a smart-home device and a Samsung SmartTag were first observed near the final location, then had about `23.6–23.7 min` with no samples, and became `SUSPICIOUS` immediately after reacquisition around `16:03`; their GPS positions before/after the gap were essentially the same at field-test accuracy;
- the final `DANGEROUS` record had a `30.6 min` no-observation gap. Before the gap it remained `SAFE` at scores up to `50`; immediately after reacquisition it rose to `55`, then `80` within seconds because the wall-clock duration crossed the 30-minute tier.

### GPS makes the movement latch more fragile

The field export's location accuracy was approximately:

- median: `77.9 m`;
- p90: `131.4 m`;
- best: about `6.5 m`.

The movement latch threshold is only `50 m`, and `FollowMeSessionManager.updateMovement()` ignores the supplied location accuracy. The user did truly move in this run, so movement eventually should have become true, but this policy can also latch movement from coarse GPS jitter in other sessions.

### Decision

**Confirmed Follow-Me scoring defect.** Keep "has ever moved" if it is useful for baseline semantics, but it cannot stand in for current movement. Follow duration must be based on observed/moving intervals and must not accrue through long no-observation gaps.

## Blocker 3 — Apple shadow correlation over-merges concurrent devices

The short-window carryover path is designed to destructively merge addresses within `30 s`. Its Apple shadow shortcut explicitly allows `shadow -> shadow` matching and contains a `CONCURRENT BOOST`: when two Apple-like observations arrive within `1 s` with sufficiently similar RSSI, the code assumes one physical source and returns a `100%` carryover match.

The field data show that this assumption is unsafe in a dense public environment.

Examples from pseudonymized logical records:

- the final `DANGEROUS` Apple-like logical record contains `74` distinct observed MACs and up to **11 distinct MACs inside a single one-second bucket**;
- an `Apple AirPlay Target` logical record contains `76` observed MACs and up to **16 distinct MACs in one second**;
- other suspicious Apple-like records show `20–47` observed MACs with `6–14` distinct MACs in the same second.

Across the dataset, `23 / 31` records whose final type is generic `TRACKER` contain more than one observed MAC inside at least one one-second bucket.

This is not credible evidence of simple sequential address rotation for one logical physical device. It demonstrates that the carryover layer is clustering simultaneously present Apple-like devices. The downstream effects compound:

- `macChangeCount` is inflated;
- encounter count is inflated;
- RSSI history combines different physical sources;
- first/last-seen duration spans a crowd rather than one object;
- Follow-Me receives strong `MAC_ROTATION_WITH_STABLE_PAYLOAD`, duration and encounter evidence for an artificial aggregate.

The `DANGEROUS 80/100` result is therefore **not credible as one physical tracker**. It is explained by both the long-gap duration defect and identity over-merge.

### Decision

**Confirmed identity-correlation defect for crowded Apple-like traffic.** Simultaneously observed independent addresses must be anti-evidence for destructive rotation carryover, not a blanket positive "concurrent boost". Ambiguous Apple shadow observations should stay separate or become reversible candidates until stronger identity evidence exists.

## Re-evaluation of the 34 attention devices

The field capture does not support treating all `34` final attention records as plausible followers.

A conservative re-evaluation is:

- `26` records: score materially contaminated by long no-observation gaps / latched movement semantics;
- among the remaining eight continuous records, five Apple-like tracker records show clear concurrent-address over-merge;
- the remaining GFPS record uses two observed addresses that overlap in time, so it is also identity-ambiguous rather than clean evidence of one rotating physical device;
- two Eddystone Beacon records remain the strongest unresolved candidates: each was observed continuously for roughly `6–9 min`, used sequential rather than concurrent addresses, and was seen across material GPS displacement. They warrant HCI/manual review, but the data still do not prove malicious following.

So the correct product conclusion is not "1 dangerous tracker and 33 suspicious devices". The correct conclusion is that **most attention results in this capture are explainable by scoring/identity artifacts**, with only a small remainder deserving deeper evidence review.

## Long-gap identity candidates — safety boundary works, confidence does not

Five explicit long-gap identity candidates were stored. All are `SAME_NAME_PROXIMITY`, `candidateOnly=true` and `UNREVIEWED`.

Names include GFPS and common JBL/realme headphone models. In the export, the source/target pairs do not share an identical raw payload. Keeping them as review-only candidates rather than destructively merging them is the correct safety behavior.

However, these name-led candidates are stored with `confidence=1.0` / `scorePct=100`, which overstates the strength of same-name proximity evidence in a crowded environment. This is a tuning/semantics issue, not a current destructive-data bug because the verdict remains `UNREVIEWED`.

## HCI status

The private bugreport yielded a real LE trace:

- `25,066` parsed LE advertising reports across the useful captures;
- `432` HCI address values;
- `319` addresses also present in Tracker under exploratory population alignment.

Exact timestamp-domain alignment is still unresolved, so the HCI data must not yet be used to claim per-second app loss or successful per-packet capture. It remains useful for later payload/address-population review and, after clock semantics are established, for validating screen-off delivery.

## Updated phase decision

The previous narrow ingest verdict remains **PASS**, but end-to-end Phase 3 field acceptance is **REOPENED**.

Current gates:

- ingest queue/accounting: PASS;
- Room persistence/integrity: PASS;
- runtime crash/error stability: PASS;
- continuous screen-off BLE observation: **FAIL / BLOCKER**;
- Follow-Me time/movement semantics: **FAIL / BLOCKER**;
- crowded Apple identity correlation: **FAIL / BLOCKER**;
- HCI exact timing correlation: unresolved/non-primary;
- Phase 4: **BLOCKED until the bounded Phase 3 fixes are implemented and reaccepted**.

## Bounded fix direction — no code change yet

Do not start a broad refactor. The field evidence points to three bounded changes:

1. **Background-safe BLE scan strategy**
   - replace the current broad `filters=null` screen-off behavior with a deliberately supported Android background approach;
   - preserve broad foreground radar coverage where useful;
   - validate the selected filter/PendingIntent/foreground-service combination on the S22+ rather than assuming equivalence.

2. **Separate movement state from movement history**
   - retain an `everMoved` concept only where needed for baseline logic;
   - introduce current/recent movement semantics for Follow-Me;
   - accumulate observed moving duration instead of `lastSeen-firstSeen`;
   - stop duration accumulation across material observation gaps.

3. **Add an identity concurrency guard**
   - do not destructively carry over a new rotating address into a target that is simultaneously still being observed under another address unless a protocol-specific identity rule truly allows it;
   - remove/restrict the generic Apple `CONCURRENT BOOST` assumption;
   - prefer reversible identity candidates when multiple compatible Apple-like sources coexist.

Also make movement detection accuracy-aware; the current `50 m` threshold is smaller than the median location uncertainty in this capture.

## Required targeted reacceptance after fixes

Do not repeat a large field walk first. Use small controlled tests:

1. screen locked for at least 20–30 minutes in a known BLE-rich environment or beside a controlled BLE beacon; verify callbacks/persisted heartbeats continue while the display is off;
2. walk enough to establish movement, then remain stationary near fixed home devices for 20–30 minutes; they must not become Follow-Me suspicious merely because wall time advances;
3. expose the phone to a dense Apple-device environment; verify one logical fingerprint does not absorb multiple addresses observed concurrently;
4. rerun the original short varied-density route only after those targeted gates pass.

## Next task

Before writing code, finish the evidence review of the two unresolved Eddystone candidates and establish whether the HCI timestamp domain can be mapped reliably enough to inspect screen-off intervals. Then define the smallest implementation patch for the three confirmed blockers.