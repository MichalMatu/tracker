# Tracker documentation index

This index is the entry point for repository documentation. It distinguishes **active plans**, **current technical references**, **authoritative phase closure records**, and **historical provenance** so an old handoff cannot accidentally become the next action again.

## Read this first

For current work, use this order:

1. [`PRODUCT_GOAL.md`](PRODUCT_GOAL.md) — what the product is trying to achieve and what it must not claim.
2. [`UI_UX_REDESIGN_PLAN.md`](UI_UX_REDESIGN_PLAN.md) — **active execution checklist** for the current Radar/Details redesign and the following local-reducer / optional AI-analysis milestone.
3. [`ARCHITECTURE_CURRENT.md`](ARCHITECTURE_CURRENT.md) — current module/dependency picture.
4. [`QUALITY_GATE.md`](QUALITY_GATE.md) — repository acceptance gates.
5. [`SANDBOX_EXECUTION_FLOW.md`](SANDBOX_EXECUTION_FLOW.md) — where source, CI, sandbox and physical-device work belong.

## Current project status

- Phase 3 field reacceptance: **ACCEPTED / CLOSED**.
- Phase 4: **UNBLOCKED**.
- Final authoritative Phase 3 record: [`PHASE3_FIELD_REACCEPTANCE_CLOSURE_2026-09-11.md`](PHASE3_FIELD_REACCEPTANCE_CLOSURE_2026-09-11.md).
- Current product work: Radar + Details UX/performance redesign on `ui/radar-details-redesign`.
- Current accepted `main` baseline at plan creation: `093b4257859abdbcc683f1620969b06195ade7a6`.

Do not use an older Phase 3 handoff, pre-field golden manifest or recovery-guide `NEXT ACTION` as current instructions.

## Active plan

### [`UI_UX_REDESIGN_PLAN.md`](UI_UX_REDESIGN_PLAN.md)

Canonical checklist for:

- compact, stable Radar cards;
- Radar presentation/data-path performance;
- decision-first Details hierarchy;
- retained RSSI chart and Tracking & Signal view;
- lazy/stable layout behavior;
- collapsed Technical Peek;
- per-device Sightings Map;
- UX acceptance;
- subsequent parser hardening;
- deterministic local reduction;
- compact Analysis Bundle;
- optional AI Analyst mode;
- field-data feedback loop.

Update its checkboxes/gates as implementation progresses.

## Current technical references

- [`PRODUCT_GOAL.md`](PRODUCT_GOAL.md) — product scope, safety/claim boundaries and user-facing evidence standard.
- [`ARCHITECTURE_CURRENT.md`](ARCHITECTURE_CURRENT.md) — current architecture and dependency direction.
- [`PIPELINE_AUDIT.md`](PIPELINE_AUDIT.md) — detailed ingest/classification/persistence pipeline audit and technical debt.
- [`EVIDENCE_MODEL.md`](EVIDENCE_MODEL.md) — evidence representation and provenance model.
- [`DETECTION_CONFIDENCE.md`](DETECTION_CONFIDENCE.md) — confidence semantics.
- [`QUALITY_GATE.md`](QUALITY_GATE.md) — required quality checks.
- [`SANDBOX_EXECUTION_FLOW.md`](SANDBOX_EXECUTION_FLOW.md) — execution/tooling workflow.
- [`RELEASES_AND_ARTIFACTS.md`](RELEASES_AND_ARTIFACTS.md) — tester/release/artifact conventions.

## Phase 3 — authoritative closure and supporting evidence

### Authoritative closure

- [`PHASE3_FIELD_REACCEPTANCE_CLOSURE_2026-09-11.md`](PHASE3_FIELD_REACCEPTANCE_CLOSURE_2026-09-11.md) — **final source of truth for Phase 3 status**; Phase 3 closed, Phase 4 unblocked.

### Supporting records

- [`PHASE3_CAPTURED_DATA_ANALYSIS_2026-09-11.md`](PHASE3_CAPTURED_DATA_ANALYSIS_2026-09-11.md) — historical root-cause analysis of the real field dataset; blockers described there were fixed by PR #6 and reaccepted.
- [`PHASE3_FIELD_REACCEPTANCE_HANDOFF_2026-09-11.md`](PHASE3_FIELD_REACCEPTANCE_HANDOFF_2026-09-11.md) — final handoff updated to closed/unblocked state.
- [`PHASE3_FINAL_CODE_QUALITY_AUDIT_2026-09-11.md`](PHASE3_FINAL_CODE_QUALITY_AUDIT_2026-09-11.md) — final read-only code-quality evidence before field closure.

## Historical stabilization provenance

The files below are intentionally retained because they explain why specific code/tests exist. They are **not current task instructions**.

- [`PHASE2_CLOSURE_AUDIT.md`](PHASE2_CLOSURE_AUDIT.md) — Phase 2 closure record.
- [`STABLE_CORE_PREIMPLEMENTATION_AUDIT.md`](STABLE_CORE_PREIMPLEMENTATION_AUDIT.md) — early stable-core design audit.
- [`STABILITY_RECOVERY_GUIDE.md`](STABILITY_RECOVERY_GUIDE.md) — original recovery roadmap; its old `NEXT ACTION` is historical after final Phase 3 closure.
- [`PHASE3_PREIMPLEMENTATION_AUDIT.md`](PHASE3_PREIMPLEMENTATION_AUDIT.md) — ingest-accounting preimplementation audit.
- [`PHASE3_CODE_QUALITY_REVIEW.md`](PHASE3_CODE_QUALITY_REVIEW.md) — earlier structural review; superseded for final audit conclusions by the dated final code-quality audit.
- [`PHASE3_FIELD_COLLECTION.md`](PHASE3_FIELD_COLLECTION.md) — field-collection procedure/status from before final closure.
- [`PHASE3_PRE_FIELD_GOLDEN.md`](PHASE3_PRE_FIELD_GOLDEN.md) — immutable historical pre-field candidate manifest.
- [`PHASE3_TARGETED_PHYSICAL_ACCEPTANCE.md`](PHASE3_TARGETED_PHYSICAL_ACCEPTANCE.md) — earlier targeted physical acceptance evidence.
- [`PHASE3_HANDOFF.md`](PHASE3_HANDOFF.md) — historical new-chat handoff from the defect-discovery/recovery cycle.
- [`PHASE3_FINAL_HANDOFF_2026-09-11.md`](PHASE3_FINAL_HANDOFF_2026-09-11.md) — explicitly superseded pre-reacceptance handoff.
- [`PHASE3_CAPTURED_DATA_ANALYSIS_HANDOFF_2026-09-11.md`](PHASE3_CAPTURED_DATA_ANALYSIS_HANDOFF_2026-09-11.md) — superseded narrow ingest/storage closure; useful as provenance only.

## Documentation maintenance rules

- Keep exactly one clearly named **active execution plan** per major current workstream.
- When a handoff/plan is superseded, retain it only when it provides useful provenance and add an explicit historical/superseded banner.
- Final closure documents outrank earlier handoffs and interim status text.
- Never commit private field telemetry: exact GPS, MAC addresses, raw HCI/bugreport/Room databases or other sensitive captures.
- Update this index when a new canonical plan or final closure is created.
- Prefer linking historical records from here instead of keeping stale `NEXT ACTION` instructions in the root `README.md`.
