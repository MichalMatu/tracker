package io.blueeye.core.decoders.parser.samsung

import io.blueeye.core.model.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartTagParserTest {
    private val parser = SmartTagParser()

    @Test
    fun `Samsung Q60 offline finding payload is not classified as SmartTag`() {
        val payload =
            "4204018066445CE99F3B47465CE99F3B46014F0000000000"
                .chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()

        val result = parser.parse(payload)

        assertNotNull(result)
        result!!
        assertEquals("Samsung Offline Finding", result.deviceModel)
        assertEquals(DeviceType.UNKNOWN, result.deviceType)
        assertTrue(result.isOfflineFinding)
        assertFalse(result.isSmartTag)
        assertNull(result.smartTagId)
    }
}
