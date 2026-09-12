# UI/UX Phase 1 Acceptance — Compact Radar Cards

Date: 2026-09-12

## Verdict

**ACCEPTED / CLOSED**

Phase 1 of `docs/UI_UX_REDESIGN_PLAN.md` is accepted on the code state ending at:

`f554b0d5d97f5185cdd1bddba0411559ce2c8362`

The next implementation focus is Phase 2: Radar data-path and presentation-performance cleanup. Do not reopen Phase 1 unless a new concrete UI regression is demonstrated.

## Accepted behavior

The Radar card now keeps a compact, predictable structure:

- whole-card tap opens Details;
- redundant `Details` action removed;
- full evidence/source/reason/value block removed from Radar;
- calibration controls removed from Radar and remain a Details concern;
- name/alias remains primary identity;
- vendor/type remains compact secondary identity;
- RSSI and freshness remain visible;
- BLE/Classic remains low-priority context;
- at most two meaningful decision badges are shown;
- Watchlist remains a single compact quick action;
- `stableLiveHeight()` is no longer used by the Radar card;
- live values update without changing the structural card geometry.

No scanner, parser, persistence, identity-carryover, Follow-Me scoring, or alert semantics were intentionally changed by Phase 1.

## Physical S22+ validation

Device: Samsung SM-S906B.

A real live Radar scan was started from the paused state. Two UI hierarchy captures five seconds apart contained five visible live Radar rows. The row-center gaps were identical before and after live RSSI/last-seen updates:

- capture A: `330, 330, 330, 330 px`
- capture B: `330, 330, 330, 330 px`

This validates the Phase 1 requirement that ordinary live value changes do not collapse or expand the cards under the user's finger.

The same physical run completed with no fatal exception, `OutOfMemoryError`, `SQLiteException`, or `SecurityException` in the captured process log tail.

## P0 -> P1 rendering comparison

P0 baseline:

- total frames: `644`
- janky frames: `87` (`13.51%`)
- p95: `22 ms`
- p99: `44 ms`

P1 real live Radar:

- total frames: `725`
- janky frames: `84` (`11.59%`)
- p95: `21 ms`
- p99: `38 ms`

The runs are not a laboratory-grade performance benchmark because the live RF population differs, but the Phase 1 presentation change did not regress the measured physical scrolling path and the observed jank metrics improved.

## Controlled dense-list gate

The current physical database contained many historical devices but only a small number inside the Radar's rolling 180-second window, so a deterministic non-production Compose instrumentation scenario was added instead of mutating the user's database.

`RadarDenseListStabilityTest` renders **120 real `RadarDeviceItem` composables**, then:

1. captures the first card height;
2. updates RSSI, `Seen`, warning/watchlist state across the dense list;
3. verifies the first card height remains unchanged;
4. scrolls the lazy list to item 119 and verifies it is displayed.

Result on Samsung SM-S906B:

- instrumentation tests: `1/1 PASS`
- `BUILD SUCCESSFUL`
- `P1_DENSE_120_ANDROIDTEST_PASS`
- repository clean after the run

The fixture exists only in `src/androidTest`; it adds no production debug mode and does not alter the application database.

## CI

For exact Phase 1 head `f554b0d5d97f5185cdd1bddba0411559ce2c8362`:

- Quality #158: **PASS**
- Secret Scan #188: **PASS**

The earlier code-only Phase 1 head `dbf91226688f98151f75c20f742079981bcadd41` also passed Quality #155 and Secret Scan #185 and was locally assembled/installed successfully before the dense-list test was added.

## Phase 1 closure

Phase 1 is complete. The Radar surface is now intentionally simpler and has a reproducible 120-item stability regression test.

Phase 2 should optimize the data path without changing the accepted card hierarchy or Phase 3 field semantics. The first target is CPU work performed while producing each Radar snapshot; the larger target is avoiding construction/transport of full technical/evidence state for a compact list.