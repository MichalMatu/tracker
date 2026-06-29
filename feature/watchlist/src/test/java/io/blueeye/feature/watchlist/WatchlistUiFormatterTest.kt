package io.blueeye.feature.watchlist

import io.blueeye.core.domain.repository.WatchlistDeviceItem
import io.blueeye.core.model.AlertType
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchlistUiFormatterTest {
    @Test
    fun `in range device shows active status and signal`() {
        val info =
            WatchlistUiFormatter.map(
                item =
                    item(
                        isInRange = true,
                        device = device(lastSeenAt = NOW - 30_000L, rssi = -52),
                    ),
                now = NOW,
            )

        assertEquals("Desk headphones", info.displayName)
        assertEquals("Observed: Sony WH-1000XM5 | MAC: AA:BB:CC:11:22:33", info.identityText)
        assertEquals(WatchlistRangeStatus.IN_RANGE, info.status)
        assertEquals("Last seen 30s ago", info.lastSeenText)
        assertEquals("-52 dBm", info.rssiText)
        assertEquals("Alerts active", info.alertsText)
        assertEquals("Return alerts", info.alertTypeText)
        assertNull(info.returnEvidence)
    }

    @Test
    fun `offline device shows paused alerts when tracking is disabled`() {
        val info =
            WatchlistUiFormatter.map(
                item =
                    item(
                        isInRange = false,
                        alertType = AlertType.ALWAYS,
                        priorityLevel = 5,
                        device =
                            device(
                                lastSeenAt = NOW - 120_000L,
                                isTrackingEnabled = false,
                            ),
                    ),
                now = NOW,
            )

        assertEquals(WatchlistRangeStatus.OFFLINE, info.status)
        assertEquals("Last seen 2m ago", info.lastSeenText)
        assertEquals("Alerts paused", info.alertsText)
        assertEquals("Continuous alerts", info.alertTypeText)
        assertEquals("Priority 5", info.priorityText)
        assertNull(info.returnEvidence)
    }

    @Test
    fun `device without alias keeps radio name as title and mac as identity`() {
        val info =
            WatchlistUiFormatter.map(
                item =
                    item(
                        isInRange = true,
                        device =
                            device(
                                lastSeenAt = NOW - 30_000L,
                                userAlias = null,
                            ),
                    ),
                now = NOW,
            )

        assertEquals("Sony WH-1000XM5", info.displayName)
        assertEquals("MAC: AA:BB:CC:11:22:33", info.identityText)
    }

    @Test
    fun `alias matching radio name does not show redundant observed identity`() {
        val info =
            WatchlistUiFormatter.map(
                item =
                    item(
                        isInRange = true,
                        device =
                            device(
                                lastSeenAt = NOW - 30_000L,
                                userAlias = "Sony WH-1000XM5",
                            ),
                    ),
                now = NOW,
            )

        assertEquals("Sony WH-1000XM5", info.displayName)
        assertEquals("MAC: AA:BB:CC:11:22:33", info.identityText)
    }

    @Test
    fun `watchlist return evidence is shown when device returned after offline gap`() {
        val info =
            WatchlistUiFormatter.map(
                item =
                    item(
                        isInRange = true,
                        device =
                            device(
                                lastSeenAt = NOW - 5_000L,
                                evidence =
                                    listOf(
                                        watchlistEvidence(
                                            reason = "User-selected watchlist device returned after 120s offline.",
                                            timestamp = NOW - 5_000L,
                                        ),
                                    ),
                            ),
                    ),
                now = NOW,
            )

        val evidence = info.returnEvidence ?: error("Expected return evidence")

        assertEquals("High confidence", evidence.confidenceText)
        assertEquals("Source: Watchlist - BLE ad", evidence.sourceText)
        assertEquals("User-selected watchlist device returned after 120s offline.", evidence.reasonText)
        assertEquals("Value: AA:BB:CC:11:22:33 -> Desk headphones", evidence.valueText)
    }

    @Test
    fun `plain watchlist evidence does not add redundant return text`() {
        val info =
            WatchlistUiFormatter.map(
                item =
                    item(
                        isInRange = true,
                        device =
                            device(
                                lastSeenAt = NOW - 5_000L,
                                evidence =
                                    listOf(
                                        watchlistEvidence(
                                            reason = "User watchlist match: this device was selected for alerts.",
                                            timestamp = NOW - 5_000L,
                                        ),
                                    ),
                            ),
                    ),
                now = NOW,
            )

        assertNull(info.returnEvidence)
    }

    private fun item(
        isInRange: Boolean,
        device: Device,
        alertType: AlertType = AlertType.ON_APPEAR,
        priorityLevel: Int = 3,
    ): WatchlistDeviceItem =
        WatchlistDeviceItem(
            device = device,
            isInRange = isInRange,
            alertType = alertType,
            priorityLevel = priorityLevel,
        )

    private fun device(
        lastSeenAt: Long,
        rssi: Int = -70,
        isTrackingEnabled: Boolean = true,
        userAlias: String? = "Desk headphones",
        evidence: List<DetectionEvidence> = emptyList(),
    ): Device =
        Device(
            fingerprint = "AA:BB:CC:11:22:33",
            macAddress = "AA:BB:CC:11:22:33",
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = "Sony WH-1000XM5",
            deviceType = DeviceType.HEADPHONES,
            vendorName = "Sony",
            predictedModel = null,
            trackingStatus = TrackingStatus.SAFE,
            followingScore = 0f,
            isSafeBeacon = false,
            isInWatchlist = true,
            userAlias = userAlias,
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            isTrackingEnabled = isTrackingEnabled,
            firstSeenAt = NOW - 600_000L,
            lastSeenAt = lastSeenAt,
            rssi = rssi,
            encounterCount = 3,
            evidence = evidence,
        )

    private fun watchlistEvidence(
        reason: String,
        timestamp: Long,
    ): DetectionEvidence =
        DetectionEvidence(
            source = EvidenceSource.WATCHLIST,
            confidence = DetectionConfidence.CRITICAL,
            reasonText = reason,
            timestamp = timestamp,
            rawValue = "AA:BB:CC:11:22:33",
            parsedValue = "Desk headphones",
            isPassive = true,
            provenance = EvidenceProvenance.BLE_ADVERTISEMENT,
        )

    private companion object {
        private const val NOW = 1_789_000_000_000L
    }
}
