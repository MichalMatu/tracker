#!/usr/bin/env bash
set -euo pipefail
EXPECTED='745fdf30271459a20e380763ea69b8ed2e601839'

git fetch --no-tags origin main >/dev/null
HEAD="$(git rev-parse origin/main)"
echo "source_head=$HEAD"
test "$HEAD" = "$EXPECTED"
test -z "$(git status --porcelain)"
git checkout main >/dev/null 2>&1
git pull --ff-only origin main >/dev/null

python3 - <<'PY'
from pathlib import Path

sha = "745fdf30271459a20e380763ea69b8ed2e601839"
cert = "fff1ada9aa9e2709b4e4bda8333404feae854372df319b77f1f45d7869a61cb6"
apk = "563d5a068c112983eebdfab1661396e407a2ec15ca81ed603d70bc235dc69b63"

acceptance = f'''# Phase 3 Targeted Physical Acceptance — 2026-09-10

Status: **PASS for targeted physical regressions and ingest accounting; varied-density field walk still pending**

This document records the physical evidence collected after the Phase 3 software fixes. It closes the named regressions below but does **not** by itself close Phase 3 or authorize Phase 4. The remaining gate is one varied-density real-device walk with preserved Session Export JSON plus Room/WAL/SHM continuity evidence.

## Exact accepted app build

- Source commit: `{sha}` (`Reduce live stats and signal history load`)
- Device: Samsung SM-S906B, Android 16 / SDK 36
- Installed tester APK SHA-256: `{apk}`
- Tester signing certificate SHA-256: `{cert}`
- Incremental `adb install -r`: PASS
- Database before/after install: `PRAGMA integrity_check = ok`; `devices=39`, `signal_samples=21600`, `follow_me_observations=236` unchanged

The source commit passed the repository sandbox tests plus exact-SHA GitHub Quality #79, Secret Scan #109, Sandbox Pack #39, Android UI Smoke #20 and Tester Release #37.

## Targeted physical regressions

### Settings performance while Radar is running — PASS

The previously reproduced symptom was that `Database & Updates` became sluggish while Radar was scanning and became responsive immediately after Radar stopped. The accepted build decouples heavy session statistics snapshots from every Room invalidation and bounds refresh work. Manual S22+ validation confirmed that the screen remains responsive while Radar is actively scanning.

### Large Share / export OOM — PASS

The earlier physical crash was a `java.lang.OutOfMemoryError` caused by constructing a second near-full export string while appending diagnostics. The accepted build uses the streamed Share path. Manual S22+ validation successfully created and shared the large JSON export with `21704` signal samples; no Share crash occurred.

### Signal-sample write reduction — PASS

The accepted policy keys ordinary sample throttling by canonical fingerprint, uses a sparse heartbeat, and still writes immediately for meaningful changes. Tactical sampling remains independently denser.

The exported process-lifetime diagnostics reconcile exactly:

- raw BLE callbacks: `443`
- enqueue accepted: `417`
- coalesced: `26`
- enqueue rejected: `0`
- queue dropped: `0`
- processing started/succeeded/failed: `417 / 417 / 0`
- persisted device updates / throttled: `119 / 298` (`119 + 298 = 417`)
- signal samples written / throttled / failed: `107 / 310 / 0` (`107 + 310 = 417`)
- final queue depth: `0`
- queue high-water mark: `9`
- max queue wait: `130 ms`
- max processing duration: `118 ms`

Therefore `443 = 417 accepted + 26 coalesced + 0 rejected`, with no unexplained queue loss, and every accepted processed event has an explicit persistence/sample outcome.

Because the export was assembled while scanning was still active, top-level database counts and the later diagnostics append are not guaranteed to be the same instant. Use the process-lifetime ingest counters above for exact accounting rather than subtracting a pre-install database count from the export's top-level sample count.

## Passive BLE watchdog evidence already accepted

Earlier physical evidence at source `c1577ff92a0deb6bf14c29d0320d53f3aee87632` proved the Samsung Android 16 passive-scan timeout workaround across more than 430 seconds with the same app PID/service. Refresh count was `0` at 60/120/180 seconds, `1` at 240/300/330/390 seconds and `2` at 430 seconds. The harness failed only after the intended soak evidence because its final export/accounting stage did not complete. Do not repeat this long soak unless scanner source/lifecycle/watchdog behavior changes.

## Remaining Phase 3 gate

One varied-density real-device walk is still required. Preserve the process-lifetime Session Export JSON before killing the process, then collect Room plus WAL/SHM and verify continuity across the walk. Do not retest the already accepted long watchdog, Settings performance or large Share regressions unless relevant code changes.
'''
Path('docs/PHASE3_TARGETED_PHYSICAL_ACCEPTANCE.md').write_text(acceptance, encoding='utf-8')

