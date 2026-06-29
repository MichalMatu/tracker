package io.blueeye.feature.settings

import io.blueeye.core.domain.evidence.DetectionEvidenceClassifier
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.TrackingStatus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal enum class SessionExportReviewCategory(
    val reasonText: String,
) {
    WATCHLIST("Device is watchlisted or has watchlist evidence."),
    SUSPICIOUS("Tracker-like, Follow-Me, or RSSI-pattern evidence needs review."),
    PUBLIC_SAFETY("Signal is consistent with public-safety or tactical gear; not a confirmed presence."),
    NEARBY("Identified nearby device without attention evidence."),
    UNKNOWN_NOISE("Ignored, weak, or unidentified broadcast without attention evidence."),
}

internal fun Device.sessionExportReviewCategory(): SessionExportReviewCategory =
    when {
        hasWatchlistSignal() -> SessionExportReviewCategory.WATCHLIST
        isSessionReviewSuppressedByUser() -> SessionExportReviewCategory.UNKNOWN_NOISE
        hasSuspiciousTrackingSignal() -> SessionExportReviewCategory.SUSPICIOUS
        hasPublicSafetyLikeSignal() -> SessionExportReviewCategory.PUBLIC_SAFETY
        isUnknownNoise() -> SessionExportReviewCategory.UNKNOWN_NOISE
        else -> SessionExportReviewCategory.NEARBY
    }

internal fun List<Device>.sessionExportReviewCategoryCounts(): JsonObject =
    buildJsonObject {
        SessionExportReviewCategory.entries.forEach { category ->
            put(category.name, count { device -> device.sessionExportReviewCategory() == category })
        }
    }

internal fun Device.sessionExportReviewAction(reviewCategory: SessionExportReviewCategory): String =
    when {
        isSessionReviewSuppressedByUser() ->
            "Already suppressed by user calibration; change the verdict only if new evidence appears."
        hasReviewedIdentityCarryoverEvidence() ->
            "Identity carryover already reviewed as ${identityCarryoverVerdict.sessionReviewDisplayText}; " +
                "change the verdict only if new evidence appears."
        hasActionableIdentityCarryoverEvidence() ->
            "Review identity carryover before trusting merged history; mark Same Device or False Match."
        else -> reviewCategory.reviewActionText()
    }

private fun SessionExportReviewCategory.reviewActionText(): String =
    when (this) {
        SessionExportReviewCategory.WATCHLIST ->
            "Confirm whether the return alert was useful; edit Watchlist or mark Known Safe if alerts are unwanted."
        SessionExportReviewCategory.SUSPICIOUS ->
            "Compare movement history with your route, then mark Suspicious, False Positive, or Known Safe."
        SessionExportReviewCategory.PUBLIC_SAFETY ->
            "Treat as classification evidence only; keep Unknown unless you can verify the device context."
        SessionExportReviewCategory.NEARBY ->
            "No immediate action; mark Known Safe or add to Watchlist if you recognize it."
        SessionExportReviewCategory.UNKNOWN_NOISE ->
            "No immediate action; mark False Positive or Known Safe if this repeatedly appears as noise."
    }

private fun Device.hasWatchlistSignal(): Boolean =
    isInWatchlist ||
        evidence.any { item -> item.source == EvidenceSource.WATCHLIST }

private fun Device.hasSuspiciousTrackingSignal(): Boolean =
    calibrationLabel == DeviceCalibrationLabel.SUSPICIOUS ||
        evidence.any(DetectionEvidenceClassifier::isTrackerLikeEvidence) ||
        trackingStatus != TrackingStatus.SAFE ||
        followingScore >= SessionExportReviewThresholds.SUSPICIOUS_SCORE ||
        evidence.any { item ->
            item.source in FOLLOW_ME_EVIDENCE_SOURCES &&
                DetectionEvidenceClassifier.isAttentionConfidence(item.confidence)
        }

private fun Device.hasPublicSafetyLikeSignal(): Boolean {
    return evidence.any { item ->
        DetectionEvidenceClassifier.isPublicSafetyLikeEvidence(item)
    }
}

private fun Device.isUnknownNoise(): Boolean {
    val hasAttentionEvidence = evidence.any(DetectionEvidenceClassifier::isAttentionEvidence)
    val isSafeNoise = isSafeBeacon && !hasAttentionEvidence
    val isUnknownWithoutAttention = !hasExportIdentitySignal() && !hasAttentionEvidence
    return isSafeNoise || isUnknownWithoutAttention
}

private object SessionExportReviewThresholds {
    const val SUSPICIOUS_SCORE = 51f
}

private val FOLLOW_ME_EVIDENCE_SOURCES =
    setOf(
        EvidenceSource.FOLLOW_ME_SCORE,
        EvidenceSource.RSSI_PATTERN,
    )
