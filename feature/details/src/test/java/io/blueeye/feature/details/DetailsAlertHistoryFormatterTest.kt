package io.blueeye.feature.details

import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.EvidenceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DetailsAlertHistoryFormatterTest {
    @Test
    fun `format returns null for empty events`() {
        assertNull(DetailsAlertHistoryFormatter.format(emptyList()))
    }

    @Test
    fun `format summarizes latest and strongest alert evidence`() {
        val info =
            DetailsAlertHistoryFormatter.format(
                events =
                    listOf(
                        event(
                            timestamp = NOW,
                            type = AlertEvidenceEventType.PUBLIC_SAFETY_SIGNAL,
                            confidence = DetectionConfidence.HIGH,
                        ),
                        event(
                            timestamp = NOW + ONE_MINUTE,
                            type = AlertEvidenceEventType.WATCHLIST_RETURN,
                            confidence = DetectionConfidence.CRITICAL,
                        ),
                    ),
                timestampFormatter = { it.toString() },
            )

        assertNotNull(info)
        requireNotNull(info)
        assertEquals("2 alert evidence events", info.eventCountText)
        assertEquals("watchlist return at ${NOW + ONE_MINUTE}", info.latestText)
        assertEquals("Alert watchlist return", info.strongestText)
        assertEquals(DetectionConfidence.CRITICAL, info.strongestConfidence)
        assertEquals(2, info.recentItems.size)
    }

    @Test
    fun `eventTypeText labels follow me alert events`() {
        assertEquals(
            "follow-me alert",
            DetailsAlertHistoryFormatter.eventTypeText(AlertEvidenceEventType.FOLLOW_ME_ALERT),
        )
    }

    private fun event(
        timestamp: Long,
        type: AlertEvidenceEventType,
        confidence: DetectionConfidence,
    ): AlertEvidenceEvent =
        AlertEvidenceEvent(
            timestamp = timestamp,
            deviceFingerprint = FINGERPRINT,
            observedMac = FINGERPRINT,
            eventType = type,
            evidence =
                DetectionEvidence(
                    source =
                        if (type == AlertEvidenceEventType.WATCHLIST_RETURN) {
                            EvidenceSource.WATCHLIST
                        } else {
                            EvidenceSource.OUI
                        },
                    confidence = confidence,
                    reasonText = "Evidence reason",
                    timestamp = timestamp,
                    rawValue = FINGERPRINT,
                    parsedValue = "Parsed",
                    isPassive = true,
                ),
        )

    private companion object {
        private const val FINGERPRINT = "AA:BB:CC:11:22:33"
        private const val NOW = 1_789_000_000_000L
        private const val ONE_MINUTE = 60_000L
    }
}
