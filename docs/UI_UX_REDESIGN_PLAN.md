# UI/UX and analysis plan

## Status

- **Active product plan** on `main`.
- Scanner/ingest Phase 3 baseline is accepted; do not reopen it without a concrete regression.
- Compact Radar cards, lightweight Radar projection, decision-first Details and collapsed Technical Details have landed.
- Recent field changes reduced false-positive/protocol noise and added Live Nearby.
- Current focus: physical validation and remaining product/data-analysis work, not another visual rewrite.

## Product guardrails

1. Summary first, evidence second, raw data last.
2. Live values may update without causing avoidable list/layout instability.
3. Radar should answer: what is it, how recently/strongly is it visible, does it need attention?
4. Details explains the decision before showing Bluetooth internals.
5. RSSI is signal context, not exact distance.
6. Observation GPS is where the phone saw the signal, not exact device location.
7. Local deterministic logic remains authoritative offline; AI is optional.
8. UI work must not incidentally change accepted scanner/ingest semantics.

## Completed foundation

- [x] Compact stable Radar card structure; redundant technical/evidence dump removed from the list.
- [x] Lightweight Radar-specific data projection instead of full-device/full-evidence list loading.
- [x] Decision-first Details hierarchy and progressive disclosure.
- [x] Technical Details collapsed by default; raw data remains accessible deeper in Details.
- [x] Accepted dense-list/performance direction preserved.
- [x] Initial false-positive/protocol-noise grouping improvements from real field data.
- [x] Live Nearby signal-oriented view added.

Historical phase-by-phase acceptance reports were intentionally removed; concise provenance is in `HISTORY.md` and full details remain in Git history.

## U1 — Field quality and Radar usefulness

- [ ] Validate current `main` on Samsung S22+ after the latest noise/classification changes.
- [ ] Confirm active/in-range devices stay easy to find even when high-volume protocols are present.
- [ ] Verify Apple/Find Hub protocol traffic no longer floods useful Radar content.
- [ ] Record and reduce reproducible false positives without weakening evidence requirements globally.
- [ ] Verify 100+ controlled/recent-device behavior when a dense scenario is needed.
- [ ] Keep list geometry/recomposition/jank within the accepted direction while live values update.

**Gate:** a real dense/ordinary walk remains usable and attention groups are evidence-driven rather than dominated by protocol volume.

## U2 — Details / Tracking & Signal

- [ ] Verify the current first viewport explains device/attention state without reading raw Bluetooth fields.
- [ ] Keep RSSI history first-class with meaningful time context, current value and bounded aggregation/downsampling.
- [ ] Show movement/Follow-Me context only when it adds decision value.
- [ ] Keep secondary history/technical sections stable during live updates and scrolling.
- [ ] Prefer lazy/keyed composition where measurement shows it materially improves long Details surfaces.
- [ ] Validate dark mode and large font scale.

**Gate:** live Details remains readable/stable through a long session with both rich-evidence and low-evidence devices.

## U3 — Sightings map

- [x] Add per-device sightings only from location samples with accuracy metadata.
- [x] Cluster/aggregate observations instead of one marker per raw sample.
- [x] Clearly label semantics as phone observation locations.
- [x] Handle absent/poor GPS without implying precision.
- [x] Defer a global map destination until the per-device map proves useful.

Implementation policy:

- the Details map uses a lightweight per-device read model and never loads raw Bluetooth payloads for map rendering;
- coordinates must be finite/in-range and reported accuracy must be greater than 0 m and at most 100 m to be mapped;
- observations are reduced deterministically to at most 50 visible groups; each group is represented by a real observation rather than a synthetic device position;
- no route/polyline is drawn because phone observation points are not a tracker trajectory;
- map tiles load only after an explicit **Show map** action; stored sighting records remain local, while the displayed map area is requested from the configured third-party tile provider;
- a global map destination remains deferred.

**Gate:** implementation is complete; physical validation on S22+ remains required to confirm map readability, lifecycle behavior, poor-GPS handling and dense-sample interpretation.

## A1 — Parser/data hardening

- [ ] Build privacy-safe fixtures from confirmed field defects.
- [ ] Add malformed/truncated payload tests and representative supported protocol/vendor fixtures.
- [ ] Harden manufacturer/service/UUID/address-transition boundaries from reproducible evidence.
- [ ] Formalize GPS/data-quality policy where it affects analysis.

## A2 — Deterministic reducer

Reduce large scan sessions locally before any AI step:

- duplicate/noise reduction and time bucketing;
- identity candidates with coexistence safeguards;
- movement/encounter segmentation;
- RSSI/location summaries;
- representative evidence selection;
- contradictions and data-quality flags.

Output a stable, explainable analysis-candidate representation.

## A3 — Versioned Analysis Bundle

- [ ] Versioned candidate/session schema.
- [ ] Timeline/encounters, movement, RSSI statistics and location-quality summaries.
- [ ] Identity transitions, representative packets/features, local verdict and contradictions.
- [ ] Explicit privacy review; avoid unrestricted DB/raw-history upload.
- [ ] Deterministic serialization tests.

## A4 — Optional analyst and feedback loop

- [ ] Explicit opt-in; local detection remains fully functional when disabled.
- [ ] Keep AI assessment separate from local assessment.
- [ ] Structured response: assessment, confidence, supporting/counter evidence, unknowns/data-quality caveats.
- [ ] Turn confirmed field problems into privacy-safe fixtures and deterministic fixes where possible.
- [ ] Replay old regression datasets before accepting heuristic changes.

The continuous developer telemetry/feedback transport is specified separately in `TELEMETRY_AI_FEEDBACK_BRIDGE_PLAN.md`.

## Definition of done

This workstream is successful when normal users see a compact stable Radar and decision-first Details, field noise/false positives stay controlled, signal/location history is useful without overstating precision, and large sessions can be deterministically reduced to small explainable analysis inputs while the app remains fully local-first without AI.
