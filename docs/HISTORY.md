# Project history

Concise provenance only. Current work always starts from `docs/README.md` and the active plans.

## 2026-09

- **Stable scanner/ingest baseline:** recovery work established one scanner lifecycle owner, bounded ingest behavior, deterministic diagnostics and repeatable software/device gates.
- **2026-09-11 — Phase 3 field reacceptance closed:** final physical reacceptance accepted the stabilized scanner/ingest path and unblocked later product work.
- **2026-09-12 — Radar redesign/performance accepted:** compact stable Radar cards and a lightweight Radar-specific projection were accepted. The controlled 120-item device scenario passed; the final comparable physical benchmark recorded about 4.51% jank, p95 12 ms and p99 36 ms.
- **2026-09-12/13 — Details progressive disclosure:** decision-first Details and collapsed Technical Details landed while preserving raw/technical inspection.
- **2026-09-13 — field-noise corrections:** Follow-Me/public-safety false-positive handling and Find Hub/generic classification were tightened from real device evidence.
- **2026-09-14 — Radar noise grouping and Live Nearby:** high-volume protocol noise was grouped and a live-nearby signal view was added.
- **2026-09-14 — telemetry bridge T0 accepted:** the dedicated Google account path was proven end to end: Android authorization, `drive.file`, `gmail.send`, Drive JSON creation, self-addressed `BLUEEYE/BATCH_READY`, ChatGPT retrieval of the exact referenced Drive file, revoke, and reauthorization. The app now persists only the selected account identity and silently re-runs Google authorization after process restart; access tokens remain memory-only. Physical force-stop/relaunch verification confirmed the same account and both scopes return to `Granted` without another consent flow. Automatic T1+ telemetry remains blocked until a policy-compatible production transport/control plane is selected.

## 2026-10

- **2026-10-06 — U3 per-device sightings map merged:** Details gained a lazy MapLibre/OpenFreeMap sightings view backed by a lightweight per-device read model, strict GPS-quality filtering and deterministic clustering to real phone observation points.
- **2026-10-06 — Android UI smoke realigned with current Details UX:** stale Radar/Details selectors were replaced and the emulator smoke now runs on pull requests as well as `main`, catching UX drift before merge.
- **2026-10-06 — A1 parser/data hardening closed:** privacy-safe Lime/LBCAT-S-shaped regression coverage, 16-bit service-data boundary tests and advertisement-evidence malformed/truncation tests landed. Existing address-carryover tests cover coexistence-vs-rotation and identity guards; location-quality policy is aligned across Details sightings and deterministic analysis (>0 m and <=100 m finite accuracy, finite/in-range coordinates, poor/missing samples rejected or counted separately).
- **2026-10-06 — A2 deterministic reducer merged:** `DeterministicAnalysisReducer` now produces stable local analysis candidates with canonical ordering, duplicate reduction, fixed buckets, RSSI/location-quality summaries, movement/encounter summaries, identity summaries, bounded evidence, quality flags and contradictions.
- **2026-10-06 — A3 Analysis Bundle V1 merged:** a deterministic versioned JSON contract and pure builder now convert reduced candidates into privacy-bounded session bundles using aliases instead of raw identifiers; exact coordinates, raw payloads, free-form evidence text and active probe evidence are excluded by default. No production telemetry/AI transport was enabled.
- **2026-10-06 — current implementation baseline closed:** scanner/ingest remains accepted, Radar/Details/map implementation and A1-A3 are on `main`; A4 optional analyst integration and T1+ production telemetry require a new product/architecture decision.
- **2026-10-07 — STABLE_CORE S22+ physical acceptance closed:** manual GATT read/discovery/disconnect succeeded without characteristic writes; Radar scanner→Room freshness, protocol grouping and scroll/process health were verified; an active Details target received fresh signal samples with no crash/ANR; Technical/Raw access was physically confirmed; and the per-device good-GPS sightings map was verified with privacy wording, usable-observation/group counts, explicit Show/Hide behavior, repeated map toggling and Home/return lifecycle. Final foreground health reported app focus present, crash/ANR count zero and the process alive. The session contained no natural poor/no-GPS targets, so those fallback states remain covered by deterministic software tests rather than synthetic field telemetry.

Full historical phase reports/hand-offs remain available in repository history before the 2026-09-14 documentation cleanup. They are intentionally not current instructions.
