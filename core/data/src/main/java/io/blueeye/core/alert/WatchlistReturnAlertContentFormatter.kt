package io.blueeye.core.alert

import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.EvidenceSource

internal data class WatchlistReturnAlertContent(
    val title: String,
    val body: String,
)

internal object WatchlistReturnAlertContentFormatter {
    fun format(
        mac: String,
        rssi: Int,
        evidence: DetectionEvidence,
    ): WatchlistReturnAlertContent {
        val displayName = evidence.parsedValue?.takeIf { it.isNotBlank() } ?: mac
        return WatchlistReturnAlertContent(
            title = "Watchlist device returned",
            body =
                "$displayName returned to Bluetooth range. " +
                    "Confidence: ${evidence.confidence.label}. " +
                    "Source: ${evidence.source.label} - ${evidence.modeLabel()}. " +
                    "Evidence: ${evidence.reasonText.trim().trimEnd('.')}. " +
                    "RSSI: $rssi dBm. Open BlueEye to review.",
        )
    }

    private fun DetectionEvidence.modeLabel(): String =
        when {
            source == EvidenceSource.USER_CONFIRMATION -> "verdict"
            isPassive -> "passive"
            else -> "active"
        }

    private val DetectionConfidence.label: String
        get() =
            when (this) {
                DetectionConfidence.LOW -> "Low confidence"
                DetectionConfidence.MEDIUM -> "Medium confidence"
                DetectionConfidence.HIGH -> "High confidence"
                DetectionConfidence.CRITICAL -> "High confidence"
            }

    private val EvidenceSource.label: String
        get() =
            when (this) {
                EvidenceSource.NAME -> "Name"
                EvidenceSource.MODEL -> "Model"
                EvidenceSource.APPEARANCE -> "Appearance"
                EvidenceSource.CLASS_OF_DEVICE -> "Class"
                EvidenceSource.OUI -> "OUI"
                EvidenceSource.MANUFACTURER_ID -> "Manufacturer"
                EvidenceSource.SERVICE_UUID -> "Service"
                EvidenceSource.RAW_PAYLOAD -> "Payload"
                EvidenceSource.FOLLOW_ME_SCORE -> "Follow-Me"
                EvidenceSource.RSSI_PATTERN -> "RSSI"
                EvidenceSource.IDENTITY_CARRYOVER -> "Identity"
                EvidenceSource.WATCHLIST -> "Watchlist"
                EvidenceSource.USER_CONFIRMATION -> "User"
                EvidenceSource.GATT_PROBE -> "GATT"
                EvidenceSource.RFCOMM_PROBE -> "RFCOMM"
            }
}
