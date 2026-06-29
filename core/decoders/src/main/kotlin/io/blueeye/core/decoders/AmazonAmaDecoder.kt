package io.blueeye.core.decoders

import io.blueeye.core.decoders.parser.generic.ServiceDataExtractor
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decoder for Amazon AMA (Alexa Mobile Accessory) / Sidewalk-related BLE advertising.
 * Service Data UUID: 0xFE03.
 */
@Singleton
class AmazonAmaDecoder
@Inject
constructor() : BleBeaconDecoder {
    private companion object {
        const val AMA_UUID16 = 0xFE03
    }

    override fun supports(input: BleBeaconScanInput): Boolean {
        val rawData = input.rawData
        val serviceData = ServiceDataExtractor.extract16(rawData)
        return serviceData.containsKey(AMA_UUID16)
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        val rawData = input.rawData
        val payload = ServiceDataExtractor.extract16(rawData)[AMA_UUID16]
            ?: return SensorData(beaconType = "Amazon AMA", sensorStatus = "Missing service data")

        // Heuristic field layout (common in AMA docs/RE):
        // [0-1]=VendorId (LE), [2-3]=ProductId (LE), [4]=State/Flags (optional)
        val vendorId = if (payload.size >= 2) (payload[0].u8() or (payload[1].u8() shl 8)) else null
        val productId = if (payload.size >= 4) (payload[2].u8() or (payload[3].u8() shl 8)) else null
        val state = payload.getOrNull(4)?.u8()

        val status = buildString {
            if (vendorId != null) append("VendorId=0x${vendorId.toString(16).uppercase().padStart(4, '0')} ")
            if (productId != null) append("ProductId=0x${productId.toString(16).uppercase().padStart(4, '0')} ")
            if (state != null) append("State=0x${state.toString(16).uppercase().padStart(2, '0')}")
        }.trim().ifEmpty { "AMA Service Data" }

        return SensorData(
            beaconType = "Amazon AMA",
            sensorStatus = status,
            rawData = payload.toHex(),
        )
    }

    private fun Byte.u8(): Int = toInt() and 0xFF

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}
