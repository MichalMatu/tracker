package io.blueeye.core.alert

import io.blueeye.core.data.classifier.vendor.tactical.ConfidenceLevel
import io.blueeye.core.data.classifier.vendor.tactical.TacticalNameMatch
import io.blueeye.core.data.classifier.vendor.tactical.TacticalOuiInfo
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource

object TacticalEvidenceFactory {
    fun build(
        match: TacticalOuiInfo,
        source: EvidenceSource,
        rawValue: String,
        timestamp: Long,
        provenance: EvidenceProvenance,
    ): DetectionEvidence =
        DetectionEvidence(
            source = source,
            confidence = match.confidence.toDetectionConfidence(),
            reasonText =
                "${source.toReasonPrefix()} is consistent with ${match.vendorName}: ${match.description}.",
            timestamp = timestamp,
            rawValue = rawValue,
            parsedValue = "${match.category.name}: ${match.description}",
            isPassive = provenance.isPassiveEvidence,
            provenance = provenance,
        )

    fun buildName(
        match: TacticalNameMatch,
        rawValue: String,
        timestamp: Long,
    ): DetectionEvidence =
        DetectionEvidence(
            source = EvidenceSource.NAME,
            confidence = DetectionConfidence.MEDIUM,
            reasonText =
                "Device name is consistent with ${match.evidenceDescription}. " +
                    "Name-only signals are capped at medium confidence.",
            timestamp = timestamp,
            rawValue = rawValue,
            parsedValue = "${match.category.name}: ${match.evidenceDescription}",
            isPassive = true,
            provenance = EvidenceProvenance.BLE_ADVERTISEMENT,
        )

    private fun ConfidenceLevel.toDetectionConfidence(): DetectionConfidence =
        when (this) {
            ConfidenceLevel.CRITICAL -> DetectionConfidence.HIGH
            ConfidenceLevel.HIGH -> DetectionConfidence.HIGH
            ConfidenceLevel.MEDIUM -> DetectionConfidence.MEDIUM
        }

    private fun EvidenceSource.toReasonPrefix(): String =
        when (this) {
            EvidenceSource.OUI -> "MAC OUI"
            EvidenceSource.MANUFACTURER_ID -> "Manufacturer ID"
            EvidenceSource.SERVICE_UUID -> "Service UUID"
            EvidenceSource.RAW_PAYLOAD -> "Decoded advertisement payload"
            else -> "Bluetooth evidence"
        }

    private val EvidenceProvenance.isPassiveEvidence: Boolean
        get() =
            when (this) {
                EvidenceProvenance.ACTIVE_GATT,
                EvidenceProvenance.ACTIVE_RFCOMM,
                -> false
                EvidenceProvenance.UNKNOWN,
                EvidenceProvenance.BLE_ADVERTISEMENT,
                EvidenceProvenance.CLASSIC_DISCOVERY,
                EvidenceProvenance.CLASSIC_SDP,
                EvidenceProvenance.USER_ACTION,
                EvidenceProvenance.FOLLOW_ME_ANALYSIS,
                EvidenceProvenance.DEVICE_REGISTRY,
                -> true
            }
}
