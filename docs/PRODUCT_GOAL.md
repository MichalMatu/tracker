# Product Goal

BlueEye Tracker is a personal Bluetooth situational-awareness app.

The practical goal is to help a user answer:

- What Bluetooth/BLE devices are around me right now?
- Has a known device from my watchlist appeared again?
- Is one unknown device repeatedly seen across time and movement?
- What raw evidence supports the app's classification?

## Primary Use Cases

1. Watchlist alert
   - User marks a device.
   - App alerts when the same fingerprint is observed again.
   - This is the most realistic and highest-value workflow.

2. Possible tracker detection
   - App correlates repeated presence, RSSI trend, address behavior, manufacturer data, service UUIDs, and time window.
   - App shows suspicion level, not certainty.

3. Device recognition
   - App parses known BLE formats and known vendor signals.
   - App explains which field caused the label.

4. Evidence timeline
   - User can inspect first seen, last seen, RSSI samples, raw payload summary, parsed fields, and classification reasons.

## Non-Goals

- Do not identify a person from BLE alone.
- Do not claim that a specific public service is nearby based only on a name or vendor.
- Do not treat RSSI as distance. RSSI is only a noisy signal-strength clue.
- Do not hide active probing. Any active Bluetooth connection/probe must be explicit in UI and settings.

## Product Standard

Every user-facing detection should answer three questions:

- What was observed?
- Why did the app classify it this way?
- How confident is the app?
