# UI/UX Redesign and Analysis Pipeline Plan

## Status

- **Active plan**
- Working branch: `ui/radar-details-redesign`
- Baseline `main`: `093b4257859abdbcc683f1620969b06195ade7a6`
- Phase 3: **CLOSED / ACCEPTED**
- Phase 4: **UNBLOCKED**
- Current focus: Radar + Details usability, stability and presentation performance

This document is the execution checklist for the next product iteration. Keep the checkboxes and acceptance gates current as work lands. UI work must remain isolated from scanner/parser/scoring behavior until the UI redesign is accepted.

## Product principles

1. **Summary first, evidence second, raw data last.** Existing technical data remains accessible, but it must not dominate the default UI.
2. **Live data may change values, not geometry.** RSSI, last-seen and status updates must not make cards collapse, expand or jump during normal scanning.
3. **Radar answers three questions quickly:** what is it, how recently/strongly is it visible, and does it need attention?
4. **Details explains the decision.** It should show why the device matters before exposing diagnostic internals.
5. **Progressive disclosure.** UUIDs, raw payloads, PHY/GATT and full evidence stay available through explicit expansion/navigation.
6. **RSSI is useful context, not precise distance.** Keep a signal chart without presenting RSSI as exact ranging.
7. **Observed location is not device location.** Future maps show where the phone observed a signal and must preserve location-accuracy context.
8. **Local deterministic logic remains authoritative offline.** Future AI analysis is optional and separately identified.
9. **Do not trade field reliability for UI work.** Phase 3 scanner/storage behavior stays unchanged during phases 0-8 unless a separate defect is proven.

---

# Milestone A — UI/UX redesign

## Phase 0 — Baseline and guardrails

- [ ] Capture baseline screenshots of current Radar and Details on a representative dataset.
- [ ] Record a baseline long-list scenario (100+ recent devices) for later performance comparison.
- [ ] Keep all redesign changes on `ui/radar-details-redesign` until acceptance.
- [ ] Do not change BLE collection, parser, identity carryover, Follow-Me scoring or persistence semantics as part of visual cleanup.
- [ ] Treat information in three levels: **summary -> evidence/context -> technical/raw**.
- [ ] Define reusable stable-height rules for live cards instead of using layout growth as a side effect of incoming data.

**Gate:** baseline captured and UI work can be compared without changing field semantics.

## Phase 1 — Compact Radar cards

Target default card shape:

```text
AirTag                                  -61 dBm
Apple · Tracker
Seen now · BLE
SUSPICIOUS   WATCHLIST
```

- [ ] Remove the redundant `Details` button; tapping the card already opens Details.
- [ ] Remove the full evidence summary from each Radar card.
- [ ] Remove default `Source`, `Reason`, `Value`, raw payload and evidence chips from Radar.
- [ ] Remove calibration controls from Radar.
- [ ] Keep display name / user alias as the primary label.
- [ ] Keep vendor + classified type as compact secondary identity.
- [ ] Keep RSSI prominent and in a fixed slot.
- [ ] Keep `Seen now / Xs ago` freshness.
- [ ] Keep BLE / Classic technology as low-priority context.
- [ ] Show at most 1-2 important status badges such as `SUSPICIOUS`, `WATCHLIST` or `PUBLIC SAFETY`.
- [ ] Evaluate whether `SAFE` should be omitted when it adds no decision value.
- [ ] If Watchlist remains a quick action, use one compact icon in a fixed position instead of a full action row.
- [ ] Limit name and identity lines to one line each with ellipsis.
- [ ] Prevent badge wrapping from changing card height.
- [ ] Give every normal Radar card the same structural slots so live values do not reflow the list.

**Gate:** with 100+ devices and live RSSI updates, cards under the user's finger do not move because their own height changes.

## Phase 2 — Radar data-path and performance cleanup

The Radar must not build or transport full technical device state merely to draw a compact list.

