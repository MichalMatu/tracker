package io.blueeye.feature.radar.presentation

import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RadarUiFormatterTest {
    @Test
    fun `vendor and type omits unknown placeholders`() {
        assertEquals("", RadarUiFormatter.formatVendorAndType(device(connectionStatus = "NONE")))
        assertEquals(
            "Apple",
            RadarUiFormatter.formatVendorAndType(
                device(
                    DeviceSpec(
                        connectionStatus = "NONE",
                        vendorName = "Apple",
                    ),
                ),
            ),
        )
        assertEquals(
            "Apple • HEADPHONES",
            RadarUiFormatter.formatVendorAndType(
                device(
                    DeviceSpec(
                        connectionStatus = "NONE",
                        vendorName = "Apple",
                        deviceType = DeviceType.HEADPHONES,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `public safety type labels remain signal based instead of confirmed device claims`() {
        val cases =
            listOf(
                DeviceType.POLICE to "Public-safety-like signal",
                DeviceType.AXON to "Axon-like signal",
                DeviceType.BODY_CAMERA to "Body-camera-like signal",
                DeviceType.TACTICAL_AUDIO to "Professional audio signal",
                DeviceType.TACTICAL_RADIO to "Professional radio signal",
                DeviceType.TACTICAL_EUD to "Professional terminal signal",
                DeviceType.HOLSTER_SENSOR to "Holster-sensor-like signal",
                DeviceType.SMART_WEAPON to "Smart equipment signal",
                DeviceType.VEHICLE_ROUTER to "Vehicle router signal",
                DeviceType.DOCUMENT_READER to "Document reader signal",
                DeviceType.FIREFIGHTER to "Fire telemetry signal",
                DeviceType.TACTICAL to "Professional equipment signal",
            )

        cases.forEach { (deviceType, expectedLabel) ->
            val text =
                RadarUiFormatter.formatVendorAndType(
                    device(
                        DeviceSpec(
                            connectionStatus = "NONE",
                            vendorName = null,
                            deviceType = deviceType,
                        ),
                    ),
                )

            assertEquals(expectedLabel, text)
            assertNoConfirmedPublicSafetyClaim(text)
        }
    }

    @Test
    fun `rssi colors are signal quality not safety verdicts`() {
        val cases =
            listOf(
                -52 to RadarUiColorToken.PRIMARY,
                -72 to RadarUiColorToken.SECONDARY,
                -92 to RadarUiColorToken.OUTLINE,
            )

        cases.forEach { (rssi, expectedColor) ->
            val info = RadarUiFormatter.formatSignalInfo(device(connectionStatus = "UNKNOWN", rssi = rssi))

            assertEquals(expectedColor, info.signalColor)
            assertFalse(
                "RSSI color ${info.signalColor} must not look like a risk verdict",
                info.signalColor in RSSI_RISK_VERDICT_COLORS,
            )
        }
    }

    @Test
    fun `new device status does not tint card as danger`() {
        val info = RadarUiFormatter.formatStatusInfo(device(connectionStatus = "NONE"), isNew = true)

        assertEquals("NEW", info.text)
        assertEquals(RadarUiColorToken.PRIMARY, info.backgroundTint)
        assertFalse(info.isWarning)
        assertNull(info.cardBackgroundColor)
    }

    @Test
    fun `high follow me status is review colored suspicious not dangerous`() {
        val info =
            RadarUiFormatter.formatStatusInfo(
                device(
                    DeviceSpec(
                        connectionStatus = "NONE",
                        trackingStatus = TrackingStatus.DANGEROUS,
                    ),
                ),
                isNew = false,
            )

        assertEquals("REVIEW", info.text)
        assertFalse(info.text.contains("DANGEROUS"))
        assertEquals(RadarUiColorToken.SUSPICIOUS, info.textColor)
        assertEquals(RadarUiColorToken.SUSPICIOUS_CONTAINER, info.backgroundTint)
    }

    private fun assertNoConfirmedPublicSafetyClaim(text: String) {
        val normalized = text.lowercase()
        PUBLIC_SAFETY_OVERCLAIMS.forEach { phrase ->
            assertFalse("Expected <$text> not to contain <$phrase>", normalized.contains(phrase))
        }
    }

    private data class DeviceSpec(
        val connectionStatus: String,
        val technology: String = "BLE",
        val isConnectable: Boolean? = true,
        val rssi: Int = -55,
        val vendorName: String? = "Unknown Vendor",
        val deviceType: DeviceType = DeviceType.UNKNOWN,
        val trackingStatus: TrackingStatus = TrackingStatus.SAFE,
    )

    private fun device(
        connectionStatus: String,
        technology: String = "BLE",
        isConnectable: Boolean? = true,
        rssi: Int = -55,
    ): Device =
        device(
            DeviceSpec(
                connectionStatus = connectionStatus,
                technology = technology,
                isConnectable = isConnectable,
                rssi = rssi,
            ),
        )

    private fun device(spec: DeviceSpec): Device =
        Device(
            fingerprint = "AA:BB:CC:11:22:33",
            macAddress = "AA:BB:CC:11:22:33",
            macAddressType = MacAddressType.PUBLIC,
            technology = spec.technology,
            name = "Device",
            deviceType = spec.deviceType,
            vendorName = spec.vendorName,
            predictedModel = null,
            trackingStatus = spec.trackingStatus,
            followingScore = 0f,
            isSafeBeacon = false,
            isInWatchlist = false,
            userAlias = null,
            userNotes = null,
            alertSound = false,
            alertVibration = false,
            firstSeenAt = NOW,
            lastSeenAt = NOW,
            rssi = spec.rssi,
            encounterCount = 1,
            isConnectable = spec.isConnectable,
            connectionStatus = spec.connectionStatus,
        )

    private companion object {
        private const val NOW = 1_789_000_000_000L
        private val PUBLIC_SAFETY_OVERCLAIMS =
            listOf(
                "police",
                "law enforcement",
                "emergency radio",
                "body camera",
                "safety camera",
                "firefighter equip",
            )
        private val RSSI_RISK_VERDICT_COLORS =
            setOf(
                RadarUiColorToken.DANGEROUS,
                RadarUiColorToken.DANGEROUS_CONTAINER,
                RadarUiColorToken.SAFE,
                RadarUiColorToken.SAFE_CONTAINER,
                RadarUiColorToken.SUSPICIOUS,
                RadarUiColorToken.SUSPICIOUS_CONTAINER,
                RadarUiColorToken.WARNING,
            )
    }
}
