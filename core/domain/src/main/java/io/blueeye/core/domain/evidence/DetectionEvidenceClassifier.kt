package io.blueeye.core.domain.evidence

import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.EvidenceSource

object DetectionEvidenceClassifier {
    fun isTrackerLikeEvidence(evidence: DetectionEvidence): Boolean {
        val isEligibleEvidence =
            isAttentionConfidence(evidence.confidence) &&
                evidence.source in TRACKER_EVIDENCE_SOURCES
        val parsed = evidence.parsedValue.orEmpty().uppercase()
        val reason = evidence.reasonText.lowercase()
        val matchesParsedValue = TRACKER_PARSED_VALUES.any { value -> parsed.contains(value) }
        val matchesReason = TRACKER_REASON_KEYWORDS.any { keyword -> reason.contains(keyword) }

        return isEligibleEvidence && (matchesParsedValue || matchesReason)
    }

    fun isPublicSafetyLikeEvidence(evidence: DetectionEvidence): Boolean {
        val isEligibleEvidence =
            evidence.confidence != DetectionConfidence.LOW &&
                evidence.source in PUBLIC_SAFETY_EVIDENCE_SOURCES
        val parsed = evidence.parsedValue.orEmpty().uppercase()
        val reason = evidence.reasonText.lowercase()
        val matchesParsedValue = PUBLIC_SAFETY_PARSED_VALUES.any { value -> parsed.contains(value) }
        val matchesReason = PUBLIC_SAFETY_REASON_KEYWORDS.any { keyword -> reason.contains(keyword) }

        return isEligibleEvidence && (matchesParsedValue || matchesReason)
    }

    fun isAttentionEvidence(evidence: DetectionEvidence): Boolean = isAttentionConfidence(evidence.confidence)

    fun isAttentionConfidence(confidence: DetectionConfidence): Boolean =
        confidencePriority(confidence) >= confidencePriority(DetectionConfidence.MEDIUM)

    fun confidencePriority(confidence: DetectionConfidence): Int = confidence.priority

    private val DetectionConfidence.priority: Int
        get() =
            when (this) {
                DetectionConfidence.LOW -> 1
                DetectionConfidence.MEDIUM -> 2
                DetectionConfidence.HIGH -> 3
                DetectionConfidence.CRITICAL -> 4
            }

    private val TRACKER_EVIDENCE_SOURCES =
        setOf(
            EvidenceSource.NAME,
            EvidenceSource.MANUFACTURER_ID,
            EvidenceSource.SERVICE_UUID,
            EvidenceSource.RAW_PAYLOAD,
        )

    private val TRACKER_PARSED_VALUES =
        listOf(
            "AIRTAG",
            "TILE",
            "SAMSUNG_TAG",
            "TAG",
            "TRACKER",
        )

    private val TRACKER_REASON_KEYWORDS =
        listOf(
            "bluetooth tracker",
            "tracker accessory",
        )

    private val PUBLIC_SAFETY_EVIDENCE_SOURCES =
        setOf(
            EvidenceSource.NAME,
            EvidenceSource.OUI,
            EvidenceSource.MANUFACTURER_ID,
            EvidenceSource.SERVICE_UUID,
        )

    private val PUBLIC_SAFETY_PARSED_VALUES =
        listOf(
            "BODY_CAMERA",
            "TACTICAL_RADIO",
            "VEHICLE_ROUTER",
            "TACTICAL_AUDIO",
            "HOLSTER_SENSOR",
            "SMART_WEAPON",
            "TACTICAL_EUD",
            "FIREFIGHTER",
        )

    private val PUBLIC_SAFETY_REASON_KEYWORDS =
        listOf(
            "axon",
            "body camera",
            "motorola solutions",
            "hytera",
            "tetra",
            "tactical",
            "public safety",
        )
}