- [ ] Introduce a lightweight Radar projection/model (for example `RadarDeviceSummary`) containing only fields required by the list and sectioning logic.
- [ ] Avoid loading full GATT/characteristic/raw technical fields for the Radar list where possible.
- [ ] Avoid building and sorting the complete `DeviceEvidenceFactory` output for every visible/recent device solely for Radar rendering.
- [ ] Preserve only the small set of precomputed decision flags needed for Radar sectioning and badges.
- [ ] Decouple frequently changing presentation fields (RSSI/last seen) from expensive classification/evidence work.
- [ ] Review the current 750 ms presentation cadence; use the slowest refresh that still feels live.
- [ ] Keep stable list keys based on fingerprint/stable identity.
- [ ] Verify that one device update does not unnecessarily rebuild the whole visible tree.
- [ ] Measure recomposition/jank and UI-thread work before and after the change.
- [ ] Run a 30-60 minute dense-scan UI soak.

**Gate:** scrolling remains responsive as the recent-device population grows; no progressive slowdown attributable to Radar presentation work.

## Phase 3 — Rebuild Details around information priority

Default order:

1. **Device summary**
2. **Why it matters**
3. **Tracking & Signal**
4. **Key evidence**
5. **Identity**
6. **Actions / Review**
7. **History**
8. **Technical details**
9. **All evidence**

- [ ] Merge the current Header and decision summary into one clear top card.
- [ ] Stop repeating the same display name/fingerprint between TopBar and Header.
- [ ] Keep current RSSI and last seen visible near the top.
- [ ] Show Follow-Me score prominently only when it contributes meaningful decision context.
- [ ] Add/retain a short `Why it matters` explanation, normally no more than 2-3 lines.
- [ ] Do not render a large empty `No evidence` card.
- [ ] Show at most 1-3 key evidence items in the default decision flow.
- [ ] Move complete evidence source/provenance/raw/parsed fields into `All evidence`.
- [ ] Move Calibration into `Actions / Review`.
- [ ] Group Watchlist, alias/notes and alert preferences into coherent user actions.
- [ ] Move active connection/GATT controls lower unless they are required for the current device task.
- [ ] Consolidate Identity: stable identity, vendor, type/model and relevant address context.
- [ ] Do not present a rotating/random MAC as the primary identity.
- [ ] Move PHY, advertising interval, beacon type, services, firmware, serial and similar fields under Technical details.
- [ ] Keep raw advertisement accessible but one level deeper than the normal Details view.

**Gate:** the first viewport explains the device and its risk/context without requiring the user to parse technical Bluetooth fields.

## Phase 4 — Tracking & Signal

The RSSI chart remains a first-class Details element.

- [ ] Retain the existing signal-strength chart.
- [ ] Use a meaningful time axis rather than treating points only as ordinal samples.
- [ ] Show current RSSI clearly.
- [ ] Show a compact trend plus useful aggregate such as median and range/min-max.
- [ ] Do not label RSSI as exact physical distance.
- [ ] Show Follow-Me score/status alongside signal context where useful.
- [ ] Show movement state (`moving`, `stationary`, `unavailable`) when relevant.
- [ ] Consider optional movement-interval markers on the signal chart.
- [ ] Consider optional identity/address-transition markers when they aid interpretation.
- [ ] Put detailed Follow-Me snapshots behind `Show history` / expansion.
- [ ] Downsample/aggregate chart points to the resolution needed by the viewport instead of drawing unnecessary points.

**Gate:** the graph remains useful during a long live session without becoming a high-cost rendering path.

## Phase 5 — Layout stability and lazy composition

- [ ] Replace the monolithic `Column.verticalScroll` Details layout with a keyed `LazyColumn` or an equivalent lazy structure.
- [ ] Give major Details sections stable keys.
- [ ] Avoid automatically inserting/removing large sections above the current viewport as live data arrives.
- [ ] Make major default cards structurally predictable in height.
- [ ] Expand secondary/technical content primarily through explicit user action.
- [ ] Audit uses of `stableLiveHeight()`; retain it only where it solves a proven problem rather than masking unstable composition.
- [ ] Verify live refresh while scrolling, not only static previews.
- [ ] Verify back/forward navigation preserves sensible scroll/state behavior.

