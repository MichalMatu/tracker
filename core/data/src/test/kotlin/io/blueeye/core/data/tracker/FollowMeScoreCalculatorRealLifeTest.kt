package io.blueeye.core.data.tracker

import io.blueeye.core.data.tracker.analysis.RssiStabilityAnalyzer
import io.blueeye.core.data.tracker.formatter.RiskAssessmentFormatter
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Audit tests to check how the calculator behaves in "Real Life" edge cases.
 * Goal: Detect logical flaws or false positives.
 */
class FollowMeScoreCalculatorRealLifeTest {

    private val rssiAnalyzer: RssiStabilityAnalyzer = mock()
    private val riskFormatter: RiskAssessmentFormatter = mock()

    private val calculator = FollowMeScoreCalculator(rssiAnalyzer, riskFormatter)

    @Test
    fun `scenario The Commuter - Seen twice 30 mins apart (Low Encounters)`() {
        // Scenario: You see a car at home, then 30 mins later the same car passes you at work.
        // It's a "Long Duration" technically, but only 2 encounters.
        // Should NOT be suspicious.
        
        // Given
        val start = 1000L
        val end = start + 31 * 60 * 1000L // 31 minutes
        
        val metrics = FollowMeScoreCalculator.DeviceMetrics(
            deviceType = DeviceType.UNKNOWN,
            firstSeenAt = start,
            lastSeenAt = end,
            encounterCount = 2, // Only 2 pings
            rssiSamples = listOf(-80, -80)
        )
        whenever(rssiAnalyzer.calculateStabilityScore(any())).doReturn(0) // Not enough samples for stability

        // When
        val result = calculator.calculateScore(metrics)

        // Analysis:
        // Duration > 30m but encounters < 10 -> 0 duration points.
        // Two distant pings are coincidence-level evidence, not continuous follow-me evidence.
        //
        // Logic Justification: Only 2 pings 30 mins apart is likely coincidence,
        // not a continuous follow-me pattern.

        println("The Commuter Score: ${result.totalScore} -> ${result.status}")
        
        assertEquals(0, result.durationScore)
        assertEquals(0, result.totalScore)
        assertEquals(TrackingStatus.SAFE, result.status)
        assertFalse(result.explanation.contains("Seen for"))
    }

    @Test
    fun `scenario The Bus Neighbor - Random iPhone on bus for 15 mins`() {
        // Scenario: You sit next to a stranger on a bus for 15 mins.
        // Signal is stable, duration is medium, it's an Apple device (privacy random).
        
        // Given
        val start = 1000L
        val end = start + 15 * 60 * 1000L // 15 minutes
        
        val metrics = FollowMeScoreCalculator.DeviceMetrics(
            deviceType = DeviceType.UNKNOWN,
            firstSeenAt = start,
            lastSeenAt = end,
            encounterCount = 20, // Pinged continuously
            rssiSamples = listOf(-50, -52, -51), // Stable
            userHasMoved = true,
        )
        // Mock stable RSSI during the shared movement window on a bus.
        whenever(rssiAnalyzer.calculateStabilityScore(any())).doReturn(20)

        // When
        val result = calculator.calculateScore(metrics)

        // Analysis
        // Duration (10-30m) -> 25 pts (SCORE_DURATION_MEDIUM)
        // RSSI Stable -> 20 pts
        // Encounters (>10) -> 4 pts
        // Total = 49 pts
        // Status = SAFE: bus rides are a common moving false positive unless
        // there is stronger evidence such as known tracker type or correlated MAC rotation.

        println("The Bus Neighbor Score: ${result.totalScore} -> ${result.status}")
        
        assertEquals(49, result.totalScore)
        assertEquals(TrackingStatus.SAFE, result.status)
    }
}
