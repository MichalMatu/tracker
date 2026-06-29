package io.blueeye.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionRssiQualityFormatterTest {
    @Test
    fun `format hides empty quality`() {
        assertNull(SessionRssiQualityFormatter.format(RssiQualityStats()))
    }

    @Test
    fun `format shows normal sample variety`() {
        val info =
            SessionRssiQualityFormatter.format(
                RssiQualityStats(
                    sampleCount = 12,
                    uniqueRssiCount = 8,
                ),
            )

        assertEquals("RSSI quality: 12 samples / 8 unique values", info?.text)
        assertEquals(SessionRssiQualityTone.NORMAL, info?.tone)
    }

    @Test
    fun `format explains too few samples`() {
        val info =
            SessionRssiQualityFormatter.format(
                RssiQualityStats(
                    sampleCount = 1,
                    uniqueRssiCount = 1,
                    warnings = listOf(RssiQualityWarning.TOO_FEW_SAMPLES),
                ),
            )

        assertEquals("RSSI quality: only 1 sample; collect at least 3", info?.text)
        assertEquals(SessionRssiQualityTone.WARNING, info?.tone)
    }

    @Test
    fun `format explains flat rssi pattern`() {
        val info =
            SessionRssiQualityFormatter.format(
                RssiQualityStats(
                    sampleCount = 6,
                    uniqueRssiCount = 1,
                    dominantRssi = -50,
                    dominantRssiShare = 1.0,
                    warnings = listOf(RssiQualityWarning.FLAT_RSSI_PATTERN),
                ),
            )

        assertEquals("RSSI quality: flat pattern; -50 dBm dominates 100% of samples", info?.text)
        assertEquals(SessionRssiQualityTone.WARNING, info?.tone)
    }
}