**Gate:** active scanning does not cause unexpected vertical jumps in Radar or Details.

## Phase 6 — Technical Peek

Keep important Bluetooth details easy to inspect without putting them on the surface.

Default collapsed section: **Technical details**.

- [ ] Address / address type.
- [ ] Manufacturer ID.
- [ ] Service UUIDs.
- [ ] Advertising interval.
- [ ] Tx Power.
- [ ] PHY.
- [ ] Beacon type.
- [ ] Model/firmware/serial when available.
- [ ] GATT services/characteristics where available.
- [ ] Raw advertisement one level deeper.
- [ ] Render long technical values with suitable monospace presentation.
- [ ] Add Copy actions where useful.
- [ ] Ensure live technical updates do not alter collapsed-section height.

**Gate:** a technical user can still inspect the important BLE facts quickly, while a normal user never has to read them to understand the device.

## Phase 7 — Per-device Sightings Map

Existing signal samples already contain optional GPS coordinates and location accuracy. The first map should live in Details rather than creating a global map tab immediately.

- [ ] Add a `Sightings map` section using only samples with valid location data.
- [ ] Preserve and use location accuracy when filtering/visualizing points.
- [ ] Clearly label semantics as **where the phone observed the signal**, not exact tracker location.
- [ ] Avoid one marker per raw sample; cluster/aggregate nearby observations.
- [ ] Show first and latest meaningful sightings.
- [ ] Show route/observation areas only when the data quality supports them.
- [ ] Allow selecting an observation/cluster to see timestamp and RSSI summary.
- [ ] Consider RSSI-based visual context only if it does not imply false ranging precision.
- [ ] Handle missing/poor GPS gracefully.
- [ ] Defer a global `Map` navigation destination until the per-device map proves useful.

**Gate:** map interpretation remains honest under poor GPS and dense sample sets.

## Phase 8 — UX acceptance audit

Benchmark the result against the useful interaction patterns from nRF Connect, AirGuard, Android Unknown Tracker Alerts, Find My / Find Hub, Samsung Find, Tile/Chipolo and LightBlue without copying their product-specific behavior blindly.

- [ ] Real Samsung S22+ test.
- [ ] Dark mode.
- [ ] Large Android font scale.
- [ ] 100-500 recent devices.
- [ ] 30-60 minute scan.
- [ ] Device with extensive evidence.
- [ ] Device with no attention evidence.
- [ ] Suspicious tracker-like candidate.
- [ ] Normal headphones/phone/peripheral.
- [ ] Unknown/noise device.
- [ ] Watchlist device.
- [ ] Live RSSI changes during scroll.
- [ ] Details expansion/collapse while live data changes.
- [ ] No regression in existing scanner/field behavior.

**Gate:** merge redesign only after usability, jank and field-behavior checks pass.

---

# Milestone B — Analysis Pipeline

This milestone follows the UI work. Its goal is to make deterministic local processing do most of the mechanical work and reserve AI for the genuinely hard cross-signal interpretation.

## Phase 9 — Parser and data hardening

- [ ] Build a corpus of real captured advertisements from field datasets with privacy-safe fixtures.
- [ ] Add malformed/truncated payload cases.
- [ ] Add parser property/fuzz tests where appropriate.
- [ ] Add representative Apple, Samsung, Google/Fast Pair, Tile, Chipolo and beacon fixtures where real evidence exists.
- [ ] Harden UUID/manufacturer/advertisement parsing boundaries.
- [ ] Harden random-address and identity-transition handling.
- [ ] Validate timestamp/clock assumptions.
- [ ] Formalize GPS accuracy/use policy.
- [ ] Replay the regression suite over preserved field scenarios.

**Gate:** parser/reducer changes are reproducible against fixtures instead of being tuned only by intuition.

## Phase 10 — Deterministic reducer

