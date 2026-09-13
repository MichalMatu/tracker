#!/bin/bash
set -euo pipefail

expected_sha="11ab0f3b38c234477c3b8eb5a053710c9c32e059"
expected_main="565821f9dd46450a324907523c9ce1ad49349d75"
branch="chore/preproduction-readiness-20260913"

test "$(git rev-parse HEAD)" = "$expected_sha"
test "$(git rev-parse origin/$branch)" = "$expected_sha"
test "$(git rev-parse origin/main)" = "$expected_main"
test -z "$(git status --porcelain)"

python3 - <<'PY'
from pathlib import Path

plan = Path("docs/UI_UX_REDESIGN_PLAN.md")
text = plan.read_text()
repls = {
    "- Working branch: `ui/radar-details-redesign`": "- UX redesign branch: merged into `main` via PR #7; no active UX implementation branch",
    "- Baseline `main`: `093b4257859abdbcc683f1620969b06195ade7a6`": "- Baseline `main` at plan creation: `093b4257859abdbcc683f1620969b06195ade7a6`",
    "- Phase 4: **IN PROGRESS**": "- Phase 4: **PENDING / NOT STARTED**",
    "- Current focus: **Phase 3 Details redesign**": "- Current focus: **production-readiness hardening; Phase 4 is next product work**",
    "- Current continuation handoff: [`UI_UX_PHASE2_HANDOFF_2026-09-12.md`](UI_UX_PHASE2_HANDOFF_2026-09-12.md).": "- Historical Phase 2 handoff: [`UI_UX_PHASE2_HANDOFF_2026-09-12.md`](UI_UX_PHASE2_HANDOFF_2026-09-12.md); P2C is complete and this handoff is no longer a continuation source.",
}
for old, new in repls.items():
    if old not in text:
        raise SystemExit(f"missing expected plan text: {old}")
    text = text.replace(old, new, 1)

start = text.index("## Phase 3 — Rebuild Details around information priority")
end = text.index("## Phase 4 — Tracking & Signal", start)
phase3 = text[start:end]
phase3 = phase3.replace("- [ ] ", "- [x] ")
old_gate = "**Gate:** the first viewport explains the device and its risk/context without requiring the user to parse technical Bluetooth fields."
if old_gate not in phase3:
    raise SystemExit("missing Phase 3 gate")
new_gate = """**Status: CLOSED / ACCEPTED.** Accepted implementation checkpoint: `e1e09df50caf7cf9939420d42bd48bfb21ea6823`, merged to `main` by PR #7. Physical S22+ acceptance verified the decision-first first viewport, RSSI/last-seen context, Identity/Actions/History reachability, Technical details placement and Raw Data one level deeper. The final Raw Data gate had zero fatal exceptions, OOMs or ANRs; the broader physical audit recorded 0.78% jank, p95 10 ms, p99 17 ms and database integrity `ok`.

**Gate: PASS.** The first viewport explains the device and its risk/context without requiring the user to parse technical Bluetooth fields."""
phase3 = phase3.replace(old_gate, new_gate, 1)
text = text[:start] + phase3 + text[end:]
plan.write_text(text)

banners = {
    "docs/UI_UX_PHASE2_HANDOFF_2026-09-12.md": "> **HISTORICAL / SUPERSEDED:** UI/UX Phase 2 is closed and PR #7 has been merged. Do not use this file's NEXT ACTION as current work. See `docs/README.md` and `docs/PRODUCTION_READINESS.md`.\n\n",
    "docs/PHASE3_HANDOFF.md": "> **HISTORICAL / SUPERSEDED:** Phase 3 recovery/field work is closed. This handoff is provenance only; see `docs/README.md` for current work.\n\n",
    "docs/PHASE3_FINAL_HANDOFF_2026-09-11.md": "> **HISTORICAL / SUPERSEDED:** This pre-reacceptance handoff is retained for provenance only. Phase 3 is closed; see `docs/README.md`.\n\n",
    "docs/PHASE3_CAPTURED_DATA_ANALYSIS_HANDOFF_2026-09-11.md": "> **HISTORICAL / SUPERSEDED:** This narrow analysis handoff is retained for provenance only. Its follow-up work is complete; see `docs/README.md`.\n\n",
    "docs/PHASE3_FIELD_REACCEPTANCE_HANDOFF_2026-09-11.md": "> **HISTORICAL / CLOSED:** Field reacceptance completed successfully. This handoff is not a current continuation source; see `docs/README.md`.\n\n",
}
for name, banner in banners.items():
    p = Path(name)
    body = p.read_text()
    if not body.startswith("> **HISTORICAL"):
        p.write_text(banner + body)
PY

# Documentation integrity checks.
git diff --check

grep -Fq 'Phase 4: **PENDING / NOT STARTED**' docs/UI_UX_REDESIGN_PLAN.md
grep -Fq '**Status: CLOSED / ACCEPTED.** Accepted implementation checkpoint' docs/UI_UX_REDESIGN_PLAN.md
if sed -n '/## Phase 3 — Rebuild Details around information priority/,/## Phase 4 — Tracking & Signal/p' docs/UI_UX_REDESIGN_PLAN.md | grep -Fq -- '- [ ] '; then
  echo "Phase 3 still contains unchecked items" >&2
  exit 1
fi

echo PREPROD_V60_DOC_SYNC_OK
