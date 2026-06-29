package io.blueeye.core.data.classifier

import io.blueeye.core.decoders.beacon.IBeaconDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BeaconDecoderManagerTest {
    private val manager = BeaconDecoderManager(setOf(IBeaconDecoder()))

    @Test
    fun `decodes beacon from non primary manufacturer record`() {
        val result =
            manager.decode(
                mac = "AA:BB:CC:DD:EE:FF",
                manufacturerRecords =
                    linkedMapOf(
                        0x0075 to byteArrayOf(0x01, 0x02, 0x03),
                        0x004C to iBeaconPayload(),
                    ),
                serviceUuids = emptyList(),
                rawData = null,
            )

        assertNotNull(result)
        assertEquals("iBeacon", result?.beaconType)
        assertEquals("UUID: 00112233-4455-6677-8899-AABBCCDDEEFF, Major: 1, Minor: 2", result?.sensorStatus)
    }

    private fun iBeaconPayload(): ByteArray =
        byteArrayOf(
            0x02,
            0x15,
            0x00,
            0x11,
            0x22,
            0x33,
            0x44,
            0x55,
            0x66,
            0x77,
            0x88.toByte(),
            0x99.toByte(),
            0xAA.toByte(),
            0xBB.toByte(),
            0xCC.toByte(),
            0xDD.toByte(),
            0xEE.toByte(),
            0xFF.toByte(),
            0x00,
            0x01,
            0x00,
            0x02,
            0xC5.toByte(),
        )
}
