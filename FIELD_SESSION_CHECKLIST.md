# BlueEye field session checklist

> Phase 3 field reacceptance is **ACCEPTED / CLOSED**. This checklist is active again for repeatable engineering field sessions and dataset collection. It is not, by itself, a release-readiness verdict. See `docs/README.md` for the current plan and `docs/PHASE3_FIELD_REACCEPTANCE_CLOSURE_2026-09-11.md` for the final Phase 3 acceptance record.

## Before walking

1. Record the exact app/source SHA being tested.
2. Do not switch builds during the same comparison session.
3. Confirm Bluetooth/Nearby Devices, Location and Notifications permissions required by the chosen test scenario.
4. Confirm BlueEye is not unexpectedly battery-restricted for a background/screen-off test.
5. Open the app and confirm the scanner is running normally before leaving.
6. If alerts are part of the scenario, verify the intended alert configuration before the walk rather than changing it mid-session.
7. If HCI correlation is intentionally required, enable/verify the platform snoop path before the session; ordinary UX/dataset walks do not require HCI by default.

## Recommended session types

Use separate sessions when possible so each dataset has a clear purpose:

1. **Home/stationary baseline** — normal nearby Bluetooth devices with little/no user movement.
2. **Ordinary walk / no controlled tracker** — false-positive background and screen-off continuity.
3. **Controlled companion device** — carry a known Bluetooth/watchlist device to validate repeated presence and identity continuity.
4. **Dense city / transit / shop** — many simultaneous devices and rotating-address pressure.
5. **Targeted regression** — only when a specific parser, identity, Follow-Me, alert or background-scan defect needs reacceptance.

For comparable datasets, avoid mixing configuration changes, app restarts and multiple unrelated experiments into one session unless the experiment explicitly requires them.

## During the session

- Use the phone naturally unless the scenario requires a controlled locked-screen interval.
- Note unusual events that can explain the data later: long stationary periods, transport, app restart, permission prompt, Bluetooth toggle, battery-saver change, known device joining/leaving, or manual Pause/Stop.
- Do not interpret RSSI as exact distance.
- Do not treat an observation GPS point as the exact Bluetooth device location; it is where the phone observed the signal.

## Do not tune before reviewing the capture

When collecting data for a parser/scoring/identity problem, preserve the dataset before changing the relevant heuristic. Prefer this loop:

```text
capture
  -> analyze
  -> create privacy-safe fixture
  -> change deterministic rule/parser
  -> replay regressions
  -> collect next comparison capture
```

Do not tune Follow-Me thresholds, identity correlation or parser rules merely because one live UI card looked surprising.

## Minimum data to bring back

For each engineering session, preserve or record as appropriate:

- scenario name,
- exact app/source SHA,
- phone model and Android version,
- approximate session start/end and duration,
- whether the screen was mostly on/off and any deliberate lock interval,
- whether scanner service/process remained healthy,
- relevant exported session/database data using the supported export path,
- whether GPS was enabled and the rough quality/availability of location samples,
- known controlled devices used in the scenario,
- notable user actions or interruptions,
- observed alerts/errors/jank.

For deep regressions, additional private evidence may include Room/WAL/SHM, targeted logcat or Bluetooth HCI/bugreport material. Keep exact GPS, MAC addresses, raw HCI/bugreports and private databases out of the public repository.

## Post-session review

1. Preserve the capture before resetting/clearing anything that the analysis may need.
2. Check basic ingest/persistence consistency before drawing product conclusions.
3. Separate collection defects from UI/presentation defects.
4. Compare candidates against previous datasets when testing a rule change.
5. Convert confirmed defects into privacy-safe regression fixtures where practical.
6. Update the active plan/checklist only after evidence supports the conclusion.

## Product boundaries

BlueEye reports Bluetooth evidence, watchlist returns, cautious Follow-Me/identity analysis and supported classifications. It does not claim to identify a person, infer malicious intent, precisely range a device, know an exact Bluetooth device location, or detect every possible tracker/background signal.
