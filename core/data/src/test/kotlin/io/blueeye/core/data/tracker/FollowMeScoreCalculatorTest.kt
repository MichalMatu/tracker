package io.blueeye.core.data.tracker

import io.blueeye.core.data.tracker.analysis.RssiStabilityAnalyzer
import io.blueeye.core.data.tracker.formatter.RiskAssessmentFormatter
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FollowMeScoreCalculatorTest {

    private val rssiAnalyzer: RssiStabilityAnalyzer = mock()
    private val riskFormatter: RiskAssessmentFormatter = mock()

    private val calculator = FollowMeScoreCalculator(rssiAnalyzer, riskFormatter)

    @Test
    fun `calculateScore SHOULD return 0 for short casual encounter`() {
        val metrics = FollowMeScoreCalculator.DeviceMetrics(
            deviceType = DeviceType.UNKNOWN,
            firstSeenAt = 1000L,
            lastSeenAt = 2000L,
            encounterCount = 1,
            rssiSamples = listOf(-80, -82),
        )
        whenever(rssiAnalyzer.calculateStabilityScore(any())).doReturn(0)

        val result = calculator.calculateScore(metrics)

        assertEquals(0, result.totalScore)
        assertEquals(TrackingStatus.SAFE, result.status)
    }

    @Test
    fun `calculateScore SHOULD give points for Known Tracker Type`() {
        val metrics = FollowMeScoreCalculator.DeviceMetrics(
            deviceType = DeviceType.AIRTAG,
            firstSeenAt = 1000L,
            lastSeenAt = 2000L,
            encounterCount = 1,
            rssiSamples = listOf(-80),
        )
        whenever(rssiAnalyzer.calculateStabilityScore(any())).doReturn(0)

        val result = calculator.calculateScore(metrics)

        assertEquals(20, result.deviceTypeScore)
        assertEquals(20, result.totalScore)
    }

    @Test
    fun `calculateScore SHOULD give max points for Long Duration`() {
        val start = 1000L
        val end = start + 1_900_000L
        val metrics = FollowMeScoreCalculator.DeviceMetrics(
            deviceType = DeviceType.UNKNOWN,
            firstSeenAt = start,
            lastSeenAt = end,
            encounterCount = 15,
            rssiSamples = emptyList(),
        )
        whenever(rssiAnalyzer.calculateStabilityScore(any())).doReturn(0)

        val result = calculator.calculateScore(metrics)

        assertEquals(30, result.durationScore)
        assertEquals(4, result.encounterScore)
        assertEquals(34, result.totalScore)
    }

    @Test
    fun `explicit moving duration ignores long wall clock absence`() {
        val metrics = FollowMeScoreCalculator.DeviceMetrics(
            deviceType = DeviceType.UNKNOWN,
            firstSeenAt = 1_000L,
            lastSeenAt = 3_601_000L,
            encounterCount = 100,
            rssiSamples = emptyList(),
            userHasMoved = true,
            observedWhileMovingDurationMs = 4 * 60_000L,
        )
        whenever(rssiAnalyzer.calculateStabilityScore(any())).doReturn(0)

        val result = calculator.calculateScore(metrics)

        assertEquals(0, result.durationScore)
        assertEquals(10, result.encounterScore)
        assertEquals(10, result.totalScore)
        assertFalse(result.explanation.contains("Observed for"))
    }

    @Test
    fun `explicit moving duration scores only accumulated observed movement`() {
        val metrics = FollowMeScoreCalculator.DeviceMetrics(
            deviceType = DeviceType.UNKNOWN,
            firstSeenAt = 1_000L,
            lastSeenAt = 3_601_000L,
            encounterCount = 10,
            rssiSamples = emptyList(),
            userHasMoved = true,
            observedWhileMovingDurationMs = 6 * 60_000L,
        )
        whenever(rssiAnalyzer.calculateStabilityScore(any())).doReturn(0)

        val result = calculator.calculateScore(metrics)

        assertEquals(15, result.durationScore)
        assertEquals(4, result.encounterScore)
        assertEquals(19, result.totalScore)
        assertTrue(result.explanation.contains("Observed for 6min while moving"))
    }

    @Test
    fun `calculateScore suppresses follow signals when user has not moved`() {
        val metrics = FollowMeScoreCalculator.DeviceMetrics(
            deviceType = DeviceType.UNKNOWN,
            firstSeenAt = 1000L,
            lastSeenAt = 1_901_000L,
            encounterCount = 100,
            rssiSamples = listOf(-50, -50, -51, -50, -50),
            macChangeCount = 5,
            hasStablePayload = true,
            userHasMoved = false,
        )
        whenever(rssiAnalyzer.calculateStabilityScore(any())).doReturn(25)

        val result = calculator.calculateScore(metrics)

        assertEquals(0, result.totalScore)
        assertEquals(TrackingStatus.SAFE, result.status)
        assertEquals(0, result.durationScore)
        assertEquals(0, result.rssiStabilityScore)
        assertEquals(0, result.macBehaviorScore)
        assertEquals(0, result.encounterScore)
        assertTrue(result.explanation.contains("Movement not detected"))
    }

    @Test
    fun `calculateScore reports unavailable movement source separately`() {
        val metrics = FollowMeScoreCalculator.DeviceMetrics(
            deviceType = DeviceType.UNKNOWN,
            firstSeenAt = 1000L,
            lastSeenAt = 1_901_000L,
            encounterCount = 100,
            rssiSamples = listOf(-50, -50, -51, -50, -50),
            macChangeCount = 5,
            hasStablePayload = true,
            userHasMoved = false,
            movementTrackingAvailable = false,
        )
        whenever(rssiAnalyzer.calculateStabilityScore(any())).doReturn(25)

        val result = calculator.calculateScore(metrics)

        assertEquals(0, result.totalScore)
        assertEquals(TrackingStatus.SAFE, result.status)
        assertTrue(result.explanation.contains("Movement source unavailable"))
        assertFalse(result.explanation.contains("Movement not detected"))
    }

    @Test
    fun `calculateScore suppresses baseline devices after movement`() {
        val metrics = FollowMeScoreCalculator.DeviceMetrics(
            deviceType = DeviceType.UNKNOWN,
            firstSeenAt = 1000L,
            lastSeenAt = 1_901_000L,
            encounterCount = 100,
            rssiSamples = listOf(-50, -50, -51, -50, -50),
            macChangeCount = 5,
            hasStablePayload = true,
            userHasMoved = true,
            isBaselineDevice = true,
        )
        whenever(rssiAnalyzer.calculateStabilityScore(any())).doReturn(25)

        val result = calculator.calculateScore(metrics)

        assertEquals(0, result.totalScore)
        assertEquals(TrackingStatus.SAFE, result.status)
        assertTrue(result.explanation.contains("Baseline device"))
    }

    @Test
    fun `calculateScore keeps known tracker evidence even without movement`() {
        val metrics = FollowMeScoreCalculator.DeviceMetrics(
            deviceType = DeviceType.AIRTAG,
            firstSeenAt = 1000L,
            lastSeenAt = 1_901_000L,
            encounterCount = 100,
            rssiSamples = listOf(-50, -50, -51, -50, -50),
            userHasMoved = false,
        )
        whenever(rssiAnalyzer.calculateStabilityScore(any())).doReturn(25)

        val result = calculator.calculateScore(metrics)

        assertEquals(20, result.totalScore)
        assertEquals(TrackingStatus.SAFE, result.status)
        assertTrue(result.explanation.contains("Known tracker type"))
        assertTrue(result.explanation.contains("Movement not detected"))
    }

    @Test
    fun `calculateScore requires stable payload for MAC rotation score`() {
        val metrics = FollowMeScoreCalculator.DeviceMetrics(
            deviceType = DeviceType.UNKNOWN,
            firstSeenAt = 1000L,
            lastSeenAt = 11 * 60 * 1000L,
            encounterCount = 20,
            rssiSamples = listOf(-62, -63, -62, -63, -62),
            macChangeCount = 3,
            hasStablePayload = false,
            userHasMoved = true,
        )
        whenever(rssiAnalyzer.calculateStabilityScore(any())).doReturn(0)

        val result = calculator.calculateScore(metrics)

        assertEquals(0, result.macBehaviorScore)
        assertFalse(result.explanation.contains("Rotating MAC"))
    }

    @Test
    fun `calculateScore explains correlated rotating MAC`() {
        val metrics = FollowMeScoreCalculator.DeviceMetrics(
            deviceType = DeviceType.UNKNOWN,
            firstSeenAt = 1000L,
            lastSeenAt = 11 * 60 * 1000L,
            encounterCount = 20,
            rssiSamples = listOf(-62, -63, -62, -63, -62),
            macChangeCount = 3,
            hasStablePayload = true,
            userHasMoved = true,
        )
        whenever(rssiAnalyzer.calculateStabilityScore(any())).doReturn(0)

        val result = calculator.calculateScore(metrics)

        assertEquals(10, result.macBehaviorScore)
        assertTrue(result.explanation.contains("Rotating MAC correlated with stable payload"))
    }

    @Test
    fun `calculateScore SHOULD identify DANGEROUS device`() {
        val start = 1000L
        val end = start + 1_900_000L
        val metrics = FollowMeScoreCalculator.DeviceMetrics(
            deviceType = DeviceType.TILE,
            firstSeenAt = start,
            lastSeenAt = end,
            encounterCount = 100,
            rssiSamples = listOf(-50, -50, -50),
            macChangeCount = 5,
            hasStablePayload = true,
        )
        whenever(rssiAnalyzer.calculateStabilityScore(any())).doReturn(20)

        val result = calculator.calculateScore(metrics)

        assertEquals(95, result.totalScore)
        assertEquals(TrackingStatus.DANGEROUS, result.status)
        assertTrue(result.explanation.contains("Known tracker type"))
        assertTrue(result.explanation.contains("RSSI stayed stable during movement window"))
        assertFalse(result.explanation.contains("moving together"))
    }

    @Test
    fun `shouldMonitor ignores long wall clock span when observed moving duration is short`() {
        val metrics = FollowMeScoreCalculator.DeviceMetrics(
            deviceType = DeviceType.UNKNOWN,
            firstSeenAt = 1_000L,
            lastSeenAt = 3_601_000L,
            encounterCount = 100,
            rssiSamples = emptyList(),
            userHasMoved = true,
            observedWhileMovingDurationMs = 4 * 60_000L,
        )

        assertFalse(calculator.shouldMonitor(metrics))
    }

    @Test
    fun `shouldMonitor ignores long stationary baseline device`() {
        val metrics = FollowMeScoreCalculator.DeviceMetrics(
            deviceType = DeviceType.UNKNOWN,
            firstSeenAt = 1000L,
            lastSeenAt = 1_901_000L,
            encounterCount = 100,
            rssiSamples = listOf(-50, -50, -51, -50, -50),
            macChangeCount = 5,
            hasStablePayload = true,
            userHasMoved = true,
            isBaselineDevice = true,
        )

        assertFalse(calculator.shouldMonitor(metrics))
    }

    @Test
    fun `shouldMonitor keeps known tracker even before movement`() {
        val metrics = FollowMeScoreCalculator.DeviceMetrics(
            deviceType = DeviceType.TILE,
            firstSeenAt = 1000L,
            lastSeenAt = 2000L,
            encounterCount = 1,
            rssiSamples = listOf(-80),
            userHasMoved = false,
        )

        assertTrue(calculator.shouldMonitor(metrics))
    }
}
