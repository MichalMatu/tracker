package io.blueeye.feature.details

import io.blueeye.core.model.SignalSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailsSignalHistoryFormatterTest {
    @Test
    fun `empty samples return no history info`() {
        assertNull(DetailsSignalHistoryFormatter.format(emptyList()))
    }

    @Test
    fun `formats sample count window average range and latest rssi`() {
        val info =
            DetailsSignalHistoryFormatter.format(
                samples =
                    listOf(
                        sample(timestamp = TIMESTAMP + 30_000L, rssi = -61),
                        sample(timestamp = TIMESTAMP, rssi = -75),
                        sample(timestamp = TIMESTAMP + 60_000L, rssi = -55),
                    ),
                timestampFormatter = { "t:$it" },
            ) ?: error("Expected signal history info")

        assertEquals("3 RSSI samples", info.sampleCountText)
        assertEquals("First t:$TIMESTAMP / latest t:${TIMESTAMP + 60_000L}", info.windowText)
        assertEquals("Latest -55 dBm", info.latestRssiText)
        assertEquals("Average -64 dBm", info.averageRssiText)
        assertEquals("Range -75 to -55 dBm", info.rangeText)
    }

    @Test
    fun `formats strengthening trend when latest rssi is materially higher`() {
        val info =
            DetailsSignalHistoryFormatter.format(
                samples =
                    listOf(
                        sample(timestamp = TIMESTAMP, rssi = -76),
                        sample(timestamp = TIMESTAMP + 60_000L, rssi = -62),
                    ),
                timestampFormatter = { it.toString() },
            ) ?: error("Expected signal history info")

        assertEquals("RSSI strengthening (+14 dB)", info.trendText)
        assertEquals(DetailsSignalTrendTone.STRENGTHENING, info.trendTone)
    }

    @Test
    fun `formats fading and stable trends without overclaiming movement`() {
        val fading =
            DetailsSignalHistoryFormatter.format(
                samples =
                    listOf(
                        sample(timestamp = TIMESTAMP, rssi = -55),
                        sample(timestamp = TIMESTAMP + 60_000L, rssi = -68),
                    ),
                timestampFormatter = { it.toString() },
            ) ?: error("Expected fading signal history info")
        val stable =
            DetailsSignalHistoryFormatter.format(
                samples =
                    listOf(
                        sample(timestamp = TIMESTAMP, rssi = -55),
                        sample(timestamp = TIMESTAMP + 60_000L, rssi = -58),
                    ),
                timestampFormatter = { it.toString() },
            ) ?: error("Expected stable signal history info")

        assertEquals("RSSI fading (-13 dB)", fading.trendText)
        assertEquals(DetailsSignalTrendTone.FADING, fading.trendTone)
        assertEquals("RSSI stable (-3 dB)", stable.trendText)
        assertEquals(DetailsSignalTrendTone.STABLE, stable.trendTone)
    }

    @Test
    fun `single sample asks for more trend data`() {
        val info =
            DetailsSignalHistoryFormatter.format(
                samples = listOf(sample(timestamp = TIMESTAMP, rssi = -70)),
                timestampFormatter = { "t:$it" },
            ) ?: error("Expected signal history info")

        assertEquals("1 RSSI sample", info.sampleCountText)
        assertEquals("Observed t:$TIMESTAMP", info.windowText)
        assertEquals("Trend needs more samples", info.trendText)
        assertEquals(DetailsSignalTrendTone.INSUFFICIENT, info.trendTone)
    }

    private fun sample(
        timestamp: Long,
        rssi: Int,
    ): SignalSample =
        SignalSample(
            timestamp = timestamp,
            rssi = rssi,
        )

    private companion object {
        private const val TIMESTAMP = 1_789_000_000_000L
    }
}
