package io.blueeye.core.decoders.parser.samsung

import io.blueeye.core.model.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartTagParserTest {
    private val parser = SmartTagParser()

    @Test
    fun `Samsung Q60 field payload is not classified as SmartTag`() {
        assertNull(parser.parse(SAMSUNG_Q60_FIELD_FRAME, "[TV] Samsung Q60 Series (65)"))
    }

    @Test
    fun `Samsung 0x42 frame without model name is not a SmartTag`() {
        assertNull(parser.parse(SAMSUNG_42_FRAME))
    }

    @Test
    fun `recognized SmartTag model with corroborating frame is classified as tag`() {
        val result = parser.parse(SAMSUNG_42_FRAME, "Samsung SmartTag2")

        assertEquals(DeviceType.TAG, result?.deviceType)
        assertTrue(result?.isSmartTag == true)
    }

    @Test
    fun `truncated frame is rejected even with SmartTag name`() {
        assertNull(parser.parse(byteArrayOf(0x42, 0x01), "Samsung SmartTag2"))
    }

    private companion object {
        private val SAMSUNG_42_FRAME =
            byteArrayOf(0x42) + ByteArray(11) { 0x01 }
        private val SAMSUNG_Q60_FIELD_FRAME =
            "4204018066445CE99F3B47465CE99F3B46014F0000000000"
                .chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
    }
}
