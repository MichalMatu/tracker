package io.blueeye.feature.details

import io.blueeye.core.model.FollowMeHistorySample
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DetailsFollowMeHistoryFormatterTest {
    @Test
    fun `format returns null for empty history`() {
        assertNull(DetailsFollowMeHistoryFormatter.format(emptyList()))
    }

    @Test
    fun `format summarizes latest peak and strongest components`() {
        val info =
            DetailsFollowMeHistoryFormatter.format(
                samples =
                    listOf(
                        sample(
                            FollowMeSampleSpec(
                                timestamp = NOW,
                                status = TrackingStatus.SAFE,
                                score = 10f,
                                explanation = "Initial baseline",
                            ),
                        ),
                        sample(
                            FollowMeSampleSpec(
                                timestamp = NOW + ONE_MINUTE,
                                status = TrackingStatus.SUSPICIOUS,
                                score = 58f,
                                explanation = "Seen while moving",
                                durationScore = 25,
                                rssiStabilityScore = 18,
                                encounterScore = 4,
                                userMoved = true,
                                baselineDevice = false,
                            ),
                        ),
                    ),
                timestampFormatter = { it.toString() },
            )

        assertNotNull(info)
        requireNotNull(info)
        assertEquals("2 score snapshots", info.sampleCountText)
        assertEquals("First $NOW / latest ${NOW + ONE_MINUTE}", info.windowText)
        assertEquals("Latest 58/100", info.latestScoreText)
        assertEquals("Peak 58/100", info.peakScoreText)
        assertEquals(TrackingStatus.SUSPICIOUS, info.latestStatus)
        assertEquals("Suspicious", info.latestStatusText)
        assertEquals("movement observed / not baseline", info.movementText)
        assertEquals("duration +25, RSSI +18, encounters +4", info.componentText)
        assertEquals("Seen while moving", info.latestExplanation)
        assertEquals(2, info.recentItems.size)
    }

    private fun sample(spec: FollowMeSampleSpec): FollowMeHistorySample =
        FollowMeHistorySample(
            timestamp = spec.timestamp,
            observedMac = "AA:BB:CC:DD:EE:FF",
            trackingStatus = spec.status,
            score = spec.score,
            explanation = spec.explanation,
            rssi = -61,
            encounterCount = 4,
            durationScore = spec.durationScore,
            rssiStabilityScore = spec.rssiStabilityScore,
            deviceTypeScore = 0,
            macBehaviorScore = 0,
            encounterScore = spec.encounterScore,
            userMoved = spec.userMoved,
            baselineDevice = spec.baselineDevice,
        )

    private data class FollowMeSampleSpec(
        val timestamp: Long,
        val status: TrackingStatus,
        val score: Float,
        val explanation: String?,
        val durationScore: Int = 0,
        val rssiStabilityScore: Int = 0,
        val encounterScore: Int = 0,
        val userMoved: Boolean? = null,
        val baselineDevice: Boolean? = null,
    )

    private companion object {
        private const val NOW = 1_789_000_000_000L
        private const val ONE_MINUTE = 60_000L
    }
}
