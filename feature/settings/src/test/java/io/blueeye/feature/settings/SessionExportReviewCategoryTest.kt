package io.blueeye.feature.settings

import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionExportReviewCategoryTest {
    @Test
    fun `public safety device type without evidence remains nearby`() {
        val category =
            device(
                deviceType = DeviceType.BODY_CAMERA,
                name = "Camera",
            ).sessionExportReviewCategory()

        assertEquals(SessionExportReviewCategory.NEARBY, category)
    }

    @Test
    fun `tracker device type without evidence remains nearby`() {
        val category =
            device(
                deviceType = DeviceType.TRACKER,
                name = "AirTag",
            ).sessionExportReviewCategory()

        assertEquals(SessionExportReviewCategory.NEARBY, category)
    }

    private fun device(
        deviceType: DeviceType,
        name: String,
    ): Device =
        Device(
            fingerprint = "device-${deviceType.name.lowercase()}",
            macAddress = "AA:BB:CC:11:22:33",
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = name,
            deviceType = deviceType,
            vendorName = "Known Vendor",
            predictedModel = null,
            trackingStatus = TrackingStatus.SAFE,
            followingScore = 0f,
            isSafeBeacon = false,
            isInWatchlist = false,
            userAlias = null,
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            firstSeenAt = SESSION_STARTED_AT,
            lastSeenAt = SESSION_STARTED_AT,
            rssi = -55,
            encounterCount = 1,
        )

    private companion object {
        private const val SESSION_STARTED_AT = 1_789_000_000_000L
    }
}
