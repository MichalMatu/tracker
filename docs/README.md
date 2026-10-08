# Tracker documentation

This directory contains current engineering/product contracts, not a chronological work log. Old phase audits, handoffs and completed plans belong in Git history; durable milestones stay in `HISTORY.md`.

## Current baseline

- `main` is the source of truth.
- Scanner/ingest, Radar/Details/Live Nearby, the per-device sightings map and A1-A3 deterministic analysis are accepted.
- Explicit automatic GATT collection is available only by opt-in, is read-oriented and remains sequential; RFCOMM and opportunistic Classic discovery stay disabled.
- The 2026-10-08 field-regression closeout separates physical device type, protocol capability and tracking risk; fixes FHN identity carryover, Samsung Q60/SmartTag confusion, GPS movement quality and RSSI-dominated Follow-Me scoring.
- Optional analyst integration and production telemetry are not active workstreams. Reopening either requires a new explicit product/architecture decision.

Owner-confirmed controlled field devices and the privacy-safe regression expectations derived from them are recorded in [DETECTION_MODEL.md](DETECTION_MODEL.md).

## Read order

1. [PRODUCT_GOAL.md](PRODUCT_GOAL.md) — product purpose and claim boundaries.
2. [ARCHITECTURE_CURRENT.md](ARCHITECTURE_CURRENT.md) — current modules, runtime boundaries and data flow.
3. [DETECTION_MODEL.md](DETECTION_MODEL.md) — identity/classification/risk semantics and field regression anchors.
4. [QUALITY_GATE.md](QUALITY_GATE.md) — verification requirements.
5. [SANDBOX_EXECUTION_FLOW.md](SANDBOX_EXECUTION_FLOW.md) — where engineering work runs.
6. [HISTORY.md](HISTORY.md) — concise milestone provenance only.

Physical field work also uses [`../FIELD_SESSION_CHECKLIST.md`](../FIELD_SESSION_CHECKLIST.md).

## Documentation policy

- Update an existing canonical document instead of creating dated handoff/closure/audit/status files.
- Completed work becomes a short durable status/history note, not a permanent plan.
- Put detailed reproducible evidence in tests, CI artifacts, issues/PRs or Git history.
- Keep private field telemetry, exact GPS, MAC addresses, device serials and transient network endpoints out of the repository.
- If a document stops being an active contract/reference, merge its durable content into a current file and delete it.
