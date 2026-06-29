package io.blueeye.feature.radar.presentation

import io.blueeye.core.domain.evidence.DetectionEvidenceClassifier
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource

object RadarEvidenceUiFormatter {
    fun format(evidence: List<DetectionEvidence>): RadarEvidenceInfo {
        if (evidence.isEmpty()) return noAttentionEvidence()

        val sortedEvidence =
            evidence.sortedWith(
                compareByDescending<DetectionEvidence> {
                    DetectionEvidenceClassifier.confidencePriority(it.confidence)
                }
                    .thenBy { it.source.name },
            )
        val primaryEvidence = sortedEvidence.first()

        return RadarEvidenceInfo(
            confidenceText = primaryEvidence.confidence.label,
            confidenceColor = primaryEvidence.confidence.colorToken,
            primarySourceText = primaryEvidence.sourceText(),
            primaryReasonText = primaryEvidence.reasonSummaryText(),
            primaryValueText = primaryEvidence.valueText(),
            chips = sortedEvidence.take(MAX_EVIDENCE_CHIPS).map { it.toChipInfo() },
        )
    }

    private fun noAttentionEvidence(): RadarEvidenceInfo =
        RadarEvidenceInfo(
            confidenceText = "No attention evidence",
            confidenceColor = RadarUiColorToken.OUTLINE,
            primarySourceText = "Source: none",
            primaryReasonText = "No medium-or-higher confidence evidence is available for this device.",
            primaryValueText = null,
            chips =
                listOf(
                    RadarEvidenceChipInfo(
                        text = "No evidence",
                        color = RadarUiColorToken.OUTLINE,
                    ),
                ),
        )

    private fun DetectionEvidence.toChipInfo(): RadarEvidenceChipInfo =
        RadarEvidenceChipInfo(
            text = "${source.label} ${modeLabel()}",
            color = confidence.colorToken,
        )

    private fun DetectionEvidence.sourceText(): String = "Source: ${source.label} - ${modeLabel()}"

    private fun DetectionEvidence.reasonSummaryText(): String =
        if (source == EvidenceSource.IDENTITY_CARRYOVER) {
            "Rotating Bluetooth address matched an existing device record; review identity continuity in details."
        } else {
            reasonText
        }

    private fun DetectionEvidence.valueText(): String? {
        if (source == EvidenceSource.IDENTITY_CARRYOVER) return identityCarryoverValueText()

        val raw = rawValue?.trim().orEmpty()
        val parsed = parsedValue?.trim().orEmpty()
        return when {
            raw.isNotBlank() && parsed.isNotBlank() && raw != parsed -> "Value: $raw -> $parsed"
            raw.isNotBlank() -> "Value: $raw"
            parsed.isNotBlank() -> "Value: $parsed"
            else -> null
        }
    }

    private fun DetectionEvidence.identityCarryoverValueText(): String? {
        val raw = rawValue?.trim().orEmpty()
        val stableRecord = parsedValue?.substringBefore(';')?.trim().orEmpty()
        return when {
            raw.isNotBlank() && stableRecord.isNotBlank() -> "Alias: $raw -> $stableRecord"
            raw.isNotBlank() -> "Alias: $raw"
            stableRecord.isNotBlank() -> "Stable record: $stableRecord"
            else -> null
        }
    }

    private fun DetectionEvidence.modeLabel(): String =
        when {
            source == EvidenceSource.USER_CONFIRMATION -> USER_VERDICT_LABEL
            source == EvidenceSource.GATT_PROBE || source == EvidenceSource.RFCOMM_PROBE -> ACTIVE_LABEL
            provenance != EvidenceProvenance.UNKNOWN -> provenance.label
            isPassive -> PASSIVE_LABEL
            else -> ACTIVE_LABEL
        }

    private val DetectionConfidence.label: String
        get() =
            when (this) {
                DetectionConfidence.LOW -> "Low confidence"
                DetectionConfidence.MEDIUM -> "Medium confidence"
                DetectionConfidence.HIGH -> "High confidence"
                DetectionConfidence.CRITICAL -> "High confidence"
            }

    private val DetectionConfidence.colorToken: RadarUiColorToken
        get() =
            when (this) {
                DetectionConfidence.LOW -> RadarUiColorToken.OUTLINE
                DetectionConfidence.MEDIUM -> RadarUiColorToken.WARNING
                DetectionConfidence.HIGH -> RadarUiColorToken.SUSPICIOUS
                DetectionConfidence.CRITICAL -> RadarUiColorToken.SUSPICIOUS
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

    private val EvidenceProvenance.label: String
        get() =
            when (this) {
                EvidenceProvenance.BLE_ADVERTISEMENT -> "BLE ad"
                EvidenceProvenance.CLASSIC_DISCOVERY -> "Classic"
                EvidenceProvenance.CLASSIC_SDP -> "Classic SDP"
                EvidenceProvenance.ACTIVE_GATT -> "active GATT"
                EvidenceProvenance.ACTIVE_RFCOMM -> "active RFCOMM"
                EvidenceProvenance.USER_ACTION -> "user action"
                EvidenceProvenance.FOLLOW_ME_ANALYSIS -> "analysis"
                EvidenceProvenance.DEVICE_REGISTRY -> "registry"
                EvidenceProvenance.UNKNOWN -> PASSIVE_LABEL
            }

    private const val MAX_EVIDENCE_CHIPS = 3
    private const val PASSIVE_LABEL = "passive"
    private const val ACTIVE_LABEL = "active"
    private const val USER_VERDICT_LABEL = "verdict"
}
