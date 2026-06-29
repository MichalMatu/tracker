package io.blueeye.feature.details

import io.blueeye.core.domain.evidence.DetectionEvidenceClassifier
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource

object DetailsEvidenceUiFormatter {
    fun emptyState(): DetailsEvidenceEmptyState =
        DetailsEvidenceEmptyState(
            title = "No attention evidence",
            detail = "No medium-or-higher confidence evidence is available for this device.",
            modeText = "Passive scan review",
        )

    fun format(
        evidence: List<DetectionEvidence>,
        timestampFormatter: (Long) -> String = DetailsUiFormatter::formatFriendlyTimestamp,
    ): List<DetailsEvidenceUiItem> =
        evidence
            .sortedWith(
                compareByDescending<DetectionEvidence> {
                    DetectionEvidenceClassifier.confidencePriority(it.confidence)
                }
                    .thenBy { it.source.name }
                    .thenBy { it.provenance.name }
                    .thenBy { it.rawValue.orEmpty() },
            )
            .map { item ->
                DetailsEvidenceUiItem(
                    sourceText = item.source.label,
                    confidence = item.confidence,
                    confidenceText = item.confidence.labelFor(item.source),
                    modeText = item.modeText,
                    reasonText = item.reasonText,
                    observedAtText = timestampFormatter(item.timestamp),
                    rawValueText = item.rawValue.toDisplayValue(),
                    parsedValueText = item.parsedValue.toDisplayValue(),
                )
            }

    private fun String?.toDisplayValue(): String =
        if (isNullOrBlank()) {
            EMPTY_TECHNICAL_VALUE
        } else {
            this
        }

    private fun DetectionConfidence.labelFor(source: EvidenceSource): String =
        when {
            source == EvidenceSource.WATCHLIST && this == DetectionConfidence.CRITICAL -> "Watchlist alert"
            else -> label
        }

    private val DetectionEvidence.modeText: String
        get() =
            provenance.modeText ?: when {
                source == EvidenceSource.USER_CONFIRMATION -> USER_VERDICT_MODE_TEXT
                isPassive -> PASSIVE_MODE_TEXT
                else -> ACTIVE_MODE_TEXT
            }

    private val EvidenceProvenance.modeText: String?
        get() =
            when (this) {
                EvidenceProvenance.BLE_ADVERTISEMENT -> "BLE advertisement"
                EvidenceProvenance.CLASSIC_DISCOVERY -> "Classic discovery"
                EvidenceProvenance.CLASSIC_SDP -> "Classic SDP (opportunistic)"
                EvidenceProvenance.ACTIVE_GATT -> "Active GATT"
                EvidenceProvenance.ACTIVE_RFCOMM -> "Active RFCOMM"
                EvidenceProvenance.USER_ACTION -> USER_VERDICT_MODE_TEXT
                EvidenceProvenance.FOLLOW_ME_ANALYSIS -> "Follow-Me analysis"
                EvidenceProvenance.DEVICE_REGISTRY -> "Device registry"
                EvidenceProvenance.UNKNOWN -> null
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
                EvidenceSource.APPEARANCE -> "BLE appearance"
                EvidenceSource.CLASS_OF_DEVICE -> "Class of Device"
                EvidenceSource.OUI -> "OUI"
                EvidenceSource.MANUFACTURER_ID -> "Manufacturer ID"
                EvidenceSource.SERVICE_UUID -> "Service UUID"
                EvidenceSource.RAW_PAYLOAD -> "Raw payload"
                EvidenceSource.FOLLOW_ME_SCORE -> "Follow-Me score"
                EvidenceSource.RSSI_PATTERN -> "RSSI pattern"
                EvidenceSource.IDENTITY_CARRYOVER -> "Identity continuity"
                EvidenceSource.WATCHLIST -> "Watchlist"
                EvidenceSource.USER_CONFIRMATION -> "User confirmation"
                EvidenceSource.GATT_PROBE -> "GATT probe"
                EvidenceSource.RFCOMM_PROBE -> "RFCOMM probe"
            }

    private const val PASSIVE_MODE_TEXT = "Passive scan"
    private const val ACTIVE_MODE_TEXT = "Active probe"
    private const val USER_VERDICT_MODE_TEXT = "User verdict"
    private const val EMPTY_TECHNICAL_VALUE = "None"
}

data class DetailsEvidenceUiItem(
    val sourceText: String,
    val confidence: DetectionConfidence,
    val confidenceText: String,
    val modeText: String,
    val reasonText: String,
    val observedAtText: String,
    val rawValueText: String,
    val parsedValueText: String,
)

data class DetailsEvidenceEmptyState(
    val title: String,
    val detail: String,
    val modeText: String,
)
