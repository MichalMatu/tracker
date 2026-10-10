package io.blueeye.core.decoders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AirTagBroadcastDecoderTest {
    private val decoder = AirTagBroadcastDecoder()

    @Test
    fun `status 0x25 is maintained with full battery`() {
        val input = input(0x25)
        assertTrue(decoder.supports(input))
        assertEquals("Bat: Full (St: 0x25)", decoder.decode(input).sensorStatus)
    }

    @Test
    fun `unmaintained flags cannot claim battery charge`() {
        assertEquals("Bat: Unknown (St: 0x80)", decoder.decode(input(0x80)).sensorStatus)
    }

    @Test
    fun `maintained low battery is derived from high bits`() {
        assertEquals("Bat: Low (St: 0x84)", decoder.decode(input(0x84)).sensorStatus)
    }

    private fun input(status: Int) = BleBeaconScanInput(
        mac = "",
        manufacturerRecords = mapOf(
            76 to byteArrayOf(0x12, 0x19, status.toByte()) + ByteArray(22),
        ),
        serviceUuids = emptyList(),
    )
}
