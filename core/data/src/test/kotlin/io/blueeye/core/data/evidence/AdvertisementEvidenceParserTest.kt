package io.blueeye.core.data.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvertisementEvidenceParserTest {
    @Test
    fun privacySafeLimeLegacyRecordDoesNotFabricateStructuredEvidence() {
        val evidence = AdvertisementEvidenceParser.parse(privacySafeLimeLegacyRecordHex())

        assertNull(evidence.appearance)
        assertTrue(evidence.manufacturerIds.isEmpty())
        assertTrue(evidence.serviceUuids.isEmpty())
    }

    @Test
    fun truncatedRecordKeepsEarlierValidEvidenceWithoutFabricatingTrailingFields() {
        val evidence = AdvertisementEvidenceParser.parse("0319410305FF4C00")

        assertEquals(0x0341, evidence.appearance)
        assertTrue(evidence.manufacturerIds.isEmpty())
        assertTrue(evidence.serviceUuids.isEmpty())
    }

    @Test
    fun parsesRepresentativeAppearanceManufacturerAndServiceUuidEvidence() {
        val evidence =
            AdvertisementEvidenceParser.parse(
                "03194103" +
                    "03FF4C00" +
                    "03036FFD" +
                    "04162CFE01",
            )

        assertEquals(0x0341, evidence.appearance)
        assertEquals(listOf(0x004C), evidence.manufacturerIds)
        assertEquals(
            listOf(
                "0000fd6f-0000-1000-8000-00805f9b34fb",
                "0000fe2c-0000-1000-8000-00805f9b34fb",
            ),
            evidence.serviceUuids,
        )
    }

    private fun privacySafeLimeLegacyRecordHex(): String {
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

        return (advertisingData + scanResponse).joinToString("") { byte ->
            "%02X".format(byte.toInt() and 0xFF)
        }
    }
}
