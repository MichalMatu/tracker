package io.blueeye.core.connectivity.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GattBatteryLevelDecoderTest {
    @Test
    fun `one byte GATT battery level is unsigned percent`() {
        assertEquals(0, GattBatteryLevelDecoder.parse(byteArrayOf(0)))
        assertEquals(2, GattBatteryLevelDecoder.parse(byteArrayOf(2)))
        assertEquals(100, GattBatteryLevelDecoder.parse(byteArrayOf(100)))
    }

    @Test
    fun `missing malformed and out of range values are not battery percentages`() {
        assertNull(GattBatteryLevelDecoder.parse(byteArrayOf()))
        assertNull(GattBatteryLevelDecoder.parse(byteArrayOf(50, 0)))
        assertNull(GattBatteryLevelDecoder.parse(byteArrayOf(101.toByte())))
        assertNull(GattBatteryLevelDecoder.parse(byteArrayOf(0x80.toByte())))
        assertNull(GattBatteryLevelDecoder.parse(byteArrayOf(0xFF.toByte())))
    }
}
