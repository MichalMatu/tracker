package io.blueeye.core.domain.calibration

import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCalibrationConfigMapperTest {
    @Test
    fun `known safe and false positive suppress tracking and alerts`() {
        listOf(
            DeviceCalibrationLabel.KNOWN_SAFE,
            DeviceCalibrationLabel.FALSE_POSITIVE,
        ).forEach { label ->
            val config = device().toCalibrationDeviceConfig(label)

            assertTrue(label.suppressesTracking())
            assertTrue(config.isSafe)
            assertFalse(config.alertSound)
            assertFalse(config.alertVibration)
            assertFalse(config.isTrackingEnabled)
        }
    }

    @Test
    fun `review labels keep tracking and alerts enabled`() {
        listOf(
            DeviceCalibrationLabel.TRUE_POSITIVE,
            DeviceCalibrationLabel.SUSPICIOUS,
            DeviceCalibrationLabel.UNKNOWN,
        ).forEach { label ->
            val config = device().toCalibrationDeviceConfig(label)

            assertFalse(label.suppressesTracking())
            assertFalse(config.isSafe)
            assertTrue(config.alertSound)
            assertTrue(config.alertVibration)
            assertTrue(config.isTrackingEnabled)
        }
    }

    private fun device(): Device =
        Device(
            fingerprint = "AA:BB:CC:11:22:33",
            macAddress = "AA:BB:CC:11:22:33",
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = "Device",
            deviceType = DeviceType.UNKNOWN,
            vendorName = null,
            predictedModel = null,
            trackingStatus = TrackingStatus.SAFE,
            followingScore = 0f,
            isSafeBeacon = false,
            isInWatchlist = false,
            userAlias = null,
            userNotes = null,
            alertSound = true,
            alertVibration = true,
            isTrackingEnabled = true,
            firstSeenAt = NOW,
            lastSeenAt = NOW,
            rssi = -60,
            encounterCount = 1,
        )

    private companion object {
        private const val NOW = 1_789_000_000_000L
    }
}
