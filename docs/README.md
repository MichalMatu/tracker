# Tracker documentation

This directory contains **current sources of truth**, not a chronological work log. Detailed old phase audits, handoffs and acceptance reports were removed from the working tree on 2026-09-14; Git history remains the provenance source when one of them is genuinely needed.

## Current status

- `main` is the source of truth.
- Phase 3 scanner/ingest field reacceptance is **closed / accepted**.
- Radar/Details redesign, Live Nearby and the per-device sightings map have landed.
- A1 parser/data hardening, A2 Deterministic Analysis Reducer V1 and A3 Versioned Analysis Bundle V1 are closed on `main`.
- The optional Drive + Gmail T0 telemetry/AI developer bridge is implemented and accepted for debug use; the Analysis Bundle is not wired into it and T1+ automatic production telemetry remains intentionally blocked.
- The STABLE_CORE S22+ physical runtime gate is **closed / accepted** for Radar, live Details, Technical/Raw access, manual GATT and the good-GPS sightings-map lifecycle; poor/no-GPS fallback states remain software-covered because they were not naturally present in that session.
- Explicit opt-in automatic GATT collection is re-enabled for field testing and physically accepted in its sequential form: one connectable device at a time, read-only GATT discovery/reads, 12 s per-probe timeout and 15 min per-device cooldown. New devices are not blocked by another device's cooldown.
- A4 optional analyst integration and T1+ production transport require a new explicit product/architecture decision before implementation.

## Field-test checkpoint — 2026-10-07

- Runtime/code baseline physically validated on Samsung S22+: `1348106d7a4ba867cef9a50d3819db098a204149`.
- Exact-SHA CI on that runtime baseline is green: Quality #315, Secret Scan #349, Sandbox Pack #123, Android UI Smoke #84 and Tester Release #272.
- Sequential automatic GATT is accepted for field testing. The physical run persisted 22 recently probed devices; 14 had GATT services, 19 had characteristic data and 10 exposed additional standard fields. Six probes recorded errors, which are retained as probe outcomes rather than hidden.
- The active-probe timestamps were distinct and serialized (minimum observed gap about 6.8 s, median about 16.1 s), consistent with the one-device-at-a-time queue. The 15 min cooldown is per device/fingerprint; a newly seen eligible device can enter the queue immediately.
- Passive scanning remained healthy during the active-GATT run: the quiescent snapshot contained 502 signal samples from the preceding 2 minutes, the database passed `PRAGMA quick_check=ok`, the app process remained alive and no crash/ANR was observed.
- The master-switch preference `auto_active_probe_enabled=true` persisted across the controlled stop/restart check. Automatic RFCOMM and opportunistic Classic discovery remain disabled.
- Repository hygiene at checkpoint: only `main` plus infrastructure-only `agent-control`; open pull requests = 0; pending Local Agent tasks = 0.
- No analyst/production-telemetry work is implicitly queued. A4 and T1+ still require a separate explicit product/architecture decision.

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
