# Phase 3 Targeted Physical Acceptance — 2026-09-10

Status: **PASS for targeted physical regressions and ingest accounting; varied-density field walk still pending**

This document records the physical evidence collected after the Phase 3 software fixes. It closes the named regressions below but does **not** by itself close Phase 3 or authorize Phase 4. The remaining gate is one varied-density real-device walk with preserved Session Export JSON plus Room/WAL/SHM continuity evidence.

## Exact accepted app build

- Source commit: `745fdf30271459a20e380763ea69b8ed2e601839` (`Reduce live stats and signal history load`)
- Device: Samsung SM-S906B, Android 16 / SDK 36
- Installed tester APK SHA-256: `563d5a068c112983eebdfab1661396e407a2ec15ca81ed603d70bc235dc69b63`
- Tester signing certificate SHA-256: `fff1ada9aa9e2709b4e4bda8333404feae854372df319b77f1f45d7869a61cb6`
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
