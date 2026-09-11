# Phase 3 captured field-data analysis — 2026-09-11

Status: **HISTORICAL ROOT-CAUSE ANALYSIS; BLOCKERS FIXED; PHASE 3 CLOSED; PHASE 4 UNBLOCKED**

This document records the product-level analysis of the real Phase 3 field capture that reopened acceptance. The defects described below were real in the pre-fix build and directly motivated PR #6. They are **not the current Phase 3 status**.

Final acceptance is recorded in `PHASE3_FIELD_REACCEPTANCE_CLOSURE_2026-09-11.md`.

Raw MAC addresses, exact GPS coordinates, payloads, Room/WAL/SHM, Session Export and HCI artifacts remain private.

## Historical field evidence

The field run itself proved that the ingest/storage pipeline was healthy:

- `55,691 raw = 51,466 accepted + 4,225 coalesced + 0 rejected`;
- queue drops: `0`;
- processing: `51,466 started / 51,466 succeeded / 0 failed`;
- provisional discarded: `1,571`;
- signal outcomes: `17,390 written / 32,505 throttled / 0 failed`;
- Room integrity: `ok`;
- persisted dataset: `873` devices / `15,201` signal samples;
- app-context fatal/OOM/Security/SQLite/processing failures: `0`.

Those numbers showed that events delivered into the application were processed reliably. They did not prove continuous Android BLE callback delivery or correct higher-level identity/Follow-Me interpretation.

## Captured timeline

Persisted signal samples covered approximately `155.5 min`, but were concentrated in five observation islands totaling only about `40.5 min`.

Major no-sample gaps were approximately:

- `55.7 min`;
- `24.3 min`;
- `11.5 min`;
- `23.5 min`.

The first gap was compatible with the phone being inside the pool locker. Later gaps occurred after leaving the locker and triggered platform/code investigation.

## Root cause 1 — screen-off BLE delivery

The pre-fix broad scan used `filters=null`. Android can suspend an unfiltered callback scan while the display is off, despite the app process and foreground service remaining alive.

This matched the field shape: dense samples while the phone was actively used, followed by complete multi-minute gaps when the phone was plausibly locked/in a pocket.

The persistence throttler could not explain tens of minutes with no persisted sample across a busy BLE environment.

### Resolution

PR #6 introduced explicit passive scan modes:

- `BROAD` while the screen is active;
- `BACKGROUND_FILTERED` while the screen is off using real Android scan filters;
- screen-state transitions restart only BLE registration and preserve scanner/service lifecycle.

Targeted S22+ reacceptance later confirmed real screen-off persistence growth and both mode transitions. See the final closure document.

## Root cause 2 — Follow-Me wall-clock gaps counted as movement time

The pre-fix implementation effectively combined two different concepts:

- the user had moved at some point in the session;
- the user/device were currently being observed together while moving.

Once the historical movement latch became true, duration scoring could use `lastSeenAt - firstSeenAt`, including long periods where the device was not observed and periods after the user had stopped moving.

### Field impact

There were `34` final attention-state devices (`33 SUSPICIOUS`, `1 DANGEROUS`).

- `26 / 34` contained an observation gap greater than five minutes;
- all 34 fell to score `50` or below if the stored duration component was removed;
- several fixed/home/public devices became suspicious only after reacquisition following long gaps;
- the final `DANGEROUS` record crossed higher score tiers immediately after a long no-observation period.

Field GPS accuracy was also coarse compared with the original fixed movement threshold:

- median about `77.9 m`;
- p90 about `131.4 m`;
- best about `6.5 m`.

A fixed `50 m` movement latch without using accuracy was therefore too fragile.

### Resolution

PR #6:

- separated current/recent movement from movement history;
- made movement handling accuracy-aware;
- accumulated Follow-Me duration only across contiguous observations during confirmed movement;
- stopped accumulation across long gaps/stationary periods;
- reset movement-scoped RSSI continuity across invalid boundaries.

Regression tests cover coarse GPS, long gaps and movement → stationary → movement behavior.

## Root cause 3 — concurrent Apple-like identity over-merge

The pre-fix short-window carryover path could treat near-simultaneous Apple-like observations with similar RSSI as positive evidence for one rotating physical source.

