package io.blueeye.core.data.classifier

import io.blueeye.core.data.classifier.ble.ServiceUuids
import io.blueeye.core.data.classifier.model.BleClassificationInput
import io.blueeye.core.data.classifier.vendor.ManufacturerIds
import io.blueeye.core.decoders.parser.apple.AppleContinuityParser
import io.blueeye.core.decoders.parser.apple.AppleDeviceData
import io.blueeye.core.model.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DeviceClassifierTest {
    private val appleContinuityParser: AppleContinuityParser = mock()
    private val classifier = DeviceClassifier(appleContinuityParser)

    @Test
    fun `classifyBle uses structured service data fingerprint for fast pair model`() {
        val result =
            classifier.classifyBle(
                BleClassificationInput(
                    manufacturerRecords = emptyMap(),
                    serviceUuids = listOf(FAST_PAIR_UUID),
                    serviceDataByUuid = mapOf(FAST_PAIR_UUID to SONY_XM5_FAST_PAIR_PAYLOAD),
                    appearance = null,
                    deviceName = null,
                    vendorName = null,
                )
            )

        assertEquals(DeviceType.HEADPHONES, result)
    }

    @Test
    fun `classifyBle uses apple manufacturer record even when it is not primary`() {
        whenever(appleContinuityParser.parse(APPLE_PAYLOAD))
            .thenReturn(AppleDeviceData(deviceModel = "AirPods"))

        val result =
            classifier.classifyBle(
                BleClassificationInput(
                    manufacturerRecords =
                        linkedMapOf(
                            ManufacturerIds.SAMSUNG to byteArrayOf(0x01),
                            ManufacturerIds.APPLE to APPLE_PAYLOAD,
                        ),
                    serviceUuids = emptyList(),
                    serviceDataByUuid = emptyMap(),
                    appearance = null,
                    deviceName = null,
                    vendorName = null,
                )
            )

        assertEquals(DeviceType.HEADPHONES, result)
    }

    @Test
    fun `classifyBle keeps explicit MacBook identity over conflicting AirPods payload`() {
        whenever(appleContinuityParser.parse(APPLE_PAYLOAD))
            .thenReturn(AppleDeviceData(deviceModel = "AirPods"))

        val result =
            classifier.classifyBle(
                BleClassificationInput(
                    manufacturerRecords = mapOf(ManufacturerIds.APPLE to APPLE_PAYLOAD),
                    serviceUuids = emptyList(),
                    serviceDataByUuid = emptyMap(),
                    appearance = null,
                    deviceName = "MacBook Air",
                    vendorName = "Apple, Inc.",
                )
            )

        assertEquals(DeviceType.LAPTOP, result)
    }

    @Test
    fun `classifyBle keeps explicit AirPods identity over conflicting Mac payload`() {
        whenever(appleContinuityParser.parse(APPLE_PAYLOAD))
            .thenReturn(AppleDeviceData(deviceModel = "MacBook"))

        val result =
            classifier.classifyBle(
                BleClassificationInput(
                    manufacturerRecords = mapOf(ManufacturerIds.APPLE to APPLE_PAYLOAD),
                    serviceUuids = emptyList(),
                    serviceDataByUuid = emptyMap(),
                    appearance = null,
                    deviceName = "AirPods Pro 2",
                    vendorName = "Apple, Inc.",
                )
            )

        assertEquals(DeviceType.HEADPHONES, result)
    }

    private companion object {
        private const val FAST_PAIR_UUID = ServiceUuids.UUID_GOOGLE_FAST_PAIR
        private val SONY_XM5_FAST_PAIR_PAYLOAD = byteArrayOf(0xD4.toByte(), 0x46, 0xA7.toByte())
        private val APPLE_PAYLOAD = byteArrayOf(0x10, 0x02, 0x01, 0x02)
    }
}
