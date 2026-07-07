package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RadarUiCardOrderTest {
    @Test
    fun `strong nearby devices sort before newer weak devices`() {
        val sorted =
            listOf(
                item(fingerprint = "weak-new", displayName = "A weak", rssi = -93, firstSeenAt = NOW + 2_000),
                item(fingerprint = "strong-old", displayName = "B strong", rssi = -52, firstSeenAt = NOW),
            ).sortedWith(RadarUiCardOrder.comparator)

        assertEquals(listOf("strong-old", "weak-new"), sorted.map { it.fingerprint })
    }

    @Test
    fun `same signal bucket uses rssi before recency`() {
        val sorted =
            listOf(
                item(
                    fingerprint = "medium-weaker-new",
                    displayName = "A medium",
                    rssi = -78,
                    firstSeenAt = NOW + 2_000,
                ),
                item(fingerprint = "medium-stronger-old", displayName = "B medium", rssi = -65, firstSeenAt = NOW),
            ).sortedWith(RadarUiCardOrder.comparator)

        assertEquals(listOf("medium-stronger-old", "medium-weaker-new"), sorted.map { it.fingerprint })
    }

    @Test
    fun `watchlist and new devices keep top priority`() {
        val sorted =
            listOf(
                item(fingerprint = "ordinary", displayName = "A ordinary"),
                item(fingerprint = "new", displayName = "B new", priority = RadarItemPriority.NEW),
                item(fingerprint = "watch", displayName = "C watch", priority = RadarItemPriority.WATCHLIST),
            ).sortedWith(RadarUiCardOrder.comparator)

        assertEquals(listOf("watch", "new", "ordinary"), sorted.map { it.fingerprint })
    }

    private fun item(
        fingerprint: String,
        displayName: String,
        rssi: Int = -60,
        firstSeenAt: Long = NOW,
        priority: RadarItemPriority = RadarItemPriority.ORDINARY,
    ): RadarUiItem =
        RadarUiMapper.mapToUi(
            device =
                Device(
                    fingerprint = fingerprint,
                    macAddress = "AA:BB:CC:11:22:33",
                    macAddressType = MacAddressType.PUBLIC,
                    technology = "BLE",
                    name = displayName,
                    deviceType = DeviceType.UNKNOWN,
                    vendorName = null,
                    predictedModel = null,
                    trackingStatus = TrackingStatus.SAFE,
                    followingScore = 0f,
                    isSafeBeacon = false,
                    isInWatchlist = priority == RadarItemPriority.WATCHLIST,
                    userAlias = null,
                    userNotes = null,
                    alertSound = false,
                    alertVibration = false,
                    firstSeenAt = firstSeenAt,
                    lastSeenAt = NOW,
                    rssi = rssi,
                    encounterCount = 1,
                ),
            isNew = priority == RadarItemPriority.NEW,
            activeProbeMac = null,
        )

    private enum class RadarItemPriority {
        ORDINARY,
        NEW,
        WATCHLIST,
    }

    private companion object {
        private const val NOW = 1_789_000_000_000L
    }
}
