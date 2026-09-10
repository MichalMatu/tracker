# Phase 3 Engineering Field Collection

Status: **TARGETED PHYSICAL ACCEPTANCE PASS; VARIED-DENSITY WALK PENDING — USE THE EXACT ACCEPTED APPLICATION SOURCE**
Scope: validate Phase 3 BLE ingest accounting and bounded latest-per-device coalescing on a real walk. This is engineering evidence for stability recovery; it does not unpause the general Field MVP checklist or advance identity/alert phases.

## Approved pre-field build

The historical `.1` pre-field release is superseded for the remaining walk. Use an APK built from accepted application source `745fdf30271459a20e380763ea69b8ed2e601839` and signed with the established tester certificate `fff1ada9aa9e2709b4e4bda8333404feae854372df319b77f1f45d7869a61cb6`. Once collection begins, do not switch builds during the same field session.

Before the walk, connect the launched app to the Mac with USB debugging authorized. Local Agent/ADB should capture a non-destructive baseline for package `io.blueeye`: device identity, package/install information, installed APK identity where practical, and an app-focused logcat/runtime snapshot. Do not force-stop the app merely to collect this baseline because Phase 3 ingest totals are process-lifetime diagnostics.

## Before the walk

- Use only an APK built from accepted application source `745fdf30271459a20e380763ea69b8ed2e601839` for the remaining Phase 3 collection; do not switch builds during the same field session.
- Start scanning explicitly.
- Open Settings diagnostics and confirm `Raw BLE/min` is non-zero in a place with nearby Bluetooth devices.
- `Queue dropped` should remain `0`. A non-zero `Queue rejected` is allowed only under true unique-device capacity exhaustion and must be investigated.
- Do not clear app data before or during the collection.

## Already accepted; do not repeat

The following physical evidence is already sufficient unless related implementation changes:

- >430-second passive BLE survival on Samsung Android 16 with the same PID/service and two watchdog refreshes;
- Settings responsiveness while Radar is actively scanning;
- large Session Share/export without the previously reproduced OOM;
- short process-lifetime ingest reconciliation with zero queue drops/rejections/failures and explicit sample throttling.

See `PHASE3_TARGETED_PHYSICAL_ACCEPTANCE.md`. The remaining purpose of the walk is varied-density continuity evidence, not repeating these regressions.

## During the walk

Use the phone normally and collect a varied-density BLE session. Screen-on and ordinary screen-off/background periods are useful; do not deliberately change Bluetooth or exercise Phase 2 lifecycle edge cases unless a separate test calls for it.

The Phase 3 counters are intended to make the ingest path reconcilable:

`raw BLE callbacks -> queued + coalesced + rejected -> processing -> persistence/sample outcomes`

For the Stable Core build, silent queue eviction must remain zero.

## Before killing or uninstalling the app

The ingest totals are process-runtime diagnostics and are not stored in Room. Before force-stop, reboot, uninstall, or clearing app data:

1. Stop scanning normally if desired; the diagnostics snapshot remains available in the same process.
2. Open Settings -> Session export.
3. Use **Share** (preferred) or **Copy** and preserve the complete JSON. The export contains `fieldMvpDiagnostics.scanner.ingest`.
4. Keep the app data on the phone until the raw database has also been collected.

If the JSON is not preserved before the process is killed, Room can still be analyzed, but callback/queue/coalescing totals cannot be reconstructed exactly.

## Data to collect after return

Use Local Agent/ADB against the exact installed debug build to collect, without mutating application data:

- the preserved Session Export JSON,
- the Room database for `io.blueeye`,
- matching `-wal` and `-shm` files when present,
- exact installed APK/source SHA,
- package/install metadata and baseline/final BlueEye logcat snapshots,
- a short logcat/runtime snapshot if an ingest error or rejection was observed.

## Analysis gate

Before Phase 4, analyze the field evidence for:

- `PRAGMA integrity_check`, table counts and time ranges,
- exact `raw = queued + coalesced + rejected` accounting for the Stable Core BLE path,
- `queueDroppedTotal == 0`,
- accepted work reconciling with processing plus any still-pending/in-flight work at export time,
- processing success/failure and queue wait/processing latency,
- persisted device updates vs intentional update throttling,
- signal samples written/throttled/failed,
- queue high-water pressure,
- DB growth and RSSI/sample continuity across the walk.

Identity/carryover tuning remains Phase 5. Field anomalies may be recorded now, but Phase 3 must not turn them into heuristic changes.
