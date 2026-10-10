package io.blueeye.core.decoders.environmental

import io.blueeye.core.decoders.BleBeaconScanInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermoProDecoderTest {
    private val decoder = ThermoProDecoder()

    @Test
    fun tp357BatteryStatusUsesTwoBitMapping() {
        val expectedByStatus = mapOf(
            0x00 to 1,
            0x01 to 50,
            0x02 to 100,
            0x03 to null,
            0x82 to 100,
        )

        expectedByStatus.forEach { (status, expectedBattery) ->
            val input = BleBeaconScanInput(
                mac = "",
                manufacturerRecords = emptyMap(),
                serviceUuids = emptyList(),
                rawData = ("0D0954503335372028544553542907FFC2F2002E" +
                    "%02X".format(status) + "2C").hexToBytes(),
            )

            assertTrue("Should recognize TP357, status=$status", decoder.supports(input))
            val decoded = decoder.decode(input)
            assertEquals(24.2, decoded.temperatureCelcius!!, 0.001)
            assertEquals(46.0, decoded.humidityPercent!!, 0.001)
            if (expectedBattery == null) {
                assertNull(decoded.batteryLevel)
            } else {
                assertEquals(expectedBattery, decoded.batteryLevel)
            }
        }
    }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
