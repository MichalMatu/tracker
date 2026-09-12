package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.DeviceCalibrationLabel
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
            evidenceSignals.hasPublicSafetyLikeEvidence -> RadarUiSectionType.PUBLIC_SAFETY
            isUnknownNoise() -> RadarUiSectionType.UNKNOWN_NOISE
            else -> RadarUiSectionType.NEARBY
        }

    private fun RadarUiItem.isWatchlistSignal(): Boolean = isInWatchlist || evidenceSignals.hasWatchlistEvidence

    private fun RadarUiItem.hasSuspiciousTrackingSignal(): Boolean =
        calibrationLabel == DeviceCalibrationLabel.SUSPICIOUS ||
            evidenceSignals.hasTrackerLikeEvidence ||
            trackingStatus != TrackingStatus.SAFE ||
            followingScore >= SUSPICIOUS_SCORE_THRESHOLD ||
            evidenceSignals.hasAttentionFollowMeEvidence

    private fun RadarUiItem.isUserSuppressedNoise(): Boolean =
        isIgnored ||
            calibrationLabel in USER_SUPPRESSED_CALIBRATION_LABELS

    private fun RadarUiItem.isUnknownNoise(): Boolean {
        val hasAttentionEvidence = evidenceSignals.hasAttentionEvidence
        val isSafeNoise = isSafeBeacon && !hasAttentionEvidence
        val isUnknownWithoutAttention = !hasIdentitySignal && !hasAttentionEvidence
        return isSafeNoise || isUnknownWithoutAttention
    }

    private const val SUSPICIOUS_SCORE_THRESHOLD = 51f
    private val USER_SUPPRESSED_CALIBRATION_LABELS =
        setOf(
            DeviceCalibrationLabel.FALSE_POSITIVE,
            DeviceCalibrationLabel.KNOWN_SAFE,
        )
}
