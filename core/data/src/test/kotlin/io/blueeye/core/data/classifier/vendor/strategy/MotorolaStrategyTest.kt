package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.ManufacturerIds
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorolaStrategyTest {
    private val strategy = MotorolaStrategy()

    @Test
    fun `Motorola company id is handled`() {
        assertTrue(strategy.canHandle(inputWithManufacturer(ManufacturerIds.MOTOROLA)))
    }

    @Test
    fun `Cambridge Silicon Radio company id is not handled as Motorola`() {
        assertFalse(strategy.canHandle(inputWithManufacturer(CAMBRIDGE_SILICON_RADIO_ID)))
    }

    private fun inputWithManufacturer(manufacturerId: Int): VendorScanInput =
        VendorScanInput(
            manufacturerRecords = mapOf(manufacturerId to byteArrayOf(0x01)),
            serviceUuids = emptyList(),
        )

    private companion object {
        const val CAMBRIDGE_SILICON_RADIO_ID = 0x000A
    }
}
