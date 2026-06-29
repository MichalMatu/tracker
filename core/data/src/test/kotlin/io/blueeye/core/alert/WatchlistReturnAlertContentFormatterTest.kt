package io.blueeye.core.alert

import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.EvidenceSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchlistReturnAlertContentFormatterTest {
    @Test
    fun `formats watchlist return alert with evidence and rssi`() {
        val content =
            WatchlistReturnAlertContentFormatter.format(
                mac = MAC,
                rssi = RSSI,
                evidence =
                    evidence(
                        parsedValue = "My headphones",
                        reasonText = "Watchlist device returned after 120s offline.",
                    ),
        )

        assertTrue(content.title.contains("Watchlist device returned"))
        assertTrue(content.body.contains("My headphones returned to Bluetooth range"))
        assertTrue(content.body.contains("Confidence: High confidence."))
        assertTrue(content.body.contains("Source: Watchlist - passive."))
        assertTrue(content.body.contains("Evidence: Watchlist device returned after 120s offline."))
        assertTrue(content.body.contains("$RSSI dBm"))
        assertNoOverclaim(content)
    }

    @Test
    fun `falls back to mac when watchlist evidence has no display name`() {
        val content =
            WatchlistReturnAlertContentFormatter.format(
                mac = MAC,
                rssi = RSSI,
                evidence =
                    evidence(
                        parsedValue = null,
                        reasonText = "Watchlist device returned after 90s offline.",
                    ),
            )

        assertTrue(content.body.contains("$MAC returned to Bluetooth range"))
        assertTrue(content.body.contains("Confidence: High confidence."))
        assertTrue(content.body.contains("Source: Watchlist - passive."))
        assertTrue(content.body.contains("Evidence: Watchlist device returned after 90s offline."))
    }

    private fun evidence(
        parsedValue: String?,
        reasonText: String,
    ): DetectionEvidence =
        DetectionEvidence(
            source = EvidenceSource.WATCHLIST,
            confidence = DetectionConfidence.CRITICAL,
            reasonText = reasonText,
            timestamp = 1_789_000_000_000L,
            rawValue = MAC,
            parsedValue = parsedValue,
            isPassive = true,
        )

    private fun assertNoOverclaim(content: WatchlistReturnAlertContent) {
        val combined = "${content.title} ${content.body}".lowercase()
        assertFalse(combined.contains("following you"))
        assertFalse(combined.contains("tracking you"))
        assertFalse(combined.contains("danger"))
    }

    private companion object {
        private const val MAC = "AA:BB:CC:11:22:33"
        private const val RSSI = -52
    }
}
