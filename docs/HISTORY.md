# Project history

Concise provenance only. Current work always starts from `docs/README.md` and the active plans.

## 2026-09

- **Stable scanner/ingest baseline:** recovery work established one scanner lifecycle owner, bounded ingest behavior, deterministic diagnostics and repeatable software/device gates.
- **2026-09-11 — Phase 3 field reacceptance closed:** final physical reacceptance accepted the stabilized scanner/ingest path and unblocked later product work.
- **2026-09-12 — Radar redesign/performance accepted:** compact stable Radar cards and a lightweight Radar-specific projection were accepted. The controlled 120-item device scenario passed; the final comparable physical benchmark recorded about 4.51% jank, p95 12 ms and p99 36 ms.
- **2026-09-12/13 — Details progressive disclosure:** decision-first Details and collapsed Technical Details landed while preserving raw/technical inspection.
- **2026-09-13 — field-noise corrections:** Follow-Me/public-safety false-positive handling and Find Hub/generic classification were tightened from real device evidence.
- **2026-09-14 — Radar noise grouping and Live Nearby:** high-volume protocol noise was grouped and a live-nearby signal view was added.
- **2026-09-14 — telemetry bridge T0 accepted:** the dedicated Google account path was proven end to end: Android authorization, `drive.file`, `gmail.send`, Drive JSON creation, self-addressed `BLUEEYE/BATCH_READY`, ChatGPT retrieval of the exact referenced Drive file, revoke, and reauthorization. The app now also persists only the selected account identity and silently re-runs Google authorization after process restart; access tokens remain memory-only. Automatic T1+ telemetry remains blocked until a policy-compatible production transport/control plane is selected.

Full historical phase reports/hand-offs remain available in repository history before the 2026-09-14 documentation cleanup. They are intentionally not current instructions.
