package io.blueeye.feature.radar.presentation

import io.blueeye.core.domain.evidence.DetectionEvidenceClassifier
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.TrackingStatus

object RadarUiSectionMapper {
    fun map(items: List<RadarUiItem>): List<RadarUiSection> {
        if (items.isEmpty()) return emptyList()

        val buckets = RadarUiSectionType.entries.associateWith { mutableListOf<RadarUiItem>() }
        items.forEach { item ->
            buckets.getValue(item.sectionType()) += item
        }

        return RadarUiSectionType.entries.mapNotNull { type ->
            buckets.getValue(type)
                .takeIf { it.isNotEmpty() }
                ?.let { RadarUiSection(type = type, items = it.toList()) }
        }
    }

    private fun RadarUiItem.sectionType(): RadarUiSectionType =
        when {
            isWatchlistSignal() -> RadarUiSectionType.WATCHLIST
            isUserSuppressedNoise() -> RadarUiSectionType.UNKNOWN_NOISE
            hasSuspiciousTrackingSignal() -> RadarUiSectionType.SUSPICIOUS
            device.evidence.any(DetectionEvidenceClassifier::isPublicSafetyLikeEvidence) ->
                RadarUiSectionType.PUBLIC_SAFETY
            isUnknownNoise() -> RadarUiSectionType.UNKNOWN_NOISE
            else -> RadarUiSectionType.NEARBY
        }

    private fun RadarUiItem.isWatchlistSignal(): Boolean {
        val hasWatchlistEvidence = device.evidence.any { it.source == EvidenceSource.WATCHLIST }
        return isInWatchlist || hasWatchlistEvidence
    }

    private fun RadarUiItem.hasSuspiciousTrackingSignal(): Boolean =
        device.calibrationLabel == DeviceCalibrationLabel.SUSPICIOUS ||
            device.evidence.any(DetectionEvidenceClassifier::isTrackerLikeEvidence) ||
            device.trackingStatus != TrackingStatus.SAFE ||
            device.followingScore >= SUSPICIOUS_SCORE_THRESHOLD ||
            device.evidence.any {
                it.source in FOLLOW_ME_EVIDENCE_SOURCES &&
                    DetectionEvidenceClassifier.isAttentionConfidence(it.confidence)
            }

    private fun RadarUiItem.isUserSuppressedNoise(): Boolean =
        isIgnored ||
            device.isIgnoredForTracking ||
            device.calibrationLabel in USER_SUPPRESSED_CALIBRATION_LABELS

    private fun RadarUiItem.isUnknownNoise(): Boolean {
        val hasAttentionEvidence = device.hasAttentionEvidence()
        val isSafeNoise = device.isSafeBeacon && !hasAttentionEvidence
        val isUnknownWithoutAttention =
            !RadarIdentityUiFormatter.hasIdentitySignal(device) && !hasAttentionEvidence
        return isSafeNoise || isUnknownWithoutAttention
    }

    private fun Device.hasAttentionEvidence(): Boolean {
        return evidence.any(DetectionEvidenceClassifier::isAttentionEvidence)
    }

    private const val SUSPICIOUS_SCORE_THRESHOLD = 51f
    private val FOLLOW_ME_EVIDENCE_SOURCES =
        setOf(
            EvidenceSource.FOLLOW_ME_SCORE,
            EvidenceSource.RSSI_PATTERN,
        )
    private val USER_SUPPRESSED_CALIBRATION_LABELS =
        setOf(
            DeviceCalibrationLabel.FALSE_POSITIVE,
            DeviceCalibrationLabel.KNOWN_SAFE,
        )
}
