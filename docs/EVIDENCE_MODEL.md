# Evidence Model

Evidence is now a first-class domain model passed from the data pipeline into
`Device`.

## Implemented Domain Types

Location: `core/model/src/main/kotlin/io/blueeye/core/model/DetectionEvidence.kt`

```kotlin
enum class EvidenceSource {
    NAME,
    MODEL,
    APPEARANCE,
    CLASS_OF_DEVICE,
    OUI,
    MANUFACTURER_ID,
    SERVICE_UUID,
    RAW_PAYLOAD,
    FOLLOW_ME_SCORE,
    RSSI_PATTERN,
    IDENTITY_CARRYOVER,
    WATCHLIST,
    USER_CONFIRMATION,
    GATT_PROBE,
    RFCOMM_PROBE,
}

enum class DetectionConfidence {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

enum class EvidenceProvenance {
    UNKNOWN,
    BLE_ADVERTISEMENT,
    CLASSIC_DISCOVERY,
    CLASSIC_SDP,
    ACTIVE_GATT,
    ACTIVE_RFCOMM,
    USER_ACTION,
    FOLLOW_ME_ANALYSIS,
    DEVICE_REGISTRY,
}

data class DetectionEvidence(
    val source: EvidenceSource,
    val confidence: DetectionConfidence,
    val reasonText: String,
    val timestamp: Long,
    val rawValue: String?,
    val parsedValue: String?,
    val isPassive: Boolean,
    val provenance: EvidenceProvenance = EvidenceProvenance.UNKNOWN,
)
```

`Device` now exposes:

```kotlin
val evidence: List<DetectionEvidence> = emptyList()
```

Evidence is currently built by
`core/data/src/main/java/io/blueeye/core/data/evidence/DeviceEvidenceFactory.kt`
from persisted `DeviceEntity` fields and injected into the domain model in
`DeviceMapper`.

## Device-Level Contract

Each device shown in UI should have:

- stable app fingerprint,
- current display label,
- device type label,
- confidence,
- list of evidence items,
- first seen and last seen,
- RSSI samples,
- address behavior summary,
- watchlist state,
- active-probe state if probing was used.

## Current Evidence Sources

| Source | Meaning | Passive |
| --- | --- | --- |
| `WATCHLIST` | User selected this saved fingerprint for alerts | yes |
| `NAME` | Device advertised a Bluetooth name or name-like tactical match | yes |
| `MODEL` | Model metadata was inferred from passive scan metadata or active device-info data | depends on `provenance` |
| `APPEARANCE` | BLE appearance value was decoded from the advertisement | yes |
| `CLASS_OF_DEVICE` | Classic Bluetooth Class of Device was observed during discovery | yes |
| `OUI` | Public MAC prefix matched registry metadata | yes |
| `MANUFACTURER_ID` | BLE manufacturer/company identifier matched registry metadata | yes |
| `SERVICE_UUID` | Service UUID observed through BLE advertisement, Classic SDP, or active GATT | depends on `provenance` |
| `RAW_PAYLOAD` | Advertisement payload was decoded into a known beacon type | yes |
| `FOLLOW_ME_SCORE` | Follow-Me score/status produced a behavioral clue | yes |
| `RSSI_PATTERN` | Follow-Me score/status produced a behavioral clue | yes |
| `IDENTITY_CARRYOVER` | Rotating Bluetooth address was correlated with an existing device record | yes |
| `GATT_PROBE` | Active GATT probe returned device information | no |
| `RFCOMM_PROBE` | Active RFCOMM probe changed connection status | no |

## Example Evidence

```kotlin
DetectionEvidence(
    source = EvidenceSource.MANUFACTURER_ID,
    confidence = DetectionConfidence.HIGH,
    reasonText = "Manufacturer ID is consistent with Axon.",
    timestamp = timestamp,
    rawValue = "0x034D",
    parsedValue = "BODY_CAMERA",
    isPassive = true,
)
```

## Implemented Rules

- Name-only tactical matching is capped at `MEDIUM`.
- Watchlist evidence can be `CRITICAL` because it is user-selected.
- OUI confidence follows the registry confidence.
- Manufacturer ID and service UUID tactical matches are `HIGH`.
- Active probe evidence is marked with `isPassive = false` and provenance
  `ACTIVE_GATT` or `ACTIVE_RFCOMM`.
- Service UUID evidence preserves provenance: `BLE_ADVERTISEMENT`,
  `CLASSIC_SDP`, or `ACTIVE_GATT`. Classic SDP is described as
  opportunistic because Android may omit it while Bluetooth resources are busy
  or the device is not in a discovery-friendly state.
- Name/manufacturer/raw payload evidence uses `BLE_ADVERTISEMENT`; OUI evidence
  uses `DEVICE_REGISTRY`; user calibration evidence uses `USER_ACTION`.
- Plain watchlist membership evidence uses `USER_ACTION`; watchlist return
  alert evidence uses `BLE_ADVERTISEMENT` because the return is observed by
  passive scanning.
- Non-tactical name and service UUID matches may carry generic device-type
  context, but stay `LOW`/`MEDIUM` and explicitly say they are classification
  context, not risk signals.
- Model evidence is low-confidence identity context; it explains why a
  predicted model is shown, but it does not become an attention signal.
- BLE appearance evidence is low-confidence classification context decoded from
  passive advertising bytes; it does not become an attention or risk signal.
- Classic Bluetooth Class of Device evidence is low-confidence classification
  context from discovery metadata; it does not become an attention or risk
  signal.
- Follow-Me score, score component, movement suppression, RSSI pattern, and
  Follow-Me alert evidence uses `FOLLOW_ME_ANALYSIS` provenance so UI/export
  can distinguish behavioral analysis from direct Bluetooth observations.
- Public-safety-like session review actions are calibration labels for repeated
  benign context (`Known Safe` / `False Positive`), not evidence that confirms
  or disproves a real-world public-safety presence.
- Identity/carryover evidence uses `IDENTITY_CARRYOVER` with `FOLLOW_ME_ANALYSIS` provenance and stays `LOW` because it is continuity context, not risk proof. When available, it includes the persisted matcher reason, matcher confidence, compact feature summary, and reviewed carryover verdict context. User review of the merge is stored separately as `IdentityCarryoverVerdict` (`UNREVIEWED`, `CONFIRMED_SAME_DEVICE`, `FALSE_MATCH`, `INCONCLUSIVE`) so a carryover verdict does not masquerade as a device-wide safety verdict.
- Session export schema `16` carries decoded scanner/enricher facts as
  `decodedSignal` summaries, including beacon type, formatted sensor data, raw
  payload presence, and decoded-signal kind counts.
- Evidence is sorted by confidence, source, provenance, and raw value for
  deterministic UI output.

## Design Rule

UI must render the evidence list or a summarized reason near every high-risk label. No high-risk badge should appear without explanation. `deviceType` alone is not enough to route a device into a high-attention Radar or Session Review section.

## Remaining Work

- Add review affordances for accepting/rejecting questionable Follow-Me and
  identity/carryover evidence during session calibration.
- Continue improving session export/review so historical alert evidence and
  latest-state evidence can be compared without reading logcat.
- Scanner/classifier should eventually emit evidence directly instead of deriving
  all evidence from persisted `DeviceEntity` fields.
