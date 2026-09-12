package io.blueeye.core.model

/**
 * Lightweight domain contract for the Radar list.
 *
 * It intentionally excludes technical/GATT/history fields that are only needed by Details or
 * export flows. Evidence is reduced to the decision flags consumed by Radar sectioning.
 */
data class RadarDeviceSummary(
    val fingerprint: String,
    val macAddress: String,
    val technology: String,
    val name: String?,
    val deviceType: DeviceType,
    val vendorName: String?,
    val predictedModel: String?,
    val trackingStatus: TrackingStatus,
    val followingScore: Float,
    val isSafeBeacon: Boolean,
    val isInWatchlist: Boolean,
    val userAlias: String?,
    val isIgnoredForTracking: Boolean,
    val calibrationLabel: DeviceCalibrationLabel,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val rssi: Int,
    val txPower: Int?,
    val isConnectable: Boolean?,
    val evidenceSignals: RadarEvidenceSignals,
)

data class RadarEvidenceSignals(
    val hasWatchlistEvidence: Boolean,
    val hasTrackerLikeEvidence: Boolean,
    val hasPublicSafetyLikeEvidence: Boolean,
    val hasAttentionEvidence: Boolean,
    val hasAttentionFollowMeEvidence: Boolean,
)
