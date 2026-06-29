package io.blueeye.core.decoders.environmental

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** SensorPush (HT.w, HTP.xw) decoder. Theengs: SensorP_HT_json.h, SensorP_HTP_json.h */
@Singleton
class SensorPushDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val data = input.data
        // Tag 0109 or 0209 (manufacturerdata 10 or 14 bytes)
        if (data == null) return false

        // HTP (0x00): manufacturerdata = 14, index 0 is 0x00
        if (data.size == 12 && (data[0].toInt() and 0xFF) == 0x00) return true

        // HT (0x04): manufacturerdata = 10, index 0 is 0x04
        if (data.size == 8 && (data[0].toInt() and 0xFF) == 0x04) return true

        return false
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        try {
            val firstByte = data[0].toInt() and 0xFF

            // Shared logic: value_from_hex_data hex 4,8 or 4,12
            // Theengs use modulo logic for multiple properties in one field

            if (firstByte == 0x04) { // HT.w
                // tempc: val % 66001 * 0.0025 - 40
                // hum: val % 2640106001 / 66001 * 0.0025
                val raw = extractLong(data, 1, 4)
                val temp = (raw % 66001) * 0.0025 - 40.0
                val hum = (raw % 2640106001 / 66001) * 0.0025

                return SensorData(
                    temperatureCelcius = temp,
                    humidityPercent = hum,
                    beaconType = "SensorPush HT.w",
                    rawData = "SP HT: %.1f C, %.1f%%".format(temp, hum),
                )
            } else if (firstByte == 0x00) { // HTP.xw
                // tempc: val % 72001 * 0.0025 - 40
                // hum: val % 2880112001 / 72001 * 0.0025
                // pres: val % 273613520207001 / 2880112001 + 30000 / 100

                val raw = extractLong(data, 1, 6) // hex 2,12 = 6 bytes
                val temp = (raw % 72001) * 0.0025 - 40.0
                val hum = (raw % 2880112001L / 72001) * 0.0025
                val pres = (raw % 273613520207001L / 2880112001L + 30000.0) / 100.0

                return SensorData(
                    temperatureCelcius = temp,
                    humidityPercent = hum,
                    pressureHpa = pres,
                    beaconType = "SensorPush HTP.xw",
                    rawData = "SP HTP: %.1f C, %.1f%%, %.1f hPa".format(temp, hum, pres),
                )
            }
            return SensorData(beaconType = "SensorPush (Unknown Type)")
        } catch (e: Exception) {
            return SensorData(beaconType = "SensorPush (Parse Error)")
        }
    }

    private fun extractLong(
        data: ByteArray,
        start: Int,
        len: Int,
    ): Long {
        var res = 0L
        for (i in 0 until len) {
            if (start + i < data.size) {
                res = (res shl 8) or (data[start + i].toLong() and 0xFF)
            }
        }
        return res
    }
}
