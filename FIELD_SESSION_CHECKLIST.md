# BlueEye field session checklist

Use this for repeatable physical tests. It is a capture/validation checklist, not a release verdict.

## Before the session

- Record the exact `main`/APK SHA.
- Do not switch builds or major configuration during one comparison session.
- Confirm required Bluetooth/Nearby, Location and Notification permissions for the scenario.
- Verify scanner state and intended alert/active-collection settings before leaving.
- If automatic GATT is part of the scenario, explicitly enable the master switch and record that choice. The accepted field baseline probes one connectable device at a time; the 15 min cooldown is per previously probed device, so newly seen eligible devices can still be queued immediately.
- Avoid enabling HCI/bugreport capture unless the defect actually requires it.

## Useful scenarios

- stationary/home baseline;
- ordinary walk with no controlled target;
- controlled known/watchlist companion device;
- dense city/transit/shop environment;
- targeted regression for one parser/identity/Follow-Me/alert/UI defect.

Keep scenarios separate when possible so later analysis can explain what changed.

## During the session

- Use the phone naturally unless the scenario defines a lock/screen-off interval.
- Note events that can explain discontinuities: transport, long stationary interval, restart, permission prompt, Bluetooth toggle, battery saver, known device joining/leaving, Pause/Stop.
- Treat RSSI as signal context, not distance.
- Treat GPS as the phone's observation position, not the Bluetooth device's exact position.
- Do not retune classifiers/thresholds while collecting evidence for the problem they are supposed to explain.

## Minimum evidence to preserve

- scenario and exact source/app SHA;
- phone model + Android version;
- approximate start/end/duration;
- screen-on/off and movement context;
- scanner/process health;
- supported session export when needed;
- GPS availability/quality summary when relevant;
- known controlled devices/actions;
- whether automatic GATT was enabled, plus aggregate probe success/error/timeout behavior when relevant;
- alerts, parser failures, crashes, ANRs, jank or UI defects observed.

Deep regressions may additionally need private Room/WAL/SHM, bounded logcat or HCI/bugreport evidence. Never commit exact GPS, MACs, private databases, HCI or bugreports to the public repository.

## Review loop

```text
capture
  -> preserve original evidence
  -> separate collection vs presentation defects
  -> analyze against previous/controlled data
  -> create privacy-safe regression fixture where practical
  -> change deterministic code
  -> replay tests
  -> collect the next comparison
```

Update the active plan only after evidence supports the conclusion.
