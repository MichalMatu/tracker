# Phase 3 Pre-Field Golden Candidate

Status: **SOFTWARE + EMULATOR E2E + TARGETED PHYSICAL ACCEPTANCE PASS; VARIED-DENSITY FIELD WALK PENDING**

This document began as the pre-field freeze. It now also points to the targeted physical acceptance recorded at source `745fdf30271459a20e380763ea69b8ed2e601839`. It still does **not** close Phase 3 or authorize Phase 4; one varied-density real-device walk remains.

## Historical immutable pre-field target

- Versioned tester tag/release: `v1.0.0-phase3-pre-field.1`
- Final checkpoint tag, created only after all publication gates pass: `checkpoint-phase3-pre-field-golden-2026-09-09`
- Package id: `io.blueeye`
- Signing: Android debug signing; not Play Store/production signing.
- Download entry point: **Install BlueEye Tracker** in `README.md`.

The `.1` release is the historical pre-field authority and is now superseded for the final Phase 3 walk by the physically accepted application source `745fdf30271459a20e380763ea69b8ed2e601839`. Do **not** use `.1` for the remaining walk because later stability fixes include the passive-scan watchdog, large-export OOM fix, Settings load reduction and fingerprint-based sample throttling.

Targeted S22+ acceptance for `745fdf30271459a20e380763ea69b8ed2e601839` is recorded in `PHASE3_TARGETED_PHYSICAL_ACCEPTANCE.md`: exact ingest accounting has zero unexplained loss, Settings stays responsive while Radar runs, and a 21,704-sample Share export completes without OOM.

## Production-code E2E checkpoint

Before this documentation freeze, production/test-harness tree `61659c3d516c3e1c0713d9c1d1ce50ba1d6c0893` passed Quality #66, Secret Scan #96, Tester Release #23 and Android UI Smoke #7. The smoke produced 21 screenshots, exercised real Watchlist rows with tracking toggle and `Remove from Watchlist`, covered Radar/Baseline/Filter/GATT/Calibration/Details/Edit/Raw Data/Settings/Alerts/Appearance/Database/Export/share/Clear/Scan, and recorded no `FATAL EXCEPTION` or `ANR in io.blueeye`.

The Radar regression is protected: volatile live `RSSI` and `lastSeenAt` updates do not continuously reorder existing cards.

The final documentation commit changes documentation only. Its tagged SHA must still pass the publication gate below so the downloadable artifact and repository state remain traceable.

## Golden publication gate

1. final `main` SHA passes GitHub **Quality**,
2. final `main` SHA passes **Secret Scan**,
3. **Android UI Smoke** is explicitly run against the final SHA and passes,
4. rolling `latest-tester` is rebuilt from that final SHA,
5. annotated version tag `v1.0.0-phase3-pre-field.1` points to that final SHA,
6. versioned Tester Release reruns `qualityCheck` and `:app:assembleDebug` and publishes `BlueEye-Tracker-v1.0.0-phase3-pre-field.1-debug.apk` plus `.sha256`,
7. release asset and checksum are verified,
8. annotated checkpoint `checkpoint-phase3-pre-field-golden-2026-09-09` is created only after all gates are green.

## Next physical step

Run one varied-density real-device walk using an APK built from accepted application source `745fdf30271459a20e380763ea69b8ed2e601839` with the established tester signing certificate. Preserve Session Export JSON before process death and collect Room plus WAL/SHM afterward. Reconcile continuity across the walk.

Do not repeat the already accepted >430-second passive-BLE watchdog soak, Settings performance regression, or large Share/OOM regression unless the relevant implementation changes. After the walk passes, close Phase 3 and only then hand off to Phase 4.
