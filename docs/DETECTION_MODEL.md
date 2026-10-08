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
- Physical `deviceType`, observed `ProtocolCapability` and tracking risk are separate dimensions. Find Hub, DULT, SmartThings Find, Eddystone or Fast Pair capability never changes hardware form factor by itself.
- FEAA is shared by Eddystone and Google Find Hub. Only a frame-valid payload establishes the protocol; current FHN regression coverage distinguishes 160-bit and 256-bit identity frames and rejects truncated variants.
- Shared GATT service lists are never sufficient evidence for destructive identity merging.
- RSSI cannot prove distance.
- Random/private address behavior lowers identity certainty unless stronger continuity evidence supports a merge.
- Identity carryover remains continuity context; user review uses `IdentityCarryoverVerdict` (`UNREVIEWED`, `CONFIRMED_SAME_DEVICE`, `FALSE_MATCH`, `INCONCLUSIVE`) rather than inflating risk confidence.
- Watchlist confidence applies to the saved app fingerprint, not to the real-world owner.
- Active GATT/RFCOMM evidence must be explicitly marked active.
- Follow-Me score/components/movement suppression/RSSI behavior use `FOLLOW_ME_ANALYSIS` provenance so they remain distinguishable from direct radio observations.
- Public-safety-like calibration (`Known Safe` / `False Positive`) is a local review decision, not proof that a real-world service/person is or is not present.
- High-attention Radar/Details state must have an explainable evidence path; `deviceType` alone is insufficient.
- Phone GPS is observation context, not device location. Location samples are usable only when coordinates are finite/in-range and reported accuracy is finite, greater than 0 m and at most 100 m.
- Missing/poor/invalid location must be rejected or represented as data-quality counts/flags rather than silently converted into a position.
- Reduced analysis/bundle output must not expose exact coordinates.

## Controlled field regression anchors — 2026-10-08

The following devices were explicitly confirmed by the operator as **owned/controlled** during the field session. They are ground truth for regression behavior, not universal fingerprints:

- **Garmin Forerunner 255 Music** — owned watch. Expected physical type: watch/wearable. Its presence is baseline context and must not become tracker risk solely because it moves with the phone.
- **Sony WF-1000XM5, firmware 6.1.0** — owned headphones. They can participate in Google Find Hub and Fast Pair. Expected behavior: remain `HEADPHONES` while carrying `FIND_HUB`/Fast Pair capability; Find Hub advertising alone must never reclassify them as `TRACKER`.
- **Lidl key tracker, Google Find Hub compatible** — owned dedicated key tracker. The exact retail/model identity was not recoverable from the export, so documentation must not invent one. The controlled field stream is consistent with FHN-160 behavior and is useful as a positive co-movement reference, but **FHN-160 alone is not proof that an arbitrary device is this tracker**.

Negative/counter examples from the same review:

- **Samsung Q60 Series TV** advertising Samsung manufacturer data `0x0075` / frame family `0x42` is not a SmartTag. SmartTag classification requires corroborating tag/model evidence.
- Two Find Hub identities observed at the same time must remain separate physical candidates. Rotation carryover is sequential evidence; concurrent FHN observations are counter-evidence for a merge.
- The Sony FHN-256 path and the controlled key-tracker FHN-160 path must remain distinct; frame width is a hard incompatibility for identity carryover.
- Poor GPS fixes (>100 m reported accuracy), missing accuracy or invalid coordinates do not establish Follow-Me movement.
- RSSI stability is supporting evidence only and is capped so a short weak encounter cannot outrank a substantially longer movement-correlated observation by stability alone.

No exact MAC addresses, GPS coordinates or private export records belong in repository fixtures or documentation. Preserve only privacy-safe shapes and behavior.

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
