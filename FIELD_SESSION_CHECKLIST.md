# BlueEye Field MVP session checklist

## Before walking

1. Build and install the debug APK on the test phone.
2. Grant Bluetooth, Nearby Devices, Location, and Notifications permissions when Android asks.
3. If the phone stops scanning in the background, disable battery restrictions for BlueEye for the test session.
4. Open Settings -> Alerts & Collection.
5. Confirm the Field MVP diagnostics panel shows scanner and alert delivery status.
6. Tap **Test alert** while the phone is unlocked.
7. Lock the phone and run **Test alert** again to verify Android notification delivery, vibration, and sound policy.

## First sessions to collect

Run separate sessions and export after each one:

1. **Home baseline**: stay in one place and collect normal nearby Bluetooth devices.
2. **Walk no tracker**: walk without a known watchlist device to capture false-positive background.
3. **Walk with watchlist device**: carry or place a known Bluetooth device and verify watchlist return behavior.
4. **City / transit / car**: collect noisy environments only after the home and simple walk sessions.

## Do not change before collecting exports

Do not tune Follow-Me thresholds, add new parsers, or re-enable smart-home/environmental decoder bindings until the first exported sessions are reviewed.

## Minimum data to bring back

For each session, bring the exported JSON plus these notes:

- scenario name,
- phone model and Android version,
- whether the screen was locked,
- whether the test alert worked unlocked and locked,
- whether BLE and Classic last-seen timestamps moved during the session,
- whether any Android permission or notification channel was blocked,
- which known device, if any, was used as watchlist smoke test.

## MVP boundaries

This Field MVP reports Bluetooth evidence, watchlist returns, cautious Follow-Me heuristics, and public-safety-like signal classification. It does not claim to detect a person, intent, every tracker, or every background signal under Android screen-off limits.
