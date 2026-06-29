package io.blueeye.core.data.repository.handler.ble.enricher

import io.blueeye.core.data.repository.handler.ble.ScanDataContext
import io.blueeye.core.data.tracker.analysis.SpoofingDetector
import io.blueeye.core.model.DeviceType
import io.blueeye.core.scanner.model.BleScanResultData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FingerprintEnricherTest {
    private val enricher = FingerprintEnricher(SpoofingDetector())

    @Test
    fun `resolveFingerprintModel uses structured service data when raw scan record is unavailable`() {
        val context =
            ScanDataContext.fromScan(
                BleScanResultData(
                    mac = "AA:BB:CC:11:22:33",
                    rssi = -48,
                    timestamp = NOW,
                    technology = "BLE",
                    serviceUuids = listOf(FAST_PAIR_UUID),
                    serviceDataByUuid = mapOf(FAST_PAIR_UUID to SONY_XM5_FAST_PAIR_PAYLOAD),
                    rawData = null,
                )
            )

        assertEquals("Sony XM5", enricher.resolveFingerprintModel(context))
    }

    @Test
    fun `enrich ignores AirPods fingerprint when advertised name is MacBook`() {
        val context =
            ScanDataContext.fromScan(
                BleScanResultData(
                    mac = "55:19:63:4D:86:8D",
                    rssi = -35,
                    timestamp = NOW,
                    technology = "BLE",
                    name = "MacBook Air",
                    manufacturerId = APPLE_MANUFACTURER_ID,
                    manufacturerDataById = mapOf(APPLE_MANUFACTURER_ID to AIRPODS_PRO_2_PAYLOAD),
                    rawData = null,
                )
            )

        enricher.enrich(context)

        assertNull(context.fingerprintModel)
        assertNull(context.vendorModel)
        assertEquals(DeviceType.UNKNOWN, context.vendorDeviceType)
    }

    private companion object {
        private const val NOW = 1_750_000_000_000L
        private const val FAST_PAIR_UUID = "0000fe2c-0000-1000-8000-00805f9b34fb"
        private const val APPLE_MANUFACTURER_ID = 0x004C
        private val SONY_XM5_FAST_PAIR_PAYLOAD = byteArrayOf(0xD4.toByte(), 0x46, 0xA7.toByte())
        private val AIRPODS_PRO_2_PAYLOAD = byteArrayOf(0x07, 0x19, 0x07, 0x14)
    }
}
