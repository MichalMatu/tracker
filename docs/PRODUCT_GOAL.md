# Product Goal

BlueEye Tracker is a personal Bluetooth situational-awareness app.

The practical goal is to help a user answer:

- What Bluetooth/BLE devices are around me right now?
- Which observations actually deserve my attention instead of being ordinary RF noise?
- Has a known device from my watchlist appeared again?
- Is one unknown device repeatedly seen across time, movement and locations?
- What evidence supports the app's local classification?
- When a case is ambiguous, can a compact evidence bundle support deeper optional analysis without uploading the entire raw dataset?

## Primary use cases

1. **Nearby situational awareness**
   - Radar presents a compact, responsive view of nearby devices.
   - The default UI prioritizes identity, signal freshness/strength and attention state rather than raw Bluetooth fields.
   - Technical data remains available through progressive disclosure.

2. **Watchlist alert**
   - User marks a device.
   - App alerts when the same stable identity/fingerprint is observed again.
   - This remains one of the highest-value deterministic workflows.

3. **Possible tracker detection**
   - App correlates repeated presence, confirmed user movement, encounter timing, RSSI behavior, address behavior, manufacturer/service data and other bounded evidence.
   - App shows suspicion/attention level, not certainty about intent or ownership.

4. **Device recognition and evidence**
   - App parses supported BLE/Bluetooth formats and vendor signals.
   - Details explains the most important evidence first.
   - UUIDs, payloads, PHY/GATT and full raw/parsed evidence remain inspectable but are secondary UI.

5. **Signal and observation history**
   - User can inspect RSSI history, first/last seen, encounter context and Follow-Me history.
   - A per-device sightings map may show where the phone observed a signal when GPS data is available and sufficiently accurate.
   - Observation coordinates must never be described as the exact location of the Bluetooth device.

6. **Optional deeper analysis**
   - Local deterministic collection, parsing, reduction and alerting must remain fully functional without AI.
   - A future optional Analyst mode may send a small, versioned Analysis Bundle containing reduced evidence rather than the entire database/raw scan stream.
   - AI assessment must be labeled separately from the local assessment and include supporting evidence, counter-evidence and uncertainty.

## Processing direction

The intended architecture is:

```text
raw observations
  -> deterministic parsing
  -> local noise reduction / identity / encounters / movement / RSSI summaries
  -> small set of explainable analysis candidates
  -> local verdict and UI
  -> optional compact Analysis Bundle
  -> optional AI analyst for genuinely ambiguous multi-signal reasoning
```

Mechanical, repeatable reasoning should move into deterministic local code over time. AI is a higher-level analyst, not a replacement for reliable collection, parsing, persistence or offline detection.

## Feedback loop

Real field data should improve the deterministic system cumulatively:

```text
field capture
  -> local reduction
  -> manual/AI analysis
  -> confirmed failure or ambiguity
  -> privacy-safe regression fixture
  -> parser/reducer/scorer improvement
  -> replay existing datasets
  -> next field capture
```

## Non-goals

- Do not identify a person from BLE alone.
- Do not infer malicious intent solely from Bluetooth presence.
- Do not claim that a specific public service or organization is nearby based only on a name/vendor signal.
- Do not treat RSSI as precise distance.
- Do not represent the phone's observation GPS point as exact device location.
- Do not hide active probing. Any active Bluetooth connection/probe must be explicit in UI and settings.
- Do not make cloud/AI availability a requirement for baseline local detection.
- Do not upload full private field captures when a reduced evidence representation is sufficient.

## Product standard

Every user-facing detection should answer, in this order:

1. **What is happening?** — concise device/attention summary.
2. **Why does it matter?** — the small set of evidence that materially affected the assessment.
3. **How confident is the assessment?** — including uncertainty and contradictory evidence when relevant.
4. **Can I inspect the source data?** — history and technical/raw evidence remain available without cluttering the default view.
