# Phase 3 final field reacceptance closure — 2026-09-11

Status: **PHASE 3 ACCEPTED / CLOSED; PHASE 4 UNBLOCKED**

This is the final authoritative Phase 3 closure record. It supersedes earlier Phase 3 status sections that either closed acceptance before captured-data analysis or later reopened acceptance after the real field dataset exposed end-to-end defects.

Raw Session Export, Room/WAL/SHM, exact GPS, MAC addresses, raw payloads, HCI snoop and bugreport data remain private and must not be committed.

## Final accepted source

- Repository: `MichalMatu/tracker`
- Fix PR: `#6` — `Fix Phase 3 field blockers`
- Tested PR head: `2e5223f22128059935c36bb24946cfef95eba958`
- Squash merge on `main`: `66b18fe525466618bba30a8860bb5d75f46ea1ec`
- Package: `io.blueeye`
- Device: Samsung `SM-S906B` (Galaxy S22+)
- Locally built/installed debug APK SHA-256: `e070afa0bda71685da8bce9fc66cf9526a67e7bf713963ca6338cd72fa6884c9`
- `adb install -r` of the exact local build: PASS

The final accepted source is therefore no longer the earlier `40eac7a...` field-build line. That build remains historical evidence for the dataset that exposed the defects.

## Why Phase 3 was reopened

The original field capture proved the ingest/storage pipeline was healthy but exposed three product-level blockers:

1. ordinary broad BLE scanning used an unfiltered Android `ScanCallback` path that could stop delivering results with the screen off;
2. Follow-Me treated wall-clock gaps after a historical movement latch as time observed while moving;
3. short-window identity carryover could destructively merge concurrently visible Apple-like addresses in dense environments.

The detailed pre-fix evidence remains in `PHASE3_CAPTURED_DATA_ANALYSIS_2026-09-11.md`.

## Implemented bounded fixes

PR #6 intentionally left the accepted queue/coalescing/Room ingest path alone and changed only the causal areas.

### Screen-off BLE

- screen-on passive scanning uses `BROAD` mode;
- screen-off passive scanning switches to `BACKGROUND_FILTERED` using real Android scan filters;
- screen state transitions restart only the BLE registration while preserving scanner/service lifecycle;
- wake returns the scanner to `BROAD` mode.

### Follow-Me semantics

- current/recent movement is separated from historical movement;
- movement handling is accuracy-aware;
- follow duration accumulates only across contiguous observations during confirmed movement;
- long gaps/stationary periods do not accrue follow duration;
- movement-scoped RSSI history is reset across invalid continuity boundaries.

### Identity carryover

- destructive short-window carryover has a coexistence guard;
- near-simultaneous addresses are anti-evidence for rotation rather than automatic merge evidence;
- generic Apple shadow-to-shadow observations are not destructively merged merely from proximity/RSSI;
- positive carryover requires sequential timing plus stronger corroboration.

Long-gap identity candidates remain reversible/review-only.

## Software gates — PASS

For PR head `2e5223f22128059935c36bb24946cfef95eba958`:

- Quality #148: **PASS**
- Secret Scan #178: **PASS**
- detekt: **PASS**
- Android lint: **PASS**
- unit tests: **PASS**
- debug APK build: **PASS**
- exact source artifact: **PASS**

The test suite includes regressions for coarse GPS, long observation gaps, movement → stationary → movement behavior, concurrent Apple-like addresses, and `BROAD ↔ BACKGROUND_FILTERED` scan transitions.

## Targeted Samsung S22+ reacceptance — PASS

The exact PR-head build was locally assembled and installed on the S22+.

A targeted final test first proved the environment was producing BLE persistence with the display active:

- screen-on samples: `17,033 → 17,054` in 8 s.

The phone was then forced to sleep and remained `Dozing` at both checks across the 35 s screen-off interval.

During that interval:

- process PID remained unchanged;
- foreground `ScannerService` remained active;
- persisted signal samples increased `17,055 → 17,058`;
- logcat showed `BROAD → BACKGROUND_FILTERED`;
- after wake, logcat showed `BACKGROUND_FILTERED → BROAD`.

The harness process returned code `141` only after these acceptance conditions had already passed because `set -o pipefail` combined with `grep -q` caused an expected upstream SIGPIPE. That exit code is a harness artifact, not an application failure.

A separate final app-PID log check then completed successfully with no:

- `FATAL EXCEPTION` / `AndroidRuntime` failure;
- OOM;
- SQLite exception;
- `SecurityException`;
- `BLE scan failed` event.

Result: **screen-off BLE blocker reaccepted on the target device**.

## Original field ingest/storage evidence remains accepted

The earlier real walk remains useful evidence that the ingest/storage machinery itself was healthy:

- `55,691 raw = 51,466 accepted + 4,225 coalesced + 0 rejected`;
- queue drops: `0`;
- processing: `51,466 started / 51,466 succeeded / 0 failed`;
- provisional discarded: `1,571`;
- signal outcomes: `17,390 written / 32,505 throttled / 0 failed`;
- Room integrity: `ok`;
- persisted field dataset: `873` devices / `15,201` signal samples.

Those values are historical field evidence and are not reinterpreted as semantic validation of the pre-fix Follow-Me/identity classifications.

## HCI limitation

The original private bugreport contained real LE HCI snoop data and provided a useful independent population cross-check. Exact btsnoop/app timestamp-domain alignment was not established, so HCI is not used as a per-packet acceptance oracle. This remains a documented non-blocking analysis limitation.

## Final decision

- ingest queue/accounting: **PASS**
- Room persistence/integrity: **PASS**
- runtime crash/error stability: **PASS**
- screen-off BLE observation on S22+: **PASS**
- Follow-Me gap/movement defect: **FIXED + regression-tested**
- concurrent Apple-like destructive carryover defect: **FIXED + regression-tested**
- new P0/P1 regression after fixes: **none reproduced**
- Phase 3: **ACCEPTED / CLOSED**
- Phase 4: **UNBLOCKED**

No further Phase 3 walk is required before beginning Phase 4. Future longer field runs are normal product validation, not a prerequisite for this phase boundary.
