package io.blueeye.core.domain.evidence

import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.EvidenceSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionEvidenceClassifierTest {
    @Test
    fun `tracker evidence requires eligible source and attention confidence`() {
        assertTrue(
            DetectionEvidenceClassifier.isTrackerLikeEvidence(
                evidence(
                    source = EvidenceSource.NAME,
                    confidence = DetectionConfidence.MEDIUM,
                    parsedValue = "TRACKER",
                    reasonText = "Signal is consistent with a Bluetooth tracker accessory.",
                ),
            ),
        )
        assertFalse(
            DetectionEvidenceClassifier.isTrackerLikeEvidence(
                evidence(
                    source = EvidenceSource.USER_CONFIRMATION,
                    confidence = DetectionConfidence.HIGH,
                    parsedValue = "TRACKER",
                    reasonText = "User note mentions tracker.",
                ),
            ),
        )
        assertFalse(
            DetectionEvidenceClassifier.isTrackerLikeEvidence(
                evidence(
                    source = EvidenceSource.NAME,
                    confidence = DetectionConfidence.LOW,
                    parsedValue = "TRACKER",
                    reasonText = "Signal is consistent with a Bluetooth tracker accessory.",
                ),
            ),
        )
    }

    @Test
    fun `public safety evidence requires eligible source and non-low confidence`() {
        assertTrue(
            DetectionEvidenceClassifier.isPublicSafetyLikeEvidence(
                evidence(
                    source = EvidenceSource.MANUFACTURER_ID,
                    confidence = DetectionConfidence.MEDIUM,
                    parsedValue = "BODY_CAMERA",
                    reasonText = "Axon body camera signature.",
                ),
            ),
        )
        assertFalse(
            DetectionEvidenceClassifier.isPublicSafetyLikeEvidence(
                evidence(
                    source = EvidenceSource.RAW_PAYLOAD,
                    confidence = DetectionConfidence.HIGH,
                    parsedValue = "BODY_CAMERA",
                    reasonText = "Axon body camera signature.",
                ),
            ),
        )
        assertFalse(
            DetectionEvidenceClassifier.isPublicSafetyLikeEvidence(
                evidence(
                    source = EvidenceSource.MANUFACTURER_ID,
                    confidence = DetectionConfidence.LOW,
                    parsedValue = "BODY_CAMERA",
                    reasonText = "Axon body camera signature.",
                ),
            ),
        )
    }

    @Test
    fun `attention evidence starts at medium confidence`() {
        assertFalse(DetectionEvidenceClassifier.isAttentionEvidence(evidence(confidence = DetectionConfidence.LOW)))
        assertTrue(DetectionEvidenceClassifier.isAttentionEvidence(evidence(confidence = DetectionConfidence.MEDIUM)))
        assertTrue(DetectionEvidenceClassifier.isAttentionEvidence(evidence(confidence = DetectionConfidence.HIGH)))
        assertTrue(DetectionEvidenceClassifier.isAttentionEvidence(evidence(confidence = DetectionConfidence.CRITICAL)))
    }

    private fun evidence(
        source: EvidenceSource = EvidenceSource.NAME,
        confidence: DetectionConfidence,
        parsedValue: String? = null,
        reasonText: String = "Evidence needs review.",
    ): DetectionEvidence =
        DetectionEvidence(
            source = source,
            confidence = confidence,
            reasonText = reasonText,
            timestamp = NOW,
            rawValue = null,
            parsedValue = parsedValue,
            isPassive = true,
        )

    private companion object {
        private const val NOW = 1_789_000_000_000L
    }
}
