package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.decoders.parser.samsung.SamsungManufacturerParser
import io.blueeye.core.model.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

class SamsungStrategyTest {
    private val manufacturerParser: SamsungManufacturerParser = mock()
    private val strategy = SamsungStrategy(manufacturerParser)

    @Test
    fun `SmartThings Find service uuid is handled without Samsung manufacturer record`() {
        val input =
            VendorScanInput(
                manufacturerRecords = emptyMap(),
                serviceUuids = listOf(SMARTTHINGS_FIND_UUID),
            )

        assertTrue(strategy.canHandle(input))

        val result = strategy.decode(input)

        assertEquals(DeviceType.SAMSUNG_TAG, result.deviceType)
        assertEquals("Samsung Device", result.modelName)
        assertEquals("SmartThings Find.", result.extraInfo)
        verifyNoInteractions(manufacturerParser)
    }

    @Test
    fun `Fast Pair service uuid alone is not treated as Samsung Quick Share`() {
        val input =
            VendorScanInput(
                manufacturerRecords = emptyMap(),
                serviceUuids = listOf(FAST_PAIR_UUID),
            )

        assertFalse(strategy.canHandle(input))
        verifyNoInteractions(manufacturerParser)
    }

    private companion object {
        private const val FAST_PAIR_UUID = "0000fe2c-0000-1000-8000-00805f9b34fb"
        private const val SMARTTHINGS_FIND_UUID = "0000fd5a-0000-1000-8000-00805f9b34fb"
    }
}
