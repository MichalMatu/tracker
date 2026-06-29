package io.blueeye.feature.details

import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsCalibrationUiFormatterTest {
    @Test
    fun `format falls back to known safe for ignored device`() {
        val info =
            DetailsCalibrationUiFormatter.format(
                device(isIgnoredForTracking = true),
            )

        assertEquals("Known safe", info.statusText)
        assertSelected(info, DeviceCalibrationLabel.KNOWN_SAFE)
    }

    @Test
    fun `format marks reviewable device as unknown`() {
        val info = DetailsCalibrationUiFormatter.format(device())

        assertEquals("Unknown", info.statusText)
        assertSelected(info, DeviceCalibrationLabel.UNKNOWN)
        assertTrue(info.actions.any { it.label == DeviceCalibrationLabel.FALSE_POSITIVE })
        assertTrue(info.actions.any { it.label == DeviceCalibrationLabel.TRUE_POSITIVE })
    }

    @Test
    fun `format gives explicit label priority over ignored fallback`() {
        val info =
            DetailsCalibrationUiFormatter.format(
                device(
                    isIgnoredForTracking = true,
                    calibrationLabel = DeviceCalibrationLabel.SUSPICIOUS,
                ),
            )

        assertEquals("Suspicious", info.statusText)
        assertSelected(info, DeviceCalibrationLabel.SUSPICIOUS)
    }

    private fun assertSelected(
        info: DetailsCalibrationUiInfo,
        label: DeviceCalibrationLabel,
    ) {
        assertTrue(info.actions.single { it.label == label }.isSelected)
        assertFalse(info.actions.filterNot { it.label == label }.any { it.isSelected })
    }

    private fun device(
        isSafeBeacon: Boolean = false,
        isIgnoredForTracking: Boolean = false,
        isTrackingEnabled: Boolean = true,
        calibrationLabel: DeviceCalibrationLabel = DeviceCalibrationLabel.UNKNOWN,
    ): Device =
        Device(
            fingerprint = "AA:BB:CC:11:22:33",
            macAddress = "AA:BB:CC:11:22:33",
            macAddressType = MacAddressType.PUBLIC,
            technology = "BLE",
            name = "Test device",
            deviceType = DeviceType.UNKNOWN,
            vendorName = "Unknown Vendor",
            predictedModel = null,
            trackingStatus = TrackingStatus.SAFE,
            followingScore = 0f,
            isSafeBeacon = isSafeBeacon,
            isInWatchlist = false,
            userAlias = null,
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            isTrackingEnabled = isTrackingEnabled,
            isIgnoredForTracking = isIgnoredForTracking,
            calibrationLabel = calibrationLabel,
            firstSeenAt = NOW,
            lastSeenAt = NOW,
            rssi = -55,
            encounterCount = 1,
        )

    private companion object {
        private const val NOW = 1_789_000_000_000L
    }
}
