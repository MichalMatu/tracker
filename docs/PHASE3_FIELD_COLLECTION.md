> **HISTORICAL STATUS NOTICE:** Phase 3 is ACCEPTED / CLOSED and Phase 4 is UNBLOCKED. This file is retained as stabilization provenance; do not use any older NEXT ACTION, pending-walk status, pre-field build, or blocked-phase statement below as current instructions. Current work starts from docs/README.md and docs/UI_UX_REDESIGN_PLAN.md.

# Phase 3 Engineering Field Collection

Status: **TARGETED PHYSICAL ACCEPTANCE + EXACT-BUILD PRE-SMOKE PASS; FINAL VARIED-DENSITY WALK/CAPTURE PENDING**
Scope: validate Phase 3 BLE ingest accounting and bounded latest-per-device coalescing on a real walk. This is engineering evidence for stability recovery; it does not unpause the general Field MVP checklist or advance identity/alert phases.

## Approved pre-field build

Use the already-installed debug APK built from exact source `40eac7a504d363a05cd6c235c25146e01bb36ff2`. APK SHA-256 is `17ebd807c526eda077e9e7f9e96e6306924890e4c201a3c5d3e50f9d76237a65`; signer certificate SHA-256 is `fb07493cf97b0a84ec410f7f72f12938b7f9d47151546e15c049c49c27807b11` (`Android Debug`). Do not reinstall or switch builds during the same field session. Documentation-only descendants of this source do not require reinstalling the phone.

Before the walk, connect the launched app to the Mac with USB debugging authorized. Local Agent/ADB should capture a non-destructive baseline for package `io.blueeye`: device identity, package/install information, installed APK identity where practical, and an app-focused logcat/runtime snapshot. Do not force-stop the app merely to collect this baseline because Phase 3 ingest totals are process-lifetime diagnostics.

## Before the walk

- Use only an APK built from accepted application source `40eac7a504d363a05cd6c235c25146e01bb36ff2` for the remaining Phase 3 collection; do not switch builds during the same field session.
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

## Bluetooth HCI companion capture

Bluetooth HCI snoop tracing is enabled and physically verified on the Samsung (`dumpsys bluetooth_manager` reports `snoop_logger_tracing`). Normal ADB cannot read the protected Samsung snoop path directly. Leave HCI snoop enabled throughout the walk. After preserving Session Export, ADB/runtime evidence and Room/WAL/SHM, generate a Samsung/Android bugreport and extract `btsnoop_hci.log` or the vendor-equivalent snoop artifact. Keep raw HCI/bugreport private; they may expose sensitive device/system information. HCI is a host/controller trace, not an over-the-air RF sniffer, and it complements rather than replaces Tracker GPS/RSSI telemetry.

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
- the Bluetooth HCI snoop artifact extracted from a system bugreport, with file hash/time range when practical.

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