The captured data contradicted that assumption in a dense public environment:

- one final Apple-like logical record contained `74` distinct observed MACs and up to `11` distinct MACs inside one second;
- another Apple aggregate contained `76` observed MACs and up to `16` in one second;
- many other suspicious Apple-like records showed multiple concurrent addresses.

That is not credible evidence of simple sequential address rotation for one physical device. It inflated:

- MAC-change count;
- encounter count;
- RSSI history;
- first/last-seen duration;
- downstream Follow-Me evidence.

The pre-fix `DANGEROUS 80/100` result therefore cannot be treated as evidence of one malicious physical tracker.

### Resolution

PR #6:

- added a destructive-carryover coexistence guard;
- treats near-simultaneous addresses as anti-evidence for rotation;
- prevents generic Apple shadow-to-shadow destructive merging from proximity/RSSI alone;
- requires sequential timing plus stronger corroboration for positive carryover;
- retains long-gap identity candidates as reversible/review-only evidence.

Regression tests cover concurrent Apple-like addresses and valid sequential carryover.

## Re-evaluation of the pre-fix attention results

The correct conclusion from the original field dataset was not “1 dangerous tracker and 33 suspicious devices.” Most attention-state records were explainable by the two semantic defects above:

- long wall-clock gaps being counted as observed movement time;
- crowded Apple-like identities being merged into artificial aggregates.

Two continuous Eddystone records were among the stronger unresolved historical candidates, but even those were not proof of malicious following.

The purpose of this analysis was to find causal product defects, not to validate the pre-fix classifications.

## Long-gap identity candidates

Five explicit long-gap identity candidates were stored as `candidateOnly=true` and `UNREVIEWED`. That safety boundary worked: ambiguous long-gap hypotheses were not automatically promoted into destructive identity merges.

Confidence semantics for same-name proximity remain a future tuning topic, but they were not a destructive Phase 3 blocker.

## HCI status

The private bugreport contained real LE HCI trace data:

- `25,066` parsed LE advertising reports across useful captures;
- `432` HCI address values;
- `319` addresses also appeared in Tracker under exploratory population alignment.

Exact btsnoop/app timestamp-domain alignment was not established, so HCI remains a non-blocking population/payload cross-check rather than a per-packet loss oracle.

## Fix implementation and software acceptance

PR #6 (`Fix Phase 3 field blockers`) was validated on head:

`2e5223f22128059935c36bb24946cfef95eba958`

Software gates:

- Quality #148: PASS
- Secret Scan #178: PASS
- detekt: PASS
- Android lint: PASS
- unit tests: PASS
- debug APK build: PASS

PR #6 was squash-merged into `main` as:

`66b18fe525466618bba30a8860bb5d75f46ea1ec`

## Targeted S22+ reacceptance

The exact PR-head source was built locally and installed with `adb install -r`.

Installed APK SHA-256:

`e070afa0bda71685da8bce9fc66cf9526a67e7bf713963ca6338cd72fa6884c9`

Final targeted evidence:

- screen-on persisted samples: `17,033 → 17,054`;
- phone remained `Dozing` throughout the controlled 35 s screen-off interval;
- same app PID and foreground scanner service survived;
- screen-off persisted samples: `17,055 → 17,058`;
- logcat: `BROAD → BACKGROUND_FILTERED` after sleep;
- logcat: `BACKGROUND_FILTERED → BROAD` after wake;
- final app-PID log check: no fatal/AndroidRuntime/OOM/SQLite/Security/BLE-scan failure.

A harness exit code `141` occurred only after the substantive screen-off conditions had passed, due to `pipefail` combined with `grep -q`; it is not an app failure.

## Final phase decision

Historical blocker status:

- screen-off BLE observation: **FIXED + TARGET-DEVICE REACCEPTED**;
- Follow-Me gap/movement semantics: **FIXED + REGRESSION-TESTED**;
- concurrent Apple-like identity carryover: **FIXED + REGRESSION-TESTED**.

Current phase status:

- Phase 3: **ACCEPTED / CLOSED**
- Phase 4: **UNBLOCKED**
- additional Phase 3 field walk required before Phase 4: **no**

Longer future field runs remain useful product validation, but they are no longer a Phase 3 boundary condition.
