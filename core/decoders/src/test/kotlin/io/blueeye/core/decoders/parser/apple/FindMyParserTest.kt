package io.blueeye.core.decoders.parser.apple

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FindMyParserTest {
    private val parser = FindMyParser()

    @Test
    fun `reserved low bit is not a low-battery flag`() {
        val parsed = requireNotNull(parser.parse(byteArrayOf(0x25, 0x01)))
        assertEquals("Find My [Maintained]", parsed.deviceModel)
        assertEquals(0x25, parsed.statusFlags)
        assertNull(parsed.batteryLevelLeft)
    }

    @Test
    fun `maintained battery bits distinguish low and critical`() {
        val low = requireNotNull(parser.parse(byteArrayOf(0x84.toByte(), 0x01)))
        val critical = requireNotNull(parser.parse(byteArrayOf(0xC4.toByte(), 0x01)))
        assertTrue(low.deviceModel!!.contains("[Low Batt]"))
        assertTrue(critical.deviceModel!!.contains("[Critical Batt]"))
        assertNull(low.batteryLevelLeft)
        assertNull(critical.batteryLevelLeft)
    }

    @Test
    fun `battery state without maintained flag remains unknown`() {
        val parsed = requireNotNull(parser.parse(byteArrayOf(0x80.toByte(), 0x01)))
        assertEquals("Find My [Not Maintained]", parsed.deviceModel)
        assertFalse(parsed.deviceModel!!.contains("Batt"))
    }
}
