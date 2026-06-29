package io.blueeye.core.data.tracker.analysis

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analyzes RSSI (Received Signal Strength Indicator) samples to determine stability. Stable RSSI
 * is only proximity evidence; movement context decides whether it contributes to Follow-Me score.
 */
@Singleton
class RssiStabilityAnalyzer
@Inject
constructor() {
    companion object {
        /** Maximum RSSI variance for stable-signal detection (squared dBm). */
        const val MAX_RSSI_VARIANCE_FOR_TRACKING = 100.0

        private const val MIN_SAMPLES_FOR_ANALYSIS = 5
        private const val VAR_THRESHOLD_VERY_STABLE = 25.0
        private const val VAR_THRESHOLD_STABLE = 50.0
        private const val VAR_THRESHOLD_LOW_STABILITY = 200.0
        private const val VAR_THRESHOLD_NOISY = 400.0

        private const val SCORE_VERY_STABLE = 25
        private const val SCORE_STABLE = 20
        private const val SCORE_MEDIUM_STABILITY = 15
        private const val SCORE_LOW_STABILITY = 10
        private const val SCORE_VERY_LOW_STABILITY = 5
    }

    /**
     * Calculate RSSI stability score (0-25).
     *
     * Low variance = device maintained a similar signal level during the sampled window.
     */
    fun calculateStabilityScore(rssiSamples: List<Int>): Int {
        if (rssiSamples.size < MIN_SAMPLES_FOR_ANALYSIS) return 0

        val mean = rssiSamples.average()
        val variance = rssiSamples.map { (it - mean) * (it - mean) }.average()

        // Lower variance = higher score (more suspicious)
        return when {
            variance < VAR_THRESHOLD_VERY_STABLE -> SCORE_VERY_STABLE
            variance < VAR_THRESHOLD_STABLE -> SCORE_STABLE
            variance < MAX_RSSI_VARIANCE_FOR_TRACKING -> SCORE_MEDIUM_STABILITY
            variance < VAR_THRESHOLD_LOW_STABILITY -> SCORE_LOW_STABILITY
            variance < VAR_THRESHOLD_NOISY -> SCORE_VERY_LOW_STABILITY
            else -> 0 // Very noisy, not tracking
        }
    }
}