p = Path('docs/STABILITY_RECOVERY_GUIDE.md')
s = p.read_text(encoding='utf-8')
old_next = '- [ ] Install the immutable pre-field tester `v1.0.0-phase3-pre-field.1` from README, launch it on the phone and connect USB/ADB. Capture exact installed-build identity plus a non-destructive BlueEye logcat/runtime baseline, then run `docs/PHASE3_FIELD_COLLECTION.md`, preserve Session Export JSON and Room/WAL/SHM, reconcile the counters and close Phase 3 before starting Phase 4.'
new_next = f'- [ ] Complete one varied-density real-device walk using the physically accepted app build from source commit `{sha}`. Preserve Session Export JSON plus Room/WAL/SHM and reconcile continuity across the walk. Targeted ingest accounting, Settings performance, large Share/OOM and >430 s passive-BLE watchdog evidence are already accepted; do not repeat them unless relevant code changes.'
assert old_next in s
s = s.replace(old_next, new_next, 1)
old_status = 'Status: **SOFTWARE IMPLEMENTATION + STRUCTURAL HARDENING + EMULATOR UI E2E COMPLETE; PHYSICAL FIELD VALIDATION PENDING.** See `PHASE3_PRE_FIELD_GOLDEN.md`, `PHASE3_FIELD_COLLECTION.md` and `PHASE3_CODE_QUALITY_REVIEW.md`.'
new_status = 'Status: **SOFTWARE + EMULATOR E2E + TARGETED PHYSICAL ACCEPTANCE COMPLETE; VARIED-DENSITY FIELD WALK PENDING.** See `PHASE3_TARGETED_PHYSICAL_ACCEPTANCE.md`, `PHASE3_PRE_FIELD_GOLDEN.md`, `PHASE3_FIELD_COLLECTION.md` and `PHASE3_CODE_QUALITY_REVIEW.md`.'
assert old_status in s
s = s.replace(old_status, new_status, 1)
marker = 'Design preference: do not process every advertisement as an independent expensive business event.'
insert = f'''Targeted physical acceptance on 2026-09-10 at source `{sha}` reconciled `443` raw BLE callbacks as `417` accepted + `26` coalesced + `0` rejected, with `queueDroppedTotal=0`, `417/417/0` processing started/succeeded/failed, device persistence `119 + 298 throttled = 417`, and signal sampling `107 + 310 throttled + 0 failed = 417`. The same build physically passed the large Share/OOM regression and kept Settings responsive while Radar was running. See `PHASE3_TARGETED_PHYSICAL_ACCEPTANCE.md`. The earlier >430 s two-refresh passive-BLE watchdog evidence remains accepted and should not be repeated unless scanner lifecycle/watchdog code changes.\n\n{marker}'''
assert marker in s
s = s.replace(marker, insert, 1)
work_entry = f'''\n### 2026-09-10 — Phase 3 targeted physical acceptance\n\n- Source `{sha}` installed on Samsung SM-S906B with `adb install -r`; Room counts and integrity were unchanged across install.\n- Manual physical pass: `Database & Updates` remained responsive with Radar running; large Share completed and produced the process-lifetime export instead of OOM.\n- Export accounting reconciled exactly with zero queue drops/rejections/failures; sample writes were materially reduced by intentional throttling.\n- Remaining Phase 3 item: one varied-density walk with Session Export JSON plus Room/WAL/SHM continuity evidence. Phase 4 remains blocked.\n'''
if '### 2026-09-10 — Phase 3 targeted physical acceptance' not in s:
    s = s.rstrip() + '\n' + work_entry
