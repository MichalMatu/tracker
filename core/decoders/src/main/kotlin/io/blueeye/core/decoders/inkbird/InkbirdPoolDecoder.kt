package io.blueeye.core.decoders.inkbird

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** Inkbird Pool Thermometer (IBS-P02B) decoder. Theengs: IBS_P02B_json.h */
@Singleton
class InkbirdPoolDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val data = input.data
        val rawData = input.rawData
        if (data == null || rawData == null) return false

        // Check name is IBS-P02B
        val localName = extractLocalName(rawData)
        if (localName?.startsWith("IBS-P02B") != true) return false

        // Manufacturer data = 36 hex chars = 18 bytes
        return data.size == 18
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 18) return SensorData(beaconType = "Inkbird Pool (Short)")

        try {
            // tempc: hex 12,2 -> byte 6, 2 bytes (Theengs says 12,2 which is 1 byte? No, hex 12 is
            // byte 6, len 2 chars is 1 byte)
            // wait, Theengs: "tempc": {"decoder": ["value_from_hex_data", "manufacturerdata", 12,
            // 2, true, false], "post_proc": ["/", 10]}
            // 12, 2 means start at hex 12, length 2 hex chars = 1 byte.
            val tempRaw = data[6].toInt().toByte() // Signed byte
            val temp = tempRaw / 10.0

            // batt: hex 20,2 -> byte 10
            val battery = data[10].toInt() and 0xFF

            // lowbatt: hex 26 char -> byte 13
            // "decoder": ["bit_static_value", "manufacturerdata", 26, 0, false, true]
            // char 26 is byte 13.
            val lowBatt = (data[13].toInt() and 0x01) != 0

            return SensorData(
                temperatureCelcius = temp,
                batteryLevel = battery,
                sensorStatus = if (lowBatt) "Low Battery" else null,
                beaconType = "Inkbird Pool Thermometer (IBS-P02B)",
                rawData = "Pool: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Inkbird Pool (Parse Error)")
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
