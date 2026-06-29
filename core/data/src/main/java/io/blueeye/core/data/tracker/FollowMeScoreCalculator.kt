package io.blueeye.core.data.tracker

import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.TrackingStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Follow-Me Score Calculator - Detects potential tracking devices.
 *
 * A device is considered suspicious if:
 * 1. It's been seen consistently over a long period while user is moving
 * 2. It's a known tracker type (AirTag, Tile, SmartTag)
 * 3. It has stable RSSI during a movement window
 * 4. It changes MAC address frequently but remains nearby
 *
 * Score range: 0-100
 * - 0-20: Safe (casual encounter)
 * - 21-50: Attention (worth monitoring)
 * - 51-75: Suspicious (possible follow-me pattern)
 * - 76-100: High attention (strong follow-me score)
 */
@Singleton
open class FollowMeScoreCalculator
@Inject
constructor(
    private val rssiAnalyzer: io.blueeye.core.data.tracker.analysis.RssiStabilityAnalyzer,
    private val riskFormatter: io.blueeye.core.data.tracker.formatter.RiskAssessmentFormatter,
) {
    companion object {
        private const val MS_PER_MINUTE = 60000

        // ========== THRESHOLDS ==========

        /** Minimum duration to consider for tracking (5 minutes) */
        private const val MIN_TRACKING_DURATION_MS = 5 * 60 * 1000L

        /** Strong tracking signal (10 minutes) */
        private const val STRONG_TRACKING_DURATION_MS = 10 * 60 * 1000L
        private const val EXTENDED_TRACKING_DURATION_MS = 30 * 60 * 1000L // 30 mins

        /** Minimum encounters for tracking detection */
        private const val MIN_ENCOUNTERS_FOR_TRACKING = 10
        private const val HIGH_ENCOUNTERS_THRESHOLD = 50
        private const val VERY_HIGH_ENCOUNTERS_THRESHOLD = 100

        // Scores
        private const val SCORE_DURATION_LOW = 15
        private const val SCORE_DURATION_MEDIUM = 25
        private const val SCORE_DURATION_MAX = 30

        private const val SCORE_RSSI_STABLE_THRESHOLD = 15

        private const val SCORE_TYPE_KNOWN_TRACKER = 20
        private const val SCORE_TYPE_BEACON = 10
        private const val THRESHOLD_MAC_CHANGES_HIGH = 5
        private const val THRESHOLD_MAC_CHANGES_MED = 3
        private const val THRESHOLD_MAC_CHANGES_LOW = 1
        private const val SCORE_MAC_HIGH = 15
        private const val SCORE_MAC_MED = 10
        private const val SCORE_MAC_LOW = 5

        private const val SCORE_ENCOUNTERS_MAX = 10
        private const val SCORE_ENCOUNTERS_MED = 7
        private const val SCORE_ENCOUNTERS_LOW = 4

        // Ranges
        private const val MAX_SCORE = 100
        private const val THRESHOLD_DANGEROUS = 76
        private const val THRESHOLD_SUSPICIOUS_HIGH = 51
    }

    /** Input data for score calculation. */
    data class DeviceMetrics(
        val deviceType: DeviceType,
        val firstSeenAt: Long,
        val lastSeenAt: Long,
        val encounterCount: Int,
        val rssiSamples: List<Int>, // Recent RSSI values
        val macChangeCount: Int = 0, // From AddressCarryoverTracker
        val isKnownTracker: Boolean = false, // AirTag, Tile, SmartTag
        val hasStablePayload: Boolean = false, // Payload fingerprint stable
        val userHasMoved: Boolean = true,
        val isBaselineDevice: Boolean = false,
        val movementTrackingAvailable: Boolean = true,
    )

    /** Score result with breakdown. */
    data class ScoreResult(
        val totalScore: Int,
        val status: TrackingStatus,
        val durationScore: Int,
        val rssiStabilityScore: Int,
        val deviceTypeScore: Int,
        val macBehaviorScore: Int,
        val encounterScore: Int,
        val explanation: String,
    )

    /** Calculate Follow-Me Score for a device. */
    fun calculateScore(
        metrics: DeviceMetrics,
        @Suppress("UNUSED_PARAMETER") currentTimeMs: Long = System.currentTimeMillis(),
    ): ScoreResult {
        var totalScore = 0
        val explanations = mutableListOf<String>()
        val canScoreFollowSignals =
            metrics.movementTrackingAvailable && metrics.userHasMoved && !metrics.isBaselineDevice

        // Known tracker type is device evidence, independent of motion/baseline context.
        val deviceTypeScore = calculateDeviceTypeScore(metrics, explanations)
        totalScore += deviceTypeScore

        val durationScore: Int
        val rssiStabilityScore: Int
        val macBehaviorScore: Int
        val encounterScore: Int

        if (canScoreFollowSignals) {
            // 1. Duration Score (0-30 points)
            val durationMs = metrics.lastSeenAt - metrics.firstSeenAt
            durationScore = calculateDurationScore(durationMs, metrics.encounterCount, explanations)
            totalScore += durationScore

            // 2. RSSI Stability Score (0-25 points)
            rssiStabilityScore = calculateRssiScore(metrics.rssiSamples, explanations)
            totalScore += rssiStabilityScore

            // 4. MAC Behavior Score (0-15 points)
            macBehaviorScore = calculateMacBehaviorScore(metrics, explanations)
            totalScore += macBehaviorScore

            // 5. Encounter Frequency Score (0-10 points)
            encounterScore = calculateEncounterScore(metrics.encounterCount)
            totalScore += encounterScore
        } else {
            durationScore = 0
            rssiStabilityScore = 0
            macBehaviorScore = 0
            encounterScore = 0
            explanations.add(
                when {
                    !metrics.movementTrackingAvailable ->
                        "Movement source unavailable - follow-me score suppressed"
                    !metrics.userHasMoved -> "Movement not detected - follow-me score suppressed"
                    else -> "Baseline device seen before movement - follow-me score suppressed"
                },
            )
        }

        // Cap at 100
        totalScore = totalScore.coerceIn(0, MAX_SCORE)

        // Determine status
        val status =
            when {
                totalScore >= THRESHOLD_DANGEROUS -> TrackingStatus.DANGEROUS
                totalScore >= THRESHOLD_SUSPICIOUS_HIGH -> TrackingStatus.SUSPICIOUS
                else -> TrackingStatus.SAFE
            }

        val explanation =
            if (explanations.isEmpty()) {
                "Low risk - casual encounter"
            } else {
                explanations.joinToString(", ")
            }

        return ScoreResult(
            totalScore = totalScore,
            status = status,
            durationScore = durationScore,
            rssiStabilityScore = rssiStabilityScore,
            deviceTypeScore = deviceTypeScore,
            macBehaviorScore = macBehaviorScore,
            encounterScore = encounterScore,
            explanation = explanation,
        )
    }

    /** Quick check if device should be monitored. */
    fun shouldMonitor(metrics: DeviceMetrics): Boolean {
        val duration = metrics.lastSeenAt - metrics.firstSeenAt
        val isLongDuration = duration >= MIN_TRACKING_DURATION_MS &&
            metrics.encounterCount >= MIN_ENCOUNTERS_FOR_TRACKING
        val canMonitorFollowSignals =
            metrics.movementTrackingAvailable && metrics.userHasMoved && !metrics.isBaselineDevice

        return metrics.isKnownTracker ||
            metrics.deviceType in listOf(DeviceType.AIRTAG, DeviceType.TILE, DeviceType.SAMSUNG_TAG) ||
            (canMonitorFollowSignals && isLongDuration) ||
            (canMonitorFollowSignals && metrics.macChangeCount >= 2 && metrics.hasStablePayload)
    }

    /** Put through formatter */
    fun getRiskLabel(score: Int): String = riskFormatter.getRiskLabel(score)

    /** Put through formatter */
    fun getRecommendedAction(score: Int): String = riskFormatter.getRecommendedAction(score)

    private fun calculateDurationScore(
        durationMs: Long,
        encounterCount: Int,
        explanations: MutableList<String>,
    ): Int {
        if (encounterCount < MIN_ENCOUNTERS_FOR_TRACKING) return 0

        val score = when {
            durationMs < MIN_TRACKING_DURATION_MS -> 0
            durationMs < STRONG_TRACKING_DURATION_MS -> SCORE_DURATION_LOW
            durationMs < EXTENDED_TRACKING_DURATION_MS -> SCORE_DURATION_MEDIUM
            else -> SCORE_DURATION_MAX
        }
        if (score > 0) {
            explanations.add("Seen for ${durationMs / MS_PER_MINUTE}min while moving")
        }
        return score
    }

    private fun calculateRssiScore(samples: List<Int>, explanations: MutableList<String>): Int {
        val score = rssiAnalyzer.calculateStabilityScore(samples)
        if (score > SCORE_RSSI_STABLE_THRESHOLD) {
            explanations.add("RSSI stayed stable during movement window")
        }
        return score
    }

    private fun calculateDeviceTypeScore(metrics: DeviceMetrics, explanations: MutableList<String>): Int {
        val score = when {
            metrics.isKnownTracker -> SCORE_TYPE_KNOWN_TRACKER
            metrics.deviceType == DeviceType.AIRTAG -> SCORE_TYPE_KNOWN_TRACKER
            metrics.deviceType == DeviceType.TILE -> SCORE_TYPE_KNOWN_TRACKER
            metrics.deviceType == DeviceType.SAMSUNG_TAG -> SCORE_TYPE_KNOWN_TRACKER
            metrics.deviceType == DeviceType.BEACON -> SCORE_TYPE_BEACON
            else -> 0
        }
        if (score >= SCORE_TYPE_KNOWN_TRACKER) {
            explanations.add("Known tracker type")
        }
        return score
    }

    private fun calculateMacBehaviorScore(metrics: DeviceMetrics, explanations: MutableList<String>): Int {
        if (!metrics.hasStablePayload) return 0

        val score = when {
            metrics.macChangeCount >= THRESHOLD_MAC_CHANGES_HIGH -> SCORE_MAC_HIGH
            metrics.macChangeCount >= THRESHOLD_MAC_CHANGES_MED -> SCORE_MAC_MED
            metrics.macChangeCount >= THRESHOLD_MAC_CHANGES_LOW -> SCORE_MAC_LOW
            else -> 0
        }
        if (score >= SCORE_MAC_MED) {
            explanations.add("Rotating MAC correlated with stable payload (${metrics.macChangeCount}x)")
        }
        return score
    }

    private fun calculateEncounterScore(count: Int): Int {
        return when {
            count >= VERY_HIGH_ENCOUNTERS_THRESHOLD -> SCORE_ENCOUNTERS_MAX
            count >= HIGH_ENCOUNTERS_THRESHOLD -> SCORE_ENCOUNTERS_MED
            count >= MIN_ENCOUNTERS_FOR_TRACKING -> SCORE_ENCOUNTERS_LOW
            else -> 0
        }
    }
}
