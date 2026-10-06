# UI/UX and analysis plan

## Status

- **Current implementation slice is closed on `main`.**
- Scanner/ingest Phase 3 baseline is accepted; do not reopen it without a concrete regression.
- Compact Radar cards, lightweight Radar projection, decision-first Details, collapsed Technical Details, Live Nearby and the per-device sightings map have landed.
- A1 parser/data hardening, A2 Deterministic Analysis Reducer V1 and A3 Versioned Analysis Bundle V1 are implemented.
- U1/U2/U3 physical S22+ acceptance is **closed** for the STABLE_CORE baseline from real-device evidence collected on 2026-10-07. The session physically covered Radar, live Details, Technical/Raw access and the good-GPS sightings-map lifecycle; poor/no-GPS fallback states were not naturally present and remain covered by deterministic tests.
- A4 is future optional analyst work and requires a new explicit product decision; it is not an active implementation queue.

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

## Physical validation — U1 Radar

Accepted on Samsung S22+ for the STABLE_CORE baseline. Real-device evidence covered:

- validate current `main` after the latest noise/classification changes;
- confirm active/in-range devices stay easy to find amid high-volume protocols;
- verify Apple/Find Hub traffic does not flood useful Radar content;
- capture any reproducible false positives before changing heuristics;
- repeat a dense 100+ scenario only when a comparable field run is needed;
- confirm list geometry/recomposition/jank remains within the accepted direction.

**Runtime gate:** **accepted.** Scanner→Room freshness, protocol-noise grouping, ordinary Radar usability and clean scroll/process health were verified on-device. No reproducible product regression was found.

## Physical validation — U2 Details / Tracking & Signal

Accepted on Samsung S22+ for the STABLE_CORE baseline. Real-device evidence covered:

- the first viewport explains device/attention state without relying on raw Bluetooth fields;
- RSSI history remains readable with meaningful time context;
- movement/Follow-Me context appears only when it adds decision value;
- secondary history/technical sections remain stable during live updates and scrolling;
- dark mode and large font scale remain usable on-device.

**Runtime gate:** **accepted for the exercised field session.** Active Details received fresh signal samples, remained process-stable, and preserved Technical/Raw access. The field session did not naturally provide every low-evidence variant.

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

**Implementation gate:** complete. **Physical S22+ runtime acceptance is closed for the STABLE_CORE baseline:** good-GPS observations, privacy copy, explicit Show/Hide map behavior, third-party-tile disclosure, repeated map toggling and Home/return lifecycle were verified on-device. The session contained no natural poor/no-GPS records, so those fallback states remain covered by deterministic software tests rather than synthetic field telemetry.

## A1 — Parser/data hardening

- [x] Build privacy-safe fixtures from confirmed field defects.
- [x] Add malformed/truncated payload tests and representative supported protocol/vendor fixtures.
- [x] Harden manufacturer/service/UUID/address-transition boundaries from reproducible evidence.
- [x] Formalize GPS/data-quality policy where it affects analysis.

Accepted evidence on `main`:

- privacy-safe legacy Lime/LBCAT-S-shaped scan fixtures cover the confirmed 59-byte advertisement/scan-response layout without retaining field identifiers;
- `ServiceDataExtractor` tests cover valid 16-bit service data, empty payloads and truncated AD structures;
- `AdvertisementEvidenceParser` tests prevent reserved/truncated raw records from fabricating Appearance, manufacturer or service-UUID evidence while preserving representative valid structured evidence;
- address-carryover tests cover coexistence-vs-rotation timing, corroborated sequential carryover, long-gap candidate-only behavior and Apple family/shadow conflict guards;
- location policy is deterministic in both Details sightings and analysis reduction: coordinates must be finite/in-range, accuracy must be finite, greater than 0 m and at most 100 m, poor/missing samples are rejected or counted separately, and analysis output does not expose exact coordinates.

**Gate:** A1 is closed. Future parser/heuristic changes must still begin from new reproducible privacy-safe field evidence rather than reopening this baseline speculatively.

## A2 — Deterministic reducer

- [x] Canonical sorting, duplicate/noise reduction and fixed time bucketing.
- [x] RSSI and location-quality summaries without exact GPS in the output.
- [x] Movement/encounter segmentation and Follow-Me history reduction.
- [x] Identity candidate summaries with coexistence safeguards.
- [x] Bounded representative evidence, quality flags and contradictions.
- [x] Pure `DeterministicAnalysisReducer` in `core:domain`; no DB, Android API or system clock dependency.
- [x] Permutation, duplicate, bucket-boundary, coexistence, RSSI, poor/no-GPS, Follow-Me, evidence and contradiction tests.

**Gate:** A2 is implemented and merged. The reducer produces a stable, explainable `AnalysisCandidate` representation and does not change accepted scanner/ingest behavior.

## A3 — Versioned Analysis Bundle

- [x] Versioned V1 candidate/session schema in `core:model`.
- [x] Timeline/encounters, movement, RSSI statistics and location-quality summaries.
- [x] Identity summaries, local verdict, representative structured evidence, quality flags and contradictions.
- [x] Deterministic session-scoped aliases instead of raw device/identity fingerprints.
- [x] Exact coordinates, hardware addresses, raw payloads and free-form evidence text excluded from the bundle.
- [x] Active GATT/RFCOMM probe evidence excluded by default and counted as omitted.
- [x] Deterministic JSON serialization/round-trip and privacy-boundary tests.
- [x] Pure `AnalysisBundleBuilder`; no `DatabaseExporter`, Drive/Gmail or T1+ transport integration.

**Gate:** A3 is implemented and merged. `AnalysisBundleV1` is a local, bounded contract ready for future optional analyst integration; T0 remains debug-only and T1+ telemetry remains blocked.

## A4 — Future optional analyst and feedback loop

Not active until an explicit product decision opens this workstream. The intended guardrails remain:

- explicit opt-in; local detection remains fully functional when disabled;
- AI assessment stays separate from local assessment;
- structured response includes assessment, confidence, supporting/counter evidence and unknowns/data-quality caveats;
- confirmed field problems become privacy-safe fixtures and deterministic fixes where possible;
- old regression datasets are replayed before heuristic changes are accepted.

The continuous developer telemetry/feedback transport is specified separately in `TELEMETRY_AI_FEEDBACK_BRIDGE_PLAN.md`.

## Closure

The current implementation baseline is closed on `main`: compact Radar, decision-first Details, sightings-map implementation, parser/data hardening and deterministic A2/A3 analysis are in place while scanner/ingest semantics remain accepted and untouched. STABLE_CORE physical S22+ acceptance is also closed. Future analyst/production-telemetry work requires a new decision and is not implicitly queued.
