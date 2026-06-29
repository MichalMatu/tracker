package io.blueeye.feature.watchlist

import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.PublicSafetySignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WatchlistSignalUiFormatterTest {
    @Test
    fun `card context explains signals are not confirmed presence`() {
        val text = WatchlistSignalUiFormatter.CONTEXT_TEXT

        assertEquals("Public-safety-like signals are evidence hints, not confirmed presence.", text)
        assertNoConfirmedPublicSafetyClaim(text)
    }

    @Test
    fun `card count stays evidence based`() {
        val text = WatchlistSignalUiFormatter.countText(2)

        assertEquals("2 active evidence hints", text)
        assertFalse(text.contains("active signals", ignoreCase = true))
    }

    @Test
    fun `formats public safety signal as evidence hint`() {
        val items =
            WatchlistSignalUiFormatter.map(
                detections =
                    listOf(
                        detection(
                            vendorName = "Axon Enterprise, Inc.",
                            category = "BODY_CAMERA",
                            confidence = DetectionConfidence.HIGH,
                            description = "Body camera and connected safety sensor equipment",
                            lastSeenAt = NOW - 45_000L,
                        ).copy(rssi = -61),
                    ),
                now = NOW,
            )

        assertEquals(1, items.size)
        assertEquals("Signal consistent with body-camera-like signal", items.first().title)
        assertEquals("High confidence", items.first().confidenceText)
        assertEquals(
            "Evidence: MAC OUI is consistent with Axon Enterprise, Inc.: " +
                "Body camera and connected safety sensor equipment.",
            items.first().detailText,
        )
        assertEquals("-61 dBm - seen 45s ago", items.first().signalText)
        assertNoConfirmedPublicSafetyClaim(items.first().title)
        assertNoConfirmedPublicSafetyClaim(items.first().detailText)
    }

    @Test
    fun `caps critical tactical confidence to high for user facing text`() {
        val item =
            WatchlistSignalUiFormatter.map(
                detections =
                    listOf(
                        detection(
                            category = "HOLSTER_SENSOR",
                            confidence = DetectionConfidence.CRITICAL,
                        ),
                    ),
                now = NOW,
            ).first()

        assertEquals("Signal consistent with holster-sensor-like signal", item.title)
        assertEquals("High confidence", item.confidenceText)
        assertFalse(item.confidenceText.contains("Critical"))
    }

    @Test
    fun `public safety categories stay signal based`() {
        val cases =
            listOf(
                "BODY_CAMERA" to "Signal consistent with body-camera-like signal",
                "TACTICAL_RADIO" to "Signal consistent with professional radio signal",
                "POLICE_EQUIPMENT" to "Signal consistent with public-safety-like signal",
                "FIREFIGHTER" to "Signal consistent with fire telemetry signal",
            )

        cases.forEach { (category, expectedTitle) ->
            val item =
                WatchlistSignalUiFormatter.map(
                    detections = listOf(detection(category = category)),
                    now = NOW,
                ).first()

            assertEquals(expectedTitle, item.title)
            assertNoConfirmedPublicSafetyClaim(item.title)
        }
    }

    @Test
    fun `shows only latest three signals`() {
        val items =
            WatchlistSignalUiFormatter.map(
                detections =
                    listOf(
                        detection(lastSeenAt = NOW - 4_000L),
                        detection(lastSeenAt = NOW - 3_000L),
                        detection(lastSeenAt = NOW - 2_000L),
                        detection(lastSeenAt = NOW - 1_000L),
                    ),
                now = NOW,
            )

        assertEquals(3, items.size)
        assertEquals("-70 dBm - seen 1s ago", items[0].signalText)
        assertEquals("-70 dBm - seen 3s ago", items[2].signalText)
    }

    private fun detection(
        vendorName: String = "Vendor",
        category: String = "TACTICAL_RADIO",
        confidence: DetectionConfidence = DetectionConfidence.MEDIUM,
        description: String = "Professional radio profile",
        lastSeenAt: Long = NOW - 30_000L,
    ): PublicSafetySignal =
        PublicSafetySignal(
            deviceId = "AA:BB:CC:11:22:33",
            vendorName = vendorName,
            category = category,
            confidence = confidence,
            description = description,
            evidence =
                listOf(
                    DetectionEvidence(
                        source = EvidenceSource.OUI,
                        confidence = confidence,
                        reasonText = "MAC OUI is consistent with $vendorName: $description.",
                        timestamp = lastSeenAt,
                        rawValue = "AABBCC",
                        parsedValue = "$category: $description",
                        isPassive = true,
                    ),
                ),
            rssi = -70,
            firstSeenAt = NOW - 60_000L,
            lastSeenAt = lastSeenAt,
        )

    private fun assertNoConfirmedPublicSafetyClaim(text: String) {
        val normalized = text.lowercase()
        PUBLIC_SAFETY_OVERCLAIMS.forEach { phrase ->
            assertFalse("Expected <$text> not to contain <$phrase>", normalized.contains(phrase))
        }
    }

    private companion object {
        private const val NOW = 1_789_000_000_000L
        private val PUBLIC_SAFETY_OVERCLAIMS =
            listOf(
                "police",
                "law enforcement",
                "body camera equipment",
                "professional radio equipment",
                "emergency radio",
                "firefighter equipment",
            )
    }
}
