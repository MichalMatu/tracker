# Phase 3 Pre-Field Golden Candidate

Status: **SOFTWARE + EMULATOR E2E READY; PHYSICAL FIELD EVIDENCE PENDING**

This freezes the engineering handoff immediately before the Phase 3 phone/ADB validation. It does **not** close Phase 3 and does not authorize Phase 4.

## Immutable install target

- Versioned tester tag/release: `v1.0.0-phase3-pre-field.1`
- Final checkpoint tag, created only after all publication gates pass: `checkpoint-phase3-pre-field-golden-2026-09-09`
- Package id: `io.blueeye`
- Signing: Android debug signing; not Play Store/production signing.
- Download entry point: **Install BlueEye Tracker** in `README.md`.

The versioned release is the install authority for this field run. Once collection starts, do not replace it with `latest-tester`, even if `main` moves.

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

After publication: download the immutable APK from README, install and launch it, grant required Bluetooth/location/notification permissions, connect USB with ADB authorization, then collect a non-destructive baseline (`adb devices -l`, package/install identity for `io.blueeye`, installed APK identity where practical, and BlueEye-focused logcat/runtime evidence). Do not clear app data or force-stop after beginning evidence collection. Then run `docs/PHASE3_FIELD_COLLECTION.md`.

Before Phase 4, preserve Session Export JSON plus Room/WAL/SHM and reconcile Phase 3 ingest counters, including `queueDroppedTotal == 0`.
