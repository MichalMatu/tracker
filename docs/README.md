# Tracker documentation

This directory contains **current sources of truth**, not a chronological work log. Detailed old phase audits, handoffs and acceptance reports were removed from the working tree on 2026-09-14; Git history remains the provenance source when one of them is genuinely needed.

## Current status

- `main` is the source of truth.
- Phase 3 scanner/ingest field reacceptance is **closed / accepted**.
- Radar/Details redesign, Live Nearby and the per-device sightings map have landed.
- A1 parser/data hardening, A2 Deterministic Analysis Reducer V1 and A3 Versioned Analysis Bundle V1 are closed on `main`.
- The optional Drive + Gmail T0 telemetry/AI developer bridge is implemented and accepted for debug use; the Analysis Bundle is not wired into it and T1+ automatic production telemetry remains intentionally blocked.
- The current implementation workstream is closed, and the STABLE_CORE S22+ physical runtime gate is **closed / accepted**. Manual GATT, Radar, live Details, Technical/Raw access and the good-GPS sightings-map lifecycle were verified on-device; poor/no-GPS fallback states were not naturally present in that session and remain software-covered.
- A4 optional analyst integration and T1+ production transport require a new explicit product/architecture decision before implementation.

## Closure checkpoint — 2026-10-07

- Accepted runtime/code baseline: `f5c4c8be4620c567497dc4cc77b41b08d8cf0a95`.
- STABLE_CORE physical acceptance on Samsung S22+ is closed: manual read-only GATT, Radar scanner→Room freshness/grouping/scroll health, active Details live updates, Technical/Raw access, good-GPS sightings-map semantics and repeated Show/Hide + Home/return lifecycle all passed without crash/ANR.
- The field session did not naturally contain poor/no-GPS targets; those fallback states were not synthesized and remain covered by deterministic software tests.
- Closure status head before this checkpoint: `de981f8dc2a5a4e9dfd21d74bacfcacb9cdd0b5f`. Compared with the accepted runtime SHA, its four intervening commits changed only `README.md`, `docs/README.md`, `docs/UI_UX_REDESIGN_PLAN.md` and `docs/HISTORY.md`.
- CI on `de981f8d...`: Quality #312 **success**, Secret Scan #346 **success**, Sandbox Pack #120 **success**, Tester Release #269 **success**.
- Remote branch state at checkpoint: only `main` plus infrastructure-only `agent-control`; no work branches remain and open pull requests = 0.
- No product/code follow-up is implicitly queued. A4 optional analyst work and T1+ production telemetry still require a separate explicit product/architecture decision.

## Read order

1. [PRODUCT_GOAL.md](PRODUCT_GOAL.md) — product purpose and claim boundaries.
2. The plan/reference relevant to the task:
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
