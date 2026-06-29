package io.blueeye.feature.details

import io.blueeye.core.domain.evidence.DetectionEvidenceClassifier
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.TrackingStatus

internal object DetailsDecisionSignalClassifier {
    fun hasFollowMeSignal(device: Device): Boolean =
        device.trackingStatus != TrackingStatus.SAFE || device.followingScore >= FOLLOW_ME_REVIEW_THRESHOLD

    fun hasTrackerLikeSignal(device: Device): Boolean {
        return device.evidence.any { evidence ->
            DetectionEvidenceClassifier.isTrackerLikeEvidence(evidence)
        }
    }

    fun hasPublicSafetyLikeSignal(device: Device): Boolean {
        return device.evidence.any { evidence ->
            DetectionEvidenceClassifier.isPublicSafetyLikeEvidence(evidence)
        }
    }

    fun isReviewEvidence(evidence: DetectionEvidence): Boolean {
        return DetectionEvidenceClassifier.isAttentionConfidence(evidence.confidence)
    }

    fun strongestEvidence(device: Device): DetectionEvidence? {
        return device.evidence.maxByOrNull { DetectionEvidenceClassifier.confidencePriority(it.confidence) }
    }

    fun publicSafetyLabel(device: Device): String =
        device.evidence.firstOrNull(DetectionEvidenceClassifier::isPublicSafetyLikeEvidence)
            ?.parsedValue
            ?.replace('_', ' ')
            ?.lowercase()
            ?: "available evidence"

    private const val FOLLOW_ME_REVIEW_THRESHOLD = 51f
}
