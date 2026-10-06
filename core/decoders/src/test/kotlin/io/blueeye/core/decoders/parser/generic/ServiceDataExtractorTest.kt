package io.blueeye.core.decoders.parser.generic

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceDataExtractorTest {
    @Test
    fun extracts16BitServiceDataFromCompleteStructure() {
        val record =
            byteArrayOf(
                0x06,
                0x16,
                0x2C,
                0xFE.toByte(),
                0x01,
                0x02,
                0x03,
            )

        val result = ServiceDataExtractor.extract16(record)

        assertEquals(setOf(0xFE2C), result.keys)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), result.getValue(0xFE2C))
    }

    @Test
    fun keepsPayloadLess16BitServiceDataAsEmptyPayload() {
        val record =
            byteArrayOf(
                0x03,
                0x16,
                0x2C,
                0xFE.toByte(),
            )

        val result = ServiceDataExtractor.extract16(record)

        assertEquals(setOf(0xFE2C), result.keys)
        assertArrayEquals(byteArrayOf(), result.getValue(0xFE2C))
    }

    @Test
    fun ignoresTruncatedServiceDataStructure() {
        val record =
            byteArrayOf(
                0x06,
                0x16,
                0x2C,
                0xFE.toByte(),
                0x01,
            )

        assertTrue(ServiceDataExtractor.extract16(record).isEmpty())
    }
}
