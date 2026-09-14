# Detection model

Bluetooth observation is evidence, not proof of identity, ownership, intent, exact distance or exact device location.

The source contract lives in `core/model/.../DetectionEvidence.kt`.

## Evidence contract

`DetectionEvidence` carries:

- `source` — what produced the clue;
- `confidence` — `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`;
- human-readable reason;
- timestamp;
- optional raw/parsed value;
- `isPassive`;
- provenance.

Current sources include name/model/appearance/Class of Device, OUI/manufacturer/service UUID/raw payload, Follow-Me/RSSI pattern, identity carryover, watchlist/user confirmation and active GATT/RFCOMM probes.

Current provenance values distinguish BLE advertisement, Classic discovery/SDP, active GATT/RFCOMM, user action, Follow-Me analysis and device registry evidence.

## Confidence semantics

| Level | Meaning |
| --- | --- |
| `LOW` | weak/contextual clue; useful for explanation, not strong attribution |
| `MEDIUM` | one meaningful clue or several weaker consistent clues |
| `HIGH` | strong or corroborated evidence for a device/classification claim |
| `CRITICAL` | reserved for explicit user-relevant/high-confidence conditions such as watchlist/user-confirmed context; never means certainty about a person or intent |

Confidence describes the **evidence-backed app classification**, not real-world certainty.

## Durable rules

- Name-only tactical/public-safety-like matching cannot exceed `MEDIUM`.
- Generic name/model/appearance/Class-of-Device/service clues are identity/classification context, not risk proof.
- RSSI cannot prove distance.
- Random/private address behavior lowers identity certainty unless stronger continuity evidence supports a merge.
- Identity carryover remains continuity context; user review uses `IdentityCarryoverVerdict` (`UNREVIEWED`, `CONFIRMED_SAME_DEVICE`, `FALSE_MATCH`, `INCONCLUSIVE`) rather than inflating risk confidence.
- Watchlist confidence applies to the saved app fingerprint, not to the real-world owner.
- Active GATT/RFCOMM evidence must be explicitly marked active.
- Follow-Me score/components/movement suppression/RSSI behavior use `FOLLOW_ME_ANALYSIS` provenance so they remain distinguishable from direct radio observations.
- Public-safety-like calibration (`Known Safe` / `False Positive`) is a local review decision, not proof that a real-world service/person is or is not present.
- High-attention Radar/Details state must have an explainable evidence path; `deviceType` alone is insufficient.

## UI language

Prefer cautious evidence wording:

- `Matches Axon-like Bluetooth signature`
- `Possible tracker behavior`
- `Watchlist device reappeared`

Avoid unsupported certainty:

- `Police nearby`
- `You are being tracked`
- `Distance: 3 m`

## Provenance and history

Latest-state evidence can be deterministically reconstructed from persisted device fields. Alert-relevant events and Follow-Me observations also have durable history. Preserve the distinction when reviewing/exporting a session: a current label and a historical alert event are not the same fact.

When changing confidence/scoring/classification rules, first capture a privacy-safe fixture where practical and replay existing regression data.
