package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RadarUiCardOrderTest {
    @Test
    fun `live rssi changes do not reorder existing cards`() {
        val initial =
            listOf(
                item(fingerprint = "older", displayName = "Older", rssi = -45, firstSeenAt = NOW),
                item(fingerprint = "newer", displayName = "Newer", rssi = -95, firstSeenAt = NOW + 2_000),
            ).sortedWith(RadarUiCardOrder.comparator)

        val afterRssiSwap =
            listOf(
                item(fingerprint = "older", displayName = "Older", rssi = -99, firstSeenAt = NOW),
                item(fingerprint = "newer", displayName = "Newer", rssi = -35, firstSeenAt = NOW + 2_000),
            ).sortedWith(RadarUiCardOrder.comparator)

        assertEquals(initial.map { it.fingerprint }, afterRssiSwap.map { it.fingerprint })
    }

    @Test
    fun `last seen updates do not reorder existing cards`() {
        val initial =
            listOf(
                item(fingerprint = "alpha", displayName = "Alpha", firstSeenAt = NOW).withLastSeen(NOW + 10_000),
                item(fingerprint = "beta", displayName = "Beta", firstSeenAt = NOW + 2_000).withLastSeen(NOW),
            ).sortedWith(RadarUiCardOrder.comparator)

        val afterLastSeenSwap =
            listOf(
                item(fingerprint = "alpha", displayName = "Alpha", firstSeenAt = NOW).withLastSeen(NOW),
                item(fingerprint = "beta", displayName = "Beta", firstSeenAt = NOW + 2_000).withLastSeen(NOW + 20_000),
            ).sortedWith(RadarUiCardOrder.comparator)

        assertEquals(initial.map { it.fingerprint }, afterLastSeenSwap.map { it.fingerprint })
    }

    @Test
    fun `newer discovery appears before older discovery regardless of signal`() {
        val sorted =
            listOf(
                item(fingerprint = "older-strong", displayName = "Older", rssi = -35, firstSeenAt = NOW),
                item(fingerprint = "newer-weak", displayName = "Newer", rssi = -95, firstSeenAt = NOW + 2_000),
            ).sortedWith(RadarUiCardOrder.comparator)

        assertEquals(listOf("newer-weak", "older-strong"), sorted.map { it.fingerprint })
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

    private fun RadarUiItem.withLastSeen(lastSeenAt: Long): RadarUiItem {
        return copy(
            device =
                device.copy(
                    lastSeenAt = lastSeenAt,
                ),
        )
    }

    private enum class RadarItemPriority {
        ORDINARY,
        NEW,
        WATCHLIST,
    }

    private companion object {
        private const val NOW = 1_789_000_000_000L
    }
}
