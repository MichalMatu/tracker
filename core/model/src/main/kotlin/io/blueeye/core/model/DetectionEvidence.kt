package io.blueeye.core.model

import kotlinx.serialization.Serializable

@Serializable
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

@Serializable
enum class DetectionConfidence {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

@Serializable
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

@Serializable
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
