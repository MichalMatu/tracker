# UI/UX P0 baseline — 2026-09-12

Status: **BASELINE CAPTURED; DENSE-DATASET COUNT PENDING FINAL READOUT**

Working branch: `ui/radar-details-redesign`

Source baseline before UI implementation: `72a7bb879e4537190eaca5298146acb6ecde25ca`

Accepted application baseline inherited from `main`: `093b4257859abdbcc683f1620969b06195ade7a6`

This document records the pre-redesign UI baseline. It intentionally does not change BLE collection, parsing, identity correlation, Follow-Me scoring, alert policy, persistence semantics, or field-data behavior.

## Private visual baseline

Baseline captures were taken from the physically connected Samsung `SM-S906B` using the already installed `io.blueeye` app. They remain in a private Local Agent checkpoint and are **not committed**, because screenshots/UI hierarchy may contain device names or other field telemetry.

Private checkpoint label:

`ui-p0-baseline-20260912`

Recorded artifacts:

- Radar screenshot SHA-256: `bdb9db5487b964ccad26aac0a184cf4f57400edb87f3787eae4be7b5f71546e3`
- Details screenshot SHA-256: `710eb8a82a632b48e0d1864b6d6500345392d648aae48bdfb653cdde267e78ab`
- corresponding UI hierarchy dumps are retained privately with the screenshots

The automated navigation found one large Radar-card click target and successfully entered a Details screen. The Details hierarchy contained Signal/Evidence/Follow-Me markers, confirming the capture was from the intended device-details flow.

## Radar frame baseline

A repeatable ADB scroll sample was captured after resetting `dumpsys gfxinfo io.blueeye` and performing eight upward plus eight downward swipes through Radar.

Baseline result:

- total frames rendered: `644`
- janky frames: `87` (`13.51%`)
- legacy janky frames: `118` (`18.32%`)
- p50 frame time: `7 ms`
- p90 frame time: `13 ms`
- p95 frame time: `22 ms`
- p99 frame time: `44 ms`

These numbers are not a universal product benchmark; they are the **before** measurement for the same device and comparable test procedure after Phase 1/2 changes.

## Current Radar structure

The current Radar already uses `LazyColumn` and stable item keys based on `fingerprint`, which should be preserved.

The current device card still contains multiple independently changing regions:

1. display name + live RSSI,
2. vendor/type,
3. seen/RSSI description,
4. horizontally scrollable badge row,
5. optional full evidence summary,
6. separate action row with Details, Calibration and Watchlist.

The entire card uses `stableLiveHeight()`. That helper remembers the largest measured height while the composable remains alive. It prevents later shrinking, but it does **not** stop first-time growth and can permanently retain an unnecessarily tall card after transient content appears.

### P0 Radar geometry risks

- Evidence appearing can increase card height.
- Evidence reason/value currently reserve several lines and dominate compact scanning.
- The action row duplicates navigation and adds a full persistent row.
- Optional badge content changes card information density even when row height itself remains stable.
- A card that once grows can remain at that maximum due to `stableLiveHeight()`.

## Current Details structure

Details currently renders its complete content inside one `Column.verticalScroll` rather than a lazy list. The default flow composes:

- Header,
- Evidence,
- optional Alert History,
- Calibration,
- Connection,
- optional Signal History,
- optional Follow-Me History,
- optional Sensor Data,
- Identity,
- Activity,
- Radio,
- optional Extended Info,
- optional Services,
- FAB spacing.

`DetailsViewModel` rate-limits visible device, signal-history, Follow-Me-history and alert-evidence updates to `750 ms`, so multiple potentially large sections can receive new content during a single Details session.

### P0 Details geometry/performance risks

- all sections are composed inside one vertical scroll container;
- optional sections may appear/disappear as data becomes available;
- several sections independently use `stableLiveHeight()`;
- technical, decision and action information are presented at similar visual priority;
- long histories can increase the amount of composed content even when the user is viewing only the first viewport.

## Stable-layout rules for Phases 1-8

These are implementation guardrails, not visual styling preferences.

1. **Live values may change; structural slots should not.** RSSI, age and status updates must update text/color inside an existing slot rather than insert/remove rows.
2. **Primary Radar cards use a fixed structural skeleton.** Name, compact identity, freshness/technology and a bounded status area get predictable line counts.
3. **No automatic badge wrapping.** Radar shows at most the small fixed set of decision-critical badges defined by the plan.
4. **Expandable content changes height only after explicit user input.** Technical details, all evidence, raw payloads and long histories may expand in Details because the user requested them; incoming BLE data alone should not unexpectedly expand the viewport above the user.
5. **Stable keys remain mandatory.** Radar keeps `fingerprint` keys; future Details lazy sections get explicit stable keys.
6. **`stableLiveHeight()` is a fallback, not the layout model.** Prefer bounded content and fixed slots; do not use monotonically retained height to compensate for inherently unstable composition.
7. **No hidden exact-distance semantics.** RSSI stays visible but is not translated into an exact distance claim.
8. **Observed-map points remain phone observations.** Future map UI must carry location-accuracy context and must not imply exact tracker location.

## Comparison procedure after Radar changes

Use the same Samsung `SM-S906B` and the same basic procedure:

1. launch the same package,
2. open Radar with a representative populated dataset,
3. reset `dumpsys gfxinfo io.blueeye`,
4. perform eight upward and eight downward swipes with the same duration,
5. record total/janky frames and p50/p90/p95/p99,
6. compare card geometry visually against the private P0 Radar screenshot,
7. verify that live RSSI/last-seen updates do not change card height,
8. repeat with a dense 100+ recent-device scenario before Phase 2 is accepted.

## P0 gate state

Completed:

- branch isolation confirmed;
- current Radar screenshot captured privately;
- current Details screenshot captured privately;
- frame/jank baseline captured;
- current Radar/Details geometry risks recorded;
- stable-height rules defined;
- scanner/parser/scoring/persistence semantics remain untouched.

Still required before declaring P0 fully closed:

- record/confirm the dense-list reference scenario with at least 100 devices in the relevant recent-device window, or define a reproducible non-production fixture if the current physical dataset does not contain such a window.

No application source code was changed for this baseline.
