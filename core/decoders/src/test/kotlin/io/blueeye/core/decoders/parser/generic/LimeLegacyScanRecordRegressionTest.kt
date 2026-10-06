package io.blueeye.core.decoders.parser.generic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimeLegacyScanRecordRegressionTest {
    private val scanRecordParser = ScanRecordParser()

    @Test
    fun passiveLegacyRecordDoesNotFabricateAppearanceOrServiceData() {
        val record = privacySafeLegacyRecord()

        assertEquals(59, record.size)
        assertNull(scanRecordParser.extractAppearance(record))
        assertTrue(ServiceDataExtractor.extract16(record).isEmpty())
    }

    @Test
    fun truncatedPrefixesRemainBoundedAndDoNotFabricateFields() {
        val record = privacySafeLegacyRecord()

        for (size in 0..record.size) {
            val truncated = record.copyOf(size)

            assertNull(scanRecordParser.extractAppearance(truncated))
            assertTrue(ServiceDataExtractor.extract16(truncated).isEmpty())
        }
    }

    private fun privacySafeLegacyRecord(): ByteArray {
        val reservedPayload = ByteArray(25) { index -> (0x10 + index).toByte() }
        val advertisingData =
            byteArrayOf(
                0x02,
                0x01,
                0x06,
                0x1A,
                0x00,
            ) + reservedPayload + byteArrayOf(0x00)

        val syntheticName = "lime-931303000001".encodeToByteArray()
        val scanResponse =
            byteArrayOf(
                0x12,
                0x09,
            ) +
                syntheticName +
                byteArrayOf(
                    0x05,
                    0x12,
                    0x06,
                    0x00,
                    0x0C,
                    0x00,
                    0x02,
                    0x0A,
                    0x00,
                )

        assertEquals(31, advertisingData.size)
        assertEquals(28, scanResponse.size)
        return advertisingData + scanResponse
    }
}
