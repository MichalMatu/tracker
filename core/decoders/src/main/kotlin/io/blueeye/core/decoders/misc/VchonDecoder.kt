package io.blueeye.core.decoders.misc

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decoder for VCHON Thermo-Hygrometer (VCH6003).
 * Theengs: VCH6003_json.h
 */
@Singleton
class VchonDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // VCHON: manufacturerdata 22 (0x16 length), ID 0x0901 (LE "0109")
        return manufacturerId == 0x0901 && data != null && data.size == 20
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        try {
            // value_from_hex_data at index 4, length 4 (Theengs).
            // Theengs indices are from start of MD (ID included).
            // ID is 2 bytes. So indices in `data` are at -2.
            // data[2] = MD[4].
            val tRaw = extractInt(data, 2, 2)
            val temp = tRaw.toDouble() / 10.0

            // hum at index 8, length 2 (Theengs).
            // data[6] = MD[8].
            val hum = data[6].toInt() and 0xFF

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = hum.toDouble(),
                beaconType = "VCHON Thermo-Hygrometer",
                rawData = "VCHON: %.1f C, %d%%".format(temp, hum),
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "VCHON (Parse Error)")
        }
    }

    private fun extractInt(
        data: ByteArray,
        start: Int,
        len: Int,
    ): Int {
        var res = 0
        for (i in 0 until len) {
            if (start + i < data.size) {
                res = (res shl 8) or (data[start + i].toInt() and 0xFF)
            }
        }
        return res
    }
}
