package io.blueeye.feature.details

import io.blueeye.core.model.GattCharacteristic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailsGattCharacteristicFormatterTest {
    @Test
    fun `saved characteristic data remains visible without saved service list`() {
        assertEquals(true, DetailsGattEvidencePolicy.hasEvidence(emptyList(), null, "2A19=64"))
        assertEquals(true, DetailsGattEvidencePolicy.hasEvidence(emptyList(), "180F", null))
        assertEquals(false, DetailsGattEvidencePolicy.hasEvidence(emptyList(), null, null))
        assertEquals(false, DetailsGattEvidencePolicy.hasEvidence(emptyList(), " ", ""))
    }

    @Test
    fun `shows UUID properties and read values without hiding capabilities`() {
        val source =
            GattCharacteristic(
                uuid = "00002a19-0000-1000-8000-00805f9b34fb",
                name = "Battery Level",
                properties = listOf("READ", "NOTIFY"),
                value = "0xHex:64",
            )
        val result = DetailsGattCharacteristicFormatter.format(source)
        assertEquals("Battery Level — 00002a19-0000-1000-8000-00805f9b34fb", result.heading)
        assertEquals("Properties: READ, NOTIFY", result.properties)
        assertEquals("Read value: 0xHex:64", result.value)
    }

    @Test
    fun `does not invent a read value for inaccessible characteristic`() {
        val result =
            DetailsGattCharacteristicFormatter.format(
                GattCharacteristic("test", "Restricted", listOf("WRITE"), null),
            )
        assertEquals("Properties: WRITE", result.properties)
        assertNull(result.value)
    }
}
