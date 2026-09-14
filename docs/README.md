# Tracker documentation

This directory contains **current sources of truth**, not a chronological work log. Detailed old phase audits, handoffs and acceptance reports were removed from the working tree on 2026-09-14; Git history remains the provenance source when one of them is genuinely needed.

## Current status

- `main` is the source of truth.
- Phase 3 scanner/ingest field reacceptance is **closed / accepted**.
- Radar/Details redesign has landed; current work is field validation, noise/false-positive reduction and remaining UX/analysis follow-up.
- The optional Drive + Gmail telemetry/AI bridge is planned but not yet an application dependency.

## Read order

1. [PRODUCT_GOAL.md](PRODUCT_GOAL.md) — product purpose and claim boundaries.
2. The active plan relevant to the task:
   - [UI_UX_REDESIGN_PLAN.md](UI_UX_REDESIGN_PLAN.md)
   - [TELEMETRY_AI_FEEDBACK_BRIDGE_PLAN.md](TELEMETRY_AI_FEEDBACK_BRIDGE_PLAN.md)
3. [ARCHITECTURE_CURRENT.md](ARCHITECTURE_CURRENT.md) — current modules/data flow/debt.
4. [DETECTION_MODEL.md](DETECTION_MODEL.md) — evidence, confidence and provenance contract.
5. [QUALITY_GATE.md](QUALITY_GATE.md) — verification requirements.
6. [SANDBOX_EXECUTION_FLOW.md](SANDBOX_EXECUTION_FLOW.md) — where engineering work runs.
7. [HISTORY.md](HISTORY.md) — concise milestone provenance only.

Physical field work also uses [`../FIELD_SESSION_CHECKLIST.md`](../FIELD_SESSION_CHECKLIST.md).

## Documentation policy

- Update an existing canonical document instead of creating a dated `HANDOFF`, `CLOSURE`, `AUDIT`, `GOLDEN` or status-report file.
- A completed plan item should become a short durable status/history note, not another permanent report.
- Put detailed reproducible evidence in tests, CI artifacts, issues/PRs or Git history as appropriate.
- Keep private field telemetry out of the repository.
- If a document stops being an active contract/reference, merge its durable content into a current file and delete it.
