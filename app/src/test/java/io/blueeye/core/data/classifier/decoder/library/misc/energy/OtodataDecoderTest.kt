package io.blueeye.core.data.classifier.decoder.library.misc.energy

import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.decoders.misc.energy.OtodataDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OtodataDecoderTest {
    private val decoder = OtodataDecoder()

    @Test
    fun testDecodeLevelAndStatus() {
        // Manufacturer data payload length 19 (Theengs 21 - 2 ID)
        // ID: 03 B1 (Otodata) - Passed separately

        val data = ByteArray(19)
        // Theengs byte 11 -> Payload byte 9.
        // Byte 9, 10: Level 5000 (0x1388)
        data[9] = 0x13
        data[10] = 0x88.toByte()

        // Theengs byte 13 -> Payload byte 11.
        // Byte 11, 12: Status 1 (0x0001)
        data[11] = 0x00
        data[12] = 0x01

        val supports =
            decoder.supports(
                otodataInput(data),
            )
        assertTrue(supports)

        val result =
            decoder.decode(
                otodataInput(data),
            )

        assertEquals("Otodata Rotarex Monitor", result.beaconType)
        assertEquals(50.0, result.soilMoisturePercent!!, 0.01) // Level mapped to soilMoisture
        assertEquals("Status: 1", result.sensorStatus)
    }

    @Test
    fun testDecodeSerialAndModelType() {
        // Manufacturer data payload length 22 (Theengs 24 - 2 ID)
        val data = ByteArray(22)

        // Theengs byte 9 -> Payload byte 7.
        // Byte 7-10: Serial. Let's say 12345678 -> 0x00BC614E
        data[7] = 0x00
        data[8] = 0xBC.toByte()
        data[9] = 0x61
        data[10] = 0x4E

        // Theengs byte 20 -> Payload byte 18.
        // Byte 18-21: ModelType. Let's say 101 -> 0x00000065
        data[18] = 0x00
        data[19] = 0x00
        data[20] = 0x00
        data[21] = 0x65

        val supports =
            decoder.supports(
                otodataInput(data),
            )
        assertTrue(supports)

        val result =
            decoder.decode(
                otodataInput(data),
            )

        assertEquals("Otodata Rotarex Monitor", result.beaconType)
        // Check rawData or status string contains Serial and ModelType
        assertTrue(result.rawData?.contains("Serial: 12345678") == true)
        assertTrue(result.rawData?.contains("ModelType: 101") == true)
        // Also check sensorStatus
        assertTrue(result.sensorStatus?.contains("Serial: 12345678") == true)
    }

    private fun otodataInput(data: ByteArray): BleBeaconScanInput =
        BleBeaconScanInput(
            mac = "AA:BB:CC:DD:EE:FF",
            manufacturerRecords = mapOf(0x03B1 to data),
            serviceUuids = emptyList(),
            rawData = null,
        )
}