p.write_text(s, encoding='utf-8')

p = Path('docs/PHASE3_PRE_FIELD_GOLDEN.md')
s = p.read_text(encoding='utf-8')
s = s.replace('Status: **SOFTWARE + EMULATOR E2E READY; PHYSICAL FIELD EVIDENCE PENDING**', 'Status: **SOFTWARE + EMULATOR E2E + TARGETED PHYSICAL ACCEPTANCE PASS; VARIED-DENSITY FIELD WALK PENDING**', 1)
s = s.replace('This freezes the engineering handoff immediately before the Phase 3 phone/ADB validation. It does **not** close Phase 3 and does not authorize Phase 4.', f'This document began as the pre-field freeze. It now also points to the targeted physical acceptance recorded at source `{sha}`. It still does **not** close Phase 3 or authorize Phase 4; one varied-density real-device walk remains.', 1)
s = s.replace('## Immutable install target', '## Historical immutable pre-field target', 1)
needle = 'The versioned release is the install authority for this field run. Once collection starts, do not replace it with `latest-tester`, even if `main` moves.'
replacement = f'''The `.1` release is the historical pre-field authority and is now superseded for the final Phase 3 walk by the physically accepted application source `{sha}`. Do **not** use `.1` for the remaining walk because later stability fixes include the passive-scan watchdog, large-export OOM fix, Settings load reduction and fingerprint-based sample throttling.\n\nTargeted S22+ acceptance for `{sha}` is recorded in `PHASE3_TARGETED_PHYSICAL_ACCEPTANCE.md`: exact ingest accounting has zero unexplained loss, Settings stays responsive while Radar runs, and a 21,704-sample Share export completes without OOM.'''
assert needle in s
s = s.replace(needle, replacement, 1)
start = s.index('## Next physical step')
s = s[:start] + f'''## Next physical step\n\nRun one varied-density real-device walk using an APK built from accepted application source `{sha}` with the established tester signing certificate. Preserve Session Export JSON before process death and collect Room plus WAL/SHM afterward. Reconcile continuity across the walk.\n\nDo not repeat the already accepted >430-second passive-BLE watchdog soak, Settings performance regression, or large Share/OOM regression unless the relevant implementation changes. After the walk passes, close Phase 3 and only then hand off to Phase 4.\n'''
p.write_text(s, encoding='utf-8')

p = Path('docs/PHASE3_FIELD_COLLECTION.md')
s = p.read_text(encoding='utf-8')
s = s.replace('Status: **SOFTWARE READY; FIELD VALIDATION PENDING — USE ONLY THE EXACT-SHA GREEN TESTER BUILD**', 'Status: **TARGETED PHYSICAL ACCEPTANCE PASS; VARIED-DENSITY WALK PENDING — USE THE EXACT ACCEPTED APPLICATION SOURCE**', 1)
old_approved = '''Use the immutable tester release `v1.0.0-phase3-pre-field.1` linked from README. Verify its `.sha256` before installation when practical. Once evidence collection begins, do not replace it with `latest-tester` or another build.\n\nBefore the walk, connect the launched app to the Mac with USB debugging authorized. Local Agent/ADB should capture a non-destructive baseline for package `io.blueeye`: device identity, package/install information, installed APK identity where practical, and an app-focused logcat/runtime snapshot. Do not force-stop the app merely to collect this baseline because Phase 3 ingest totals are process-lifetime diagnostics.'''
new_approved = f'''The historical `.1` pre-field release is superseded for the remaining walk. Use an APK built from accepted application source `{sha}` and signed with the established tester certificate `{cert}`. Once collection begins, do not switch builds during the same field session.\n\nBefore the walk, connect the launched app to the Mac with USB debugging authorized. Local Agent/ADB should capture a non-destructive baseline for package `io.blueeye`: device identity, package/install information, installed APK identity where practical, and an app-focused logcat/runtime snapshot. Do not force-stop the app merely to collect this baseline because Phase 3 ingest totals are process-lifetime diagnostics.'''
assert old_approved in s
s = s.replace(old_approved, new_approved, 1)
s = s.replace('- Install only the immutable `v1.0.0-phase3-pre-field.1` tester APK for this Phase 3 collection. The versioned release must have green Quality/build publication evidence; do not switch builds during the same field session.', f'- Use only an APK built from accepted application source `{sha}` for the remaining Phase 3 collection; do not switch builds during the same field session.', 1)
marker = '## During the walk'
already = '''## Already accepted; do not repeat\n\nThe following physical evidence is already sufficient unless related implementation changes:\n\n- >430-second passive BLE survival on Samsung Android 16 with the same PID/service and two watchdog refreshes;\n- Settings responsiveness while Radar is actively scanning;\n- large Session Share/export without the previously reproduced OOM;\n- short process-lifetime ingest reconciliation with zero queue drops/rejections/failures and explicit sample throttling.\n\nSee `PHASE3_TARGETED_PHYSICAL_ACCEPTANCE.md`. The remaining purpose of the walk is varied-density continuity evidence, not repeating these regressions.\n\n'''
assert marker in s
s = s.replace(marker, already + marker, 1)
p.write_text(s, encoding='utf-8')

