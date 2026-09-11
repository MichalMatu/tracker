# Captured field-data analysis handoff — 2026-09-11

Status: **SUPERSEDED BY FIELD-DATA FINDINGS; PHASE 3 REOPENED; PHASE 4 BLOCKED**

The initial version of this handoff was written immediately after the low-level ingest/storage reacceptance and stated that Phase 3 was closed. Subsequent analysis of the actual `15,201` captured signal samples found end-to-end blockers, so that phase-boundary statement is no longer valid.

The authoritative current analysis is:

`docs/PHASE3_CAPTURED_DATA_ANALYSIS_2026-09-11.md`

The earlier narrow closure remains useful evidence for the healthy queue/processing/Room path, but its final Phase 3 verdict is superseded.

## Current state

- ingest accounting, processing and Room integrity: **PASS**;
- continuous screen-off BLE observation: **FAIL / BLOCKER**;
- Follow-Me movement/duration semantics: **FAIL / BLOCKER**;
- crowded Apple identity correlation: **FAIL / BLOCKER**;
- Phase 3: **REOPENED**;
- Phase 4: **BLOCKED** until bounded fixes and targeted reacceptance.

No application code has been changed as part of this analysis.

## Evidence source

Private checkpoint:

`~/agent-workspace/repos/tracker/checkpoints/phase3-field-reacceptance-20260911/20260911T142824Z`

Primary export SHA-256:

`4eb23f6f0ee24220b65f7555e412dea78042d0ea5ab1685f537fbe0784ef6224`

Do not commit raw GPS, MAC addresses, advertising payloads, Room files or HCI captures.

## Continuation

Continue with `PHASE3_CAPTURED_DATA_ANALYSIS_2026-09-11.md`. Before changing code, finish review of the two unresolved Eddystone candidates and the HCI timestamp semantics. Then implement only the bounded fixes identified by the field data:

1. background-safe BLE scanning;
2. observed/current movement duration instead of wall-clock `lastSeen-firstSeen` under a permanently latched movement flag;
3. an identity-concurrency guard / restricted Apple shadow carryover.

Do not perform a broad refactor or another large field walk before the targeted fixes are understood and tested.