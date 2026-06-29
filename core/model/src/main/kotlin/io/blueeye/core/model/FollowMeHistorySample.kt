package io.blueeye.core.model

/**
 * Historical Follow-Me score snapshot for one device.
 */
data class FollowMeHistorySample(
    val timestamp: Long,
    val observedMac: String,
    val trackingStatus: TrackingStatus,
    val score: Float,
    val explanation: String?,
    val rssi: Int,
    val encounterCount: Int,
    val durationScore: Int,
    val rssiStabilityScore: Int,
    val deviceTypeScore: Int,
    val macBehaviorScore: Int,
    val encounterScore: Int,
    val userMoved: Boolean?,
    val baselineDevice: Boolean?,
)