Goal: reduce tens of thousands of callbacks into a small number of meaningful analysis candidates without AI.

- [ ] Obvious-noise filtering/classification.
- [ ] Duplicate/repeated-advertisement reduction.
- [ ] Time bucketing.
- [ ] Identity-candidate clustering with coexistence safeguards.
- [ ] Movement intervals.
- [ ] Encounter segmentation.
- [ ] RSSI summaries/trends.
- [ ] Location clustering.
- [ ] Representative payload selection.
- [ ] Anomaly/contradiction extraction.
- [ ] Confidence/data-quality flags.
- [ ] Produce a stable `AnalysisCandidate` domain representation.

**Gate:** a large field session can be reduced locally to a small, explainable candidate set.

## Phase 11 — Versioned Analysis Bundle

Create compact structured input for heavier external analysis instead of uploading the entire database/raw stream.

- [ ] Schema version.
- [ ] Device/candidate summary.
- [ ] Timeline/encounters.
- [ ] Confirmed movement context.
- [ ] RSSI statistics/trends.
- [ ] Location clusters with quality metadata.
- [ ] Identity/address transitions.
- [ ] Selected representative packets/features.
- [ ] Local evidence and local verdict.
- [ ] Contradictions/unknowns/data-quality warnings.
- [ ] Privacy review and explicit exclusion of unnecessary raw data.
- [ ] Deterministic serialization tests.

**Gate:** typical candidate bundle is small, inspectable and sufficient for higher-level reasoning without a full DB export.

## Phase 12 — Optional AI Analyst

Keep AI analysis distinct from the offline local assessment.

- [ ] Explicit opt-in mode.
- [ ] Preview exactly what will be sent before transmission where practical.
- [ ] Send Analysis Bundle, not unrestricted DB/raw history.
- [ ] Define structured response schema containing at least:
  - assessment
  - confidence
  - supporting evidence
  - counter evidence
  - unknowns / data-quality caveats
- [ ] Store/display AI assessment separately from local assessment.
- [ ] Add an `AI Analysis` section in Details only when an analysis exists.
- [ ] Handle offline/API failure without degrading local detection.
- [ ] Version request/response schemas.

**Gate:** disabling AI leaves a fully functional local tracker; enabling it adds explanation/interpretation rather than replacing collection or safety-critical deterministic logic.

## Phase 13 — Field feedback loop

Use every meaningful field session to improve the deterministic system.

```text
field walk
  -> captured dataset
  -> deterministic reducer
  -> AI/manual analysis
  -> identified false positive / false negative / ambiguity
  -> privacy-safe fixture
  -> parser/reducer/scorer change
  -> replay all regression datasets
  -> next field walk
```

- [ ] Define a repeatable field-dataset intake checklist.
- [ ] Convert confirmed problems into fixtures before changing rules where possible.
- [ ] Track regressions across old datasets, not only the newest walk.
- [ ] Record why each heuristic/rule exists and which fixture protects it.
- [ ] Prefer moving repeatable mechanical reasoning from AI/manual review into deterministic code over time.
- [ ] Preserve AI as a higher-level analyst for ambiguous multi-signal cases.

**Gate:** accuracy improvements become cumulative and testable rather than one-off tuning to the latest capture.

---

## Immediate execution order

1. Phase 0 baseline/guardrails.
2. Phase 1 compact Radar card.
3. Phase 2 Radar performance/data projection.
4. Phase 3 Details hierarchy.
5. Phase 4 Tracking & Signal.
6. Phase 5 layout stability/lazy composition.
7. Phase 6 Technical Peek.
8. Phase 7 Sightings Map.
9. Phase 8 UX acceptance.
10. Only then begin Milestone B parser/reducer/AI work.

## Definition of done for Milestone A

Milestone A is complete only when the redesigned UI is visually simpler **and** demonstrably more stable under live scanning: no redundant Radar actions, no default technical dump, accessible diagnostic detail, retained RSSI history, predictable card geometry, responsive long lists, lazy Details composition, and no scanner/storage/scoring regression.