p = Path('docs/PHASE3_HANDOFF.md')
s = p.read_text(encoding='utf-8')
s = s.replace('Status: **HISTORICAL KICKOFF — SOFTWARE IMPLEMENTED; FIELD VALIDATION PENDING**', 'Status: **HISTORICAL KICKOFF — TARGETED PHYSICAL ACCEPTANCE PASS; VARIED-DENSITY WALK PENDING**', 1)
s = s.replace('For current state use `STABILITY_RECOVERY_GUIDE.md`, `PHASE3_PRE_FIELD_GOLDEN.md`, `PHASE3_FIELD_COLLECTION.md` and `PHASE3_CODE_QUALITY_REVIEW.md`.', 'For current state use `STABILITY_RECOVERY_GUIDE.md`, `PHASE3_TARGETED_PHYSICAL_ACCEPTANCE.md`, `PHASE3_PRE_FIELD_GOLDEN.md`, `PHASE3_FIELD_COLLECTION.md` and `PHASE3_CODE_QUALITY_REVIEW.md`.', 1)
marker = '## Read first'
checkpoint = f'''## Current acceptance checkpoint — 2026-09-10\n\nApplication source `{sha}` has exact-SHA green CI and targeted Samsung S22+ physical acceptance for the previously open large-export OOM, Settings performance under active Radar load, and short process-lifetime ingest accounting. The accounting reconciles `443 = 417 accepted + 26 coalesced + 0 rejected`, with zero queue drops and zero processing/sample write failures. Earlier >430-second passive-BLE watchdog survival with two 240-second refreshes remains accepted.\n\nThe only remaining Phase 3 field gate is one varied-density walk with preserved Session Export JSON plus Room/WAL/SHM continuity evidence. Do not start Phase 4 yet.\n\n'''
assert marker in s
s = s.replace(marker, checkpoint + marker, 1)
p.write_text(s, encoding='utf-8')
PY

git diff --check
git diff -- docs/STABILITY_RECOVERY_GUIDE.md docs/PHASE3_PRE_FIELD_GOLDEN.md docs/PHASE3_FIELD_COLLECTION.md docs/PHASE3_HANDOFF.md docs/PHASE3_TARGETED_PHYSICAL_ACCEPTANCE.md

git add docs/STABILITY_RECOVERY_GUIDE.md docs/PHASE3_PRE_FIELD_GOLDEN.md docs/PHASE3_FIELD_COLLECTION.md docs/PHASE3_HANDOFF.md docs/PHASE3_TARGETED_PHYSICAL_ACCEPTANCE.md
git commit -m 'Record Phase 3 targeted physical acceptance'
git push origin main
NEW_HEAD="$(git rev-parse HEAD)"
echo "new_head=$NEW_HEAD"
git status --short
test -z "$(git status --porcelain)"
