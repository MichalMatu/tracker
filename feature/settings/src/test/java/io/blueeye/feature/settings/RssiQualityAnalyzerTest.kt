package io.blueeye.feature.settings

import io.blueeye.core.model.SignalSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RssiQualityAnalyzerTest {
    @Test
    fun `analyze returns empty quality for no samples`() {
        val quality = RssiQualityAnalyzer.analyze(emptyList())

        assertEquals(0, quality.sampleCount)
        assertEquals(0, quality.uniqueRssiCount)
        assertNull(quality.dominantRssi)
        assertEquals(emptyList<RssiQualityWarning>(), quality.warnings)
    }

    @Test
    fun `analyze flags too few samples`() {
        val quality =
            RssiQualityAnalyzer.analyze(
                listOf(
                    sample(rssi = -60),
                    sample(rssi = -62),
                ),
            )

        assertEquals(2, quality.sampleCount)
        assertEquals(2, quality.uniqueRssiCount)
        assertEquals(-60, quality.dominantRssi)
        assertEquals(1, quality.dominantRssiCount)
        assertEquals(0.5, quality.dominantRssiShare ?: 0.0, 0.001)
        assertEquals(listOf(RssiQualityWarning.TOO_FEW_SAMPLES), quality.warnings)
    }

    @Test
    fun `analyze flags flat rssi pattern`() {
        val quality =
            RssiQualityAnalyzer.analyze(
                List(6) { sample(rssi = -50) },
            )

        assertEquals(6, quality.sampleCount)
        assertEquals(1, quality.uniqueRssiCount)
        assertEquals(-50, quality.dominantRssi)
        assertEquals(6, quality.dominantRssiCount)
        assertEquals(1.0, quality.dominantRssiShare ?: 0.0, 0.001)
        assertEquals(listOf(RssiQualityWarning.FLAT_RSSI_PATTERN), quality.warnings)
    }

    @Test
    fun `analyze accepts varied review samples`() {
        val quality =
            RssiQualityAnalyzer.analyze(
                listOf(
                    sample(rssi = -60),
                    sample(rssi = -61),
                    sample(rssi = -62),
                    sample(rssi = -60),
                ),
            )

        assertEquals(4, quality.sampleCount)
        assertEquals(3, quality.uniqueRssiCount)
        assertEquals(-60, quality.dominantRssi)
        assertEquals(2, quality.dominantRssiCount)
        assertEquals(0.5, quality.dominantRssiShare ?: 0.0, 0.001)
        assertEquals(emptyList<RssiQualityWarning>(), quality.warnings)
    }

    @Test
    fun `trend analyzer requires enough ordered samples`() {
        val trend =
            RssiTrendAnalyzer.analyze(
                listOf(
                    sample(rssi = -70, timestamp = 1L),
                    sample(rssi = -66, timestamp = 2L),
                    sample(rssi = -62, timestamp = 3L),
                ),
            )

        assertEquals(RssiTrendDirection.INSUFFICIENT, trend.direction)
        assertNull(trend.firstWindowAverageRssi)
        assertNull(trend.lastWindowAverageRssi)
        assertNull(trend.deltaRssi)
    }

    @Test
    fun `trend analyzer flags strengthening signal`() {
        val trend =
            RssiTrendAnalyzer.analyze(
                listOf(
                    sample(rssi = -72, timestamp = 1L),
                    sample(rssi = -68, timestamp = 2L),
                    sample(rssi = -60, timestamp = 3L),
                    sample(rssi = -56, timestamp = 4L),
                ),
            )

        assertEquals(RssiTrendDirection.STRENGTHENING, trend.direction)
        assertEquals(-70.0, trend.firstWindowAverageRssi ?: 0.0, 0.001)
        assertEquals(-58.0, trend.lastWindowAverageRssi ?: 0.0, 0.001)
        assertEquals(12.0, trend.deltaRssi ?: 0.0, 0.001)
    }

    @Test
    fun `trend analyzer flags weakening signal`() {
        val trend =
            RssiTrendAnalyzer.analyze(
                listOf(
                    sample(rssi = -54, timestamp = 1L),
                    sample(rssi = -58, timestamp = 2L),
                    sample(rssi = -66, timestamp = 3L),
                    sample(rssi = -70, timestamp = 4L),
                ),
            )

        assertEquals(RssiTrendDirection.WEAKENING, trend.direction)
        assertEquals(-56.0, trend.firstWindowAverageRssi ?: 0.0, 0.001)
        assertEquals(-68.0, trend.lastWindowAverageRssi ?: 0.0, 0.001)
        assertEquals(-12.0, trend.deltaRssi ?: 0.0, 0.001)
    }

    @Test
    fun `trend analyzer treats small movement as stable`() {
        val trend =
            RssiTrendAnalyzer.analyze(
                listOf(
                    sample(rssi = -60, timestamp = 1L),
                    sample(rssi = -61, timestamp = 2L),
                    sample(rssi = -59, timestamp = 3L),
                    sample(rssi = -60, timestamp = 4L),
                ),
            )

        assertEquals(RssiTrendDirection.STABLE, trend.direction)
        assertEquals(1.0, trend.deltaRssi ?: 0.0, 0.001)
    }

    private fun sample(
        rssi: Int,
        timestamp: Long = 1L,
    ): SignalSample =
        SignalSample(
            deviceFingerprint = "device",
            rssi = rssi,
            timestamp = timestamp,
        )
}
