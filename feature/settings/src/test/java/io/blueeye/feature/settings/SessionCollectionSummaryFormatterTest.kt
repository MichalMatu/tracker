package io.blueeye.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionCollectionSummaryFormatterTest {
    @Test
    fun `format returns inactive summary before session starts`() {
        assertEquals(
            "Collected: no active session yet",
            SessionCollectionSummaryFormatter.format(SessionStats()),
        )
    }

    @Test
    fun `format includes duration for active session summaries`() {
        val stats =
            SessionStats(
                hasStarted = true,
                deviceCount = 2,
                sampleCount = 24,
                evidenceCount = 5,
                attentionEvidenceCount = 2,
                durationMs = 12 * 60 * 1_000L,
            )

        assertEquals(
            "Collected: 2 devices / 24 RSSI samples / 5 evidence / 2 review signals / over 12m",
            SessionCollectionSummaryFormatter.format(stats),
        )
    }

    @Test
    fun `format omits duration until observations establish a time span`() {
        val stats =
            SessionStats(
                hasStarted = true,
                deviceCount = 1,
                sampleCount = 0,
                evidenceCount = 0,
                attentionEvidenceCount = 0,
            )

        assertEquals(
            "Collected: 1 devices / 0 RSSI samples / 0 evidence / 0 review signals",
            SessionCollectionSummaryFormatter.format(stats),
        )
    }
}
