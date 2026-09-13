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
            ).sortedWith(RadarUiCardOrder.comparator(SORT_NOW))

        val afterRssiSwap =
            listOf(
                item(fingerprint = "older", displayName = "Older", rssi = -99, firstSeenAt = NOW),
                item(fingerprint = "newer", displayName = "Newer", rssi = -35, firstSeenAt = NOW + 2_000),
            ).sortedWith(RadarUiCardOrder.comparator(SORT_NOW))

        assertEquals(initial.map { it.fingerprint }, afterRssiSwap.map { it.fingerprint })
    }

    @Test
    fun `last seen updates inside one recency bucket do not reorder cards`() {
        val initial =
            listOf(
                item(fingerprint = "alpha", displayName = "Alpha", firstSeenAt = NOW, lastSeenAt = SORT_NOW - 5_000),
                item(fingerprint = "beta", displayName = "Beta", firstSeenAt = NOW + 2_000, lastSeenAt = SORT_NOW - 10_000),
            ).sortedWith(RadarUiCardOrder.comparator(SORT_NOW))

        val afterLastSeenSwap =
            listOf(
                item(fingerprint = "alpha", displayName = "Alpha", firstSeenAt = NOW, lastSeenAt = SORT_NOW - 12_000),
                item(fingerprint = "beta", displayName = "Beta", firstSeenAt = NOW + 2_000, lastSeenAt = SORT_NOW - 3_000),
            ).sortedWith(RadarUiCardOrder.comparator(SORT_NOW))

        assertEquals(initial.map { it.fingerprint }, afterLastSeenSwap.map { it.fingerprint })
    }

    @Test
    fun `actively seen device appears before stale device`() {
        val sorted =
            listOf(
                item(
                    fingerprint = "stale-newer",
                    displayName = "Stale",
                    firstSeenAt = NOW + 10_000,
                    lastSeenAt = SORT_NOW - 90_000,
                ),
                item(
                    fingerprint = "active-older",
                    displayName = "Active",
                    firstSeenAt = NOW,
                    lastSeenAt = SORT_NOW - 2_000,
                ),
            ).sortedWith(RadarUiCardOrder.comparator(SORT_NOW))

        assertEquals(listOf("active-older", "stale-newer"), sorted.map { it.fingerprint })
    }

    @Test
    fun `newer discovery appears before older discovery inside same recency bucket`() {
        val sorted =
            listOf(
                item(fingerprint = "older-strong", displayName = "Older", rssi = -35, firstSeenAt = NOW),
                item(fingerprint = "newer-weak", displayName = "Newer", rssi = -95, firstSeenAt = NOW + 2_000),
            ).sortedWith(RadarUiCardOrder.comparator(SORT_NOW))

        assertEquals(listOf("newer-weak", "older-strong"), sorted.map { it.fingerprint })
    }

    @Test
    fun `watchlist keeps top priority and new wins inside same recency bucket`() {
        val sorted =
            listOf(
                item(fingerprint = "ordinary", displayName = "A ordinary"),
                item(fingerprint = "new", displayName = "B new", priority = RadarItemPriority.NEW),
                item(fingerprint = "watch", displayName = "C watch", priority = RadarItemPriority.WATCHLIST),
            ).sortedWith(RadarUiCardOrder.comparator(SORT_NOW))

        assertEquals(listOf("watch", "new", "ordinary"), sorted.map { it.fingerprint })
    }

    @Suppress("LongParameterList")
    private fun item(
        fingerprint: String,
        displayName: String,
        rssi: Int = -60,
        firstSeenAt: Long = NOW,
        lastSeenAt: Long = SORT_NOW - 1_000,
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
                    lastSeenAt = lastSeenAt,
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
        private const val SORT_NOW = NOW + 30_000L
    }
}
