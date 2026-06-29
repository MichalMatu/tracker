package io.blueeye.core.decoders.govee

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Govee H5072/H5074/H5075 Thermo-Hygrometer decoder. Theengs: H5072_json.h, H5074_json.h
 * Manufacturer ID: 0xEC88, Name starts with "GVH5072", "GVH5075", or "Govee_H5074"
 */
@Singleton
class GoveeH5072Decoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        val rawData = input.rawData
        // Manufacturer ID 0xEC88 (in JSON as "88ec" which is LE)
        if (manufacturerId != 0xEC88 || data == null) return false

        // Exclude H5179 (size 11)
        if (data.size == 11) return false

        // Check name contains GVH5072, GVH5075
        val localName = extractLocalName(rawData)
        val nameMatches =
            localName?.let {
                it.startsWith("GVH5072") ||
                    it.startsWith("GVH5075")
            }
                ?: false

        // If name matches, great. Otherwise just check data length
        // Also exclude H5074 explicitly if needed, but size check might be enough?
        // H5074 is handled by GoveeH5074Decoder now.

        return (data.size in 6..9) || nameMatches // range check is safer than >= 6
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        val rawData = input.rawData
        if (data.size < 6) return SensorData(beaconType = "Govee H5072 (Error)")

        try {
            val localName = extractLocalName(rawData)

            // H5072/H5075 format: Combined temp+hum in 3 bytes
            // Bytes 3-5 (Hex 6-11): 24-bit value
            val b1 = data[3].toInt() and 0xFF
            val b2 = data[4].toInt() and 0xFF
            val b3 = data[5].toInt() and 0xFF
            val rawValue = (b1 shl 16) or (b2 shl 8) or b3

            // Check sign bit (bit 23)
            val isNegative = (rawValue and 0x800000) != 0

            // Temperature = rawValue / 1000 / 10 for positive
            // OR (rawValue & 0x7FFFFF) / 10000 * -1 for negative
            val tempValue = rawValue and 0x7FFFFF
            val temp =
                if (isNegative) {
                    -(tempValue / 10000.0)
                } else {
                    rawValue / 10000.0
                }

            // Humidity = (rawValue & 0x7FFFFF) % 1000 / 10
            val humidity = (tempValue % 1000) / 10.0

            // Battery: Byte 6
            val batt = if (data.size > 6) data[6].toInt() and 0xFF else null

            val modelName =
                when {
                    localName?.startsWith("GVH5072") == true -> "Govee H5072"
                    localName?.startsWith("GVH5075") == true -> "Govee H5075"
                    else -> "Govee H5072/75"
                }

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = humidity,
                batteryLevel = batt,
                beaconType = modelName,
                rawData = "H5072: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Govee H5072 (Parse Error)")
        }
    }

    private fun extractLocalName(rawData: ByteArray?): String? {
        if (rawData == null) return null
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break

            val type = rawData[i + 1].toInt() and 0xFF
            if (type == 0x08 || type == 0x09) {
                val nameBytes = rawData.copyOfRange(i + 2, i + 1 + len)
                return String(nameBytes, Charsets.UTF_8)
            }
            i += 1 + len
        }
        return null
    }
}
