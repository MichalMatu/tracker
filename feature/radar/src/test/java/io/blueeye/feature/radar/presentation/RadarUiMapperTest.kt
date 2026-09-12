package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarUiMapperTest {
    @Test
    fun `maps last seen and rssi text for radar cards`() {
        val item =
            RadarUiMapper.mapToUi(
                device = device(lastSeenAt = System.currentTimeMillis() - LAST_SEEN_GAP_MS),
                isNew = false,
                activeProbeMac = null,
            )

        assertEquals("-55 dBm", item.signalInfo.rssiText)
        assertEquals("1m", item.signalInfo.timeSinceSeen)
    }

    @Test
    fun `generic placeholder identity hides mac address from radar title`() {
        val item =
            RadarUiMapper.mapToUi(
                device =
                    device(
                        name = "Unknown Device",
                        vendorName = "Unknown Vendor",
                        predictedModel = "N/A",
                    ),
                isNew = false,
                activeProbeMac = null,
            )

        assertEquals("Unknown BLE device", item.displayName)
        assertFalse(item.displayName.contains("AA:BB:CC"))
    }

    @Test
    fun `active probe exposes probing state for compact card spinner`() {
        val item =
            RadarUiMapper.mapToUi(
                device = device(),
                isNew = false,
                activeProbeMac = "AA:BB:CC:11:22:33",
            )

        assertTrue(item.isProbing)
    }

    private fun device(
        lastSeenAt: Long = NOW,
        name: String? = "Axon Body 3",
        vendorName: String? = "Unknown Vendor",
        predictedModel: String? = null,
    ): Device =
        Device(
            fingerprint = "AA:BB:CC:11:22:33",
            macAddress = "AA:BB:CC:11:22:33",
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = name,
            deviceType = DeviceType.UNKNOWN,
            vendorName = vendorName,
            predictedModel = predictedModel,
            trackingStatus = TrackingStatus.SAFE,
            followingScore = 0f,
            isSafeBeacon = false,
            isInWatchlist = false,
            userAlias = null,
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            firstSeenAt = NOW,
            lastSeenAt = lastSeenAt,
            rssi = -55,
            encounterCount = 1,
            evidence = emptyList(),
        )

    private companion object {
        private const val NOW = 1_789_000_000_000L
        private const val LAST_SEEN_GAP_MS = 65_000L
    }
}
