package io.blueeye.core.decoders.inkbird

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inkbird IBS-TH1/TH2/P01B temperature/humidity sensor decoder. Theengs: IBS_THBP01B_json.h Name
 * starts with "sps" or "tps"
 */
@Singleton
class InkbirdTHDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val data = input.data
        val rawData = input.rawData
        if (data == null || rawData == null) return false

        // Check name starts with "sps" or "tps"
        val localName = extractLocalName(rawData)
        val nameMatches =
            localName?.lowercase()?.let { it.startsWith("sps") || it.startsWith("tps") }
                ?: false

        if (!nameMatches) return false

        // Manufacturer data = 18 hex chars = 9 bytes (after ID)
        return data.size >= 7
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 7) return SensorData(beaconType = "Inkbird TH (Error)")

        try {
            // Temperature: Bytes 0-1 (hex 0-3), LE, Signed, /100
            val tLow = data[0].toInt() and 0xFF
            val tHigh = data[1].toInt() and 0xFF
            val tempRaw = ((tHigh shl 8) or tLow).toShort()
            val temp = tempRaw / 100.0

            // Humidity: Bytes 2-3 (hex 4-7), LE, Signed, /100
            // Only if not 0xFFFF or 0x0000
            var humidity: Double? = null
            val hLow = data[2].toInt() and 0xFF
            val hHigh = data[3].toInt() and 0xFF
            val humRaw = (hHigh shl 8) or hLow
            if (humRaw != 0xFFFF && humRaw != 0x0000) {
                humidity = ((hHigh shl 8) or hLow).toShort() / 100.0
            }

            // Battery: Byte 7 (hex 14-15)
            // Only if not 0x0F or 0x0E
            var battery: Int? = null
            if (data.size > 7) {
                val battByte = data[7].toInt() and 0xFF
                if (battByte != 0x0F && battByte != 0x0E) {
                    battery = battByte
                }
            }

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = humidity,
                batteryLevel = battery,
                beaconType = "Inkbird TH Sensor",
                rawData = "Inkbird: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Inkbird TH (Parse Error)")
        }
    }

    private fun extractLocalName(rawData: ByteArray): String? {
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
