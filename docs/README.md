# Tracker documentation index

This index is the entry point for repository documentation. It distinguishes active plans, current technical references, authoritative phase closure records, and historical provenance so an old handoff cannot accidentally become the next action again.

## Read this first

For current work, use this order:

1. [`PRODUCT_GOAL.md`](PRODUCT_GOAL.md) — what the product is trying to achieve and what it must not claim.
2. [`UI_UX_REDESIGN_PLAN.md`](UI_UX_REDESIGN_PLAN.md) — active execution checklist for the current Radar/Details redesign and later analysis work.
3. [`TELEMETRY_AI_FEEDBACK_BRIDGE_PLAN.md`](TELEMETRY_AI_FEEDBACK_BRIDGE_PLAN.md) — active implementation plan for the optional telemetry, trigger, ChatGPT analysis and operator feedback bridge.
4. [`UI_UX_NEXT_WALK_HANDOFF_2026-09-13.md`](UI_UX_NEXT_WALK_HANDOFF_2026-09-13.md) — current continuation handoff for the next S22+ field/UX walk.
5. [`ARCHITECTURE_CURRENT.md`](ARCHITECTURE_CURRENT.md) — current module/dependency picture.
6. [`QUALITY_GATE.md`](QUALITY_GATE.md) — repository acceptance gates.
7. [`SANDBOX_EXECUTION_FLOW.md`](SANDBOX_EXECUTION_FLOW.md) — where source, CI, sandbox and physical-device work belong.

## Current project status

- Phase 3 field reacceptance: **ACCEPTED / CLOSED**.
- Radar/Details UI redesign: implementation landed on `main`; next focus is physical S22+ revalidation.
- Technical Details progressive disclosure: **implemented**; technical content is collapsed by default and expands explicitly.
- `Raw Data` remains available from the expanded Technical Details section rather than the top app bar.
- Previous accepted Radar performance work remains preserved; do not reopen without a concrete regression.
- Telemetry/AI feedback bridge: **ACTIVE PLAN**, implementation not yet started; first slice is a dedicated screen plus secondary Google account and send-only test email.
- Next action: install the current `main` build on Samsung SM-S906B, run the next controlled field/UX walk, and capture only the evidence needed for UI/field acceptance.

Do not use older Phase 2 handoffs as current execution instructions. The dated next-walk handoff below is now the current continuation record.

## Active plans

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

### [`TELEMETRY_AI_FEEDBACK_BRIDGE_PLAN.md`](TELEMETRY_AI_FEEDBACK_BRIDGE_PLAN.md)

Canonical implementation plan for the optional continuous analysis bridge:

- dedicated secondary Google account;
- least-privilege send-only Gmail authorization;
- dedicated `Telemetry & AI Bridge` screen and ViewModel;
- compact versioned telemetry deltas and checkpoints;
- durable local outbox and replaceable HTTPS transport;
- Gmail as trigger/control and operator-feedback channel, not telemetry storage;
- event-trigger validation with scheduler watchdog fallback;
- ChatGPT analysis, Gmail feedback and traceable GitHub/Local Agent engineering loop;
- privacy, retention, battery and productization gates.

Keep this work isolated from normal local scanning and alerts; disabling it must leave Tracker fully functional.

## Current handoff

### [`UI_UX_NEXT_WALK_HANDOFF_2026-09-13.md`](UI_UX_NEXT_WALK_HANDOFF_2026-09-13.md)

Use this when opening the next ChatGPT window. It records:

- the current `main` source checkpoint;
- the Local Agent repository binding;
- the installed-device target and verification steps;
- the current Details/Technical Details UI state;
- the exact next field-session goals;
- privacy rules for captured evidence.

## Current technical references

- [`PRODUCT_GOAL.md`](PRODUCT_GOAL.md) — product scope, safety/claim boundaries and user-facing evidence standard.
- [`ARCHITECTURE_CURRENT.md`](ARCHITECTURE_CURRENT.md) — current architecture and dependency direction.
- [`PIPELINE_AUDIT.md`](PIPELINE_AUDIT.md) — detailed ingest/classification/persistence pipeline audit and technical debt.
- [`EVIDENCE_MODEL.md`](EVIDENCE_MODEL.md) — evidence representation and provenance model.
- [`DETECTION_CONFIDENCE.md`](DETECTION_CONFIDENCE.md) — confidence semantics.
- [`QUALITY_GATE.md`](QUALITY_GATE.md) — required quality checks.
- [`SANDBOX_EXECUTION_FLOW.md`](SANDBOX_EXECUTION_FLOW.md) — execution/tooling workflow.
- [`RELEASES_AND_ARTIFACTS.md`](RELEASES_AND_ARTIFACTS.md) — tester/release/artifact conventions.
- [`FIELD_SESSION_CHECKLIST.md`](../FIELD_SESSION_CHECKLIST.md) — repeatable field-session procedure and privacy constraints.

## Phase 3 — authoritative closure and supporting evidence

### Authoritative closure

- [`PHASE3_FIELD_REACCEPTANCE_CLOSURE_2026-09-11.md`](PHASE3_FIELD_REACCEPTANCE_CLOSURE_2026-09-11.md) — final source of truth for Phase 3 status; Phase 3 closed, Phase 4 unblocked.

### Supporting records

- [`PHASE3_CAPTURED_DATA_ANALYSIS_2026-09-11.md`](PHASE3_CAPTURED_DATA_ANALYSIS_2026-09-11.md) — historical root-cause analysis of the real field dataset.
- [`PHASE3_FIELD_REACCEPTANCE_HANDOFF_2026-09-11.md`](PHASE3_FIELD_REACCEPTANCE_HANDOFF_2026-09-11.md) — final handoff updated to closed/unblocked state.
- [`PHASE3_FINAL_CODE_QUALITY_AUDIT_2026-09-11.md`](PHASE3_FINAL_CODE_QUALITY_AUDIT_2026-09-11.md) — final read-only code-quality evidence before field closure.

## Historical stabilization provenance

The older Phase 1–3 handoffs and recovery documents remain useful provenance but are not current task instructions.

## Documentation maintenance rules

- Keep exactly one clearly named active execution plan per major current workstream.
- Keep one clearly named current continuation handoff when work is expected to move across chat windows.
- When a handoff/plan is superseded, retain it only when it provides useful provenance and add an explicit historical/superseded banner.
- Final closure documents outrank earlier handoffs and interim status text.
- Never commit private field telemetry: exact GPS, MAC addresses, raw HCI/bugreport/Room databases or other sensitive captures.
- Update this index when a new canonical plan, current handoff or final closure is created.
