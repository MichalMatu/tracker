package io.blueeye.feature.radar.presentation

import io.blueeye.core.domain.evidence.DetectionEvidenceClassifier
import io.blueeye.core.model.Device
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.RadarDeviceSummary
import io.blueeye.core.model.RadarEvidenceSignals

/**
 * Maps the lightweight Radar domain contract to UI-ready card data.
 *
 * The Device overload is retained as a test compatibility adapter so the existing section contract
 * suite continues to assert the pre-P2C evidence semantics against the new flattened UI contract.
 */
object RadarUiMapper {
    fun mapToUi(
        device: RadarDeviceSummary,
        isNew: Boolean,
        activeProbeMac: String?,
    ): RadarUiItem {
        val isProbing =
            activeProbeMac != null &&
                (
                    device.macAddress.equals(activeProbeMac, ignoreCase = true) ||
                        device.fingerprint.equals(activeProbeMac, ignoreCase = true)
                )

        return RadarUiItem(
            fingerprint = device.fingerprint,
            displayName = RadarIdentityUiFormatter.displayName(device),
            vendorAndType = RadarUiFormatter.formatVendorAndType(device),
            signalInfo = RadarUiFormatter.formatSignalInfo(device),
            statusInfo = RadarUiFormatter.formatStatusInfo(device, isNew),
            icons = RadarUiFormatter.formatIcons(device),
            isNew = isNew,
            isInWatchlist = device.isInWatchlist,
            isIgnored = device.isIgnoredForTracking,
            nameColor =
                when {
                    device.isIgnoredForTracking -> RadarUiColorToken.SAFE
                    (System.currentTimeMillis() - device.firstSeenAt) > 180_000 -> RadarUiColorToken.SAFE
                    else -> RadarUiColorToken.PRIMARY
                },
            isProbing = isProbing,
            firstSeenAt = device.firstSeenAt,
            trackingStatus = device.trackingStatus,
            followingScore = device.followingScore,
            isSafeBeacon = device.isSafeBeacon,
            calibrationLabel = device.calibrationLabel,
            hasIdentitySignal = RadarIdentityUiFormatter.hasIdentitySignal(device),
            evidenceSignals = device.evidenceSignals,
        )
    }

    fun mapToUi(
        device: Device,
        isNew: Boolean,
        activeProbeMac: String?,
    ): RadarUiItem = mapToUi(device.toRadarSummaryForUi(), isNew, activeProbeMac)
}

internal fun Device.toRadarSummaryForUi(): RadarDeviceSummary =
    RadarDeviceSummary(
        fingerprint = fingerprint,
        macAddress = macAddress,
        technology = technology,
        name = name,
        deviceType = deviceType,
        vendorName = vendorName,
        predictedModel = predictedModel,
        trackingStatus = trackingStatus,
        followingScore = followingScore,
        isSafeBeacon = isSafeBeacon,
        isInWatchlist = isInWatchlist,
        userAlias = userAlias,
        isIgnoredForTracking = isIgnoredForTracking,
        calibrationLabel = calibrationLabel,
        firstSeenAt = firstSeenAt,
        lastSeenAt = lastSeenAt,
        rssi = rssi,
        txPower = txPower,
        isConnectable = isConnectable,
        evidenceSignals =
            RadarEvidenceSignals(
                hasWatchlistEvidence = evidence.any { it.source == EvidenceSource.WATCHLIST },
                hasTrackerLikeEvidence =
                    evidence.any(DetectionEvidenceClassifier::isTrackerLikeEvidence),
                hasPublicSafetyLikeEvidence =
                    evidence.any(DetectionEvidenceClassifier::isPublicSafetyLikeEvidence),
                hasAttentionEvidence =
                    evidence.any(DetectionEvidenceClassifier::isAttentionEvidence),
                hasAttentionFollowMeEvidence =
                    evidence.any {
                        it.source in followMeEvidenceSources &&
                            DetectionEvidenceClassifier.isAttentionConfidence(it.confidence)
                    },
            ),
    )

private val followMeEvidenceSources =
    setOf(
        EvidenceSource.FOLLOW_ME_SCORE,
        EvidenceSource.RSSI_PATTERN,
    )
