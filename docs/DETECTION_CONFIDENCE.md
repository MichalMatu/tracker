# Detection Confidence

Bluetooth observation is evidence, not proof of identity or intent.

The app should show confidence as a conservative classification built from observable signals.

## Levels

| Level | Meaning | Example |
| --- | --- | --- |
| `LOW` | One weak clue matched | Name contains a known word, generic service UUID |
| `MEDIUM` | One strong clue or several weak clues matched | Manufacturer data family, known service pattern, repeated appearance |
| `HIGH` | Multiple independent clues point to the same class | Vendor/manufacturer data + service UUID + repeated stable behavior |
| `CRITICAL` | High confidence plus user-relevant behavior | Watchlist hit, repeated follow-me pattern, or user-confirmed suspicious device |

## Rules

- Name-only matching cannot exceed `MEDIUM`.
- RSSI cannot prove distance.
- A random/private address lowers identity confidence unless correlated by stronger evidence.
- A watchlist hit is strong only for that saved fingerprint, not for the real-world owner.
- "Axon", "police", or similar labels must be shown as device classification evidence, not as proof that a specific service/person is present.
- Public-safety-like review decisions can mark repeated benign context as
  `Known Safe` or `False Positive`, but they do not prove or disprove that a
  real-world service/person is present.
- Active probing results must be marked as active evidence.
- `CRITICAL` should be reserved for user-confirmed/watchlist evidence or very strong independent evidence. The current implementation uses it for watchlist hits and registry entries that are explicitly marked critical.

## Current Implementation

The domain model is implemented in
`core/model/src/main/kotlin/io/blueeye/core/model/DetectionEvidence.kt`.

Current confidence mapping:

- `NAME`: `LOW` for a plain advertised name, `MEDIUM` for tactical name matches.
- `MODEL`: `LOW` for inferred model metadata shown as identity context, not a risk signal.
- `OUI`: mapped from tactical registry confidence.
- `MANUFACTURER_ID`: `HIGH` for tactical manufacturer matches.
- `SERVICE_UUID`: `HIGH` for tactical service UUID matches, `LOW` for neutral
  identity context. Provenance is explicit: BLE advertisement, Classic SDP, or
  active GATT service discovery.
- `RAW_PAYLOAD`: `LOW` when an advertisement payload was decoded.
- `RSSI_PATTERN`: `LOW`, `MEDIUM`, or `HIGH` from Follow-Me status.
- `IDENTITY_CARRYOVER`: `LOW` when a rotating address was correlated with an
  existing device record; this is continuity context, not a risk signal.
  User review of that merge is stored as `IdentityCarryoverVerdict`, not as a
  higher confidence risk signal. Reviewed verdicts are also copied into the
  carryover evidence context so `FALSE_MATCH` and `INCONCLUSIVE` decisions stay
  visible in Details/export without raising confidence.
- `WATCHLIST`: `CRITICAL` for user-selected saved fingerprints.
- `GATT_PROBE` / `RFCOMM_PROBE`: `MEDIUM` and marked as active evidence.

## UI Contract

Every classified device should expose:

- confidence level,
- reasons,
- evidence source,
- last observation time,
- whether evidence came from passive scan or active probe,
- raw/parsed values where safe to show.

## Copy Guidance

Use:

- "Matches Axon-like Bluetooth signature"
- "Possible tracker behavior"
- "Watchlist device reappeared"

Avoid:

- "Police nearby"
- "You are being tracked"
- "Distance: 3m"
