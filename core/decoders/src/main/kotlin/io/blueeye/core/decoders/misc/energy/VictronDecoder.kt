package io.blueeye.core.decoders.misc.energy

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Victron Energy decoders (BSC, Orion XS, SBP, SBS, Encrypted). Theengs: VICTRON_BSC_json.h,
 * VICTRON_ORIONXS_json.h, VICTRON_SBP_json.h, VICTRON_SBS_json.h, VICTRON__ENCR_json.h
 */
@Singleton
class VictronDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Manufacturer ID 0x02E1 (LE "e102")
        if (manufacturerId != 0x02E1 || data == null) return false

        // Victron devices use many bytes in MD
        return data.size >= 12 && (data[0].toInt() and 0xFF) in 0x10..0x11
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        try {
            // Wait, hex indexing: hex 0,4 is ID. hex 4 is byte 2 of MD, which is index 0 of
            // payload.
            // Payload bytes:
            // 0: prefix (0x10 or 0x11)
            // ...
            // Let's use MD relative indices for clarity. (Prefix at 2, data[0])

            val typeByte = data[0].toInt() and 0xFF

            if (typeByte == 0x10) {
                return SensorData(
                    beaconType = "Victron Energy (Encrypted)",
                    sensorStatus = "Encrypted Data",
                    rawData = "Victron Encr: ${data.joinToString("") { "%02X".format(it) }}",
                )
            }

            // Type 0x11 usually means plain data (advertised)
            // We need to distinguish between models based on length and specific bytes

            // Orion XS: length 48, index 12 is 0x0fffff?
            // Actually, let's use a simpler heuristic for model name if possible,
            // but theengs just uses byte patterns.

            var type = "Victron Device"
            var status = ""
            var voltage: Double? = null
            var temperature: Double? = null

            // Check for Smart Battery Sense (length 50, index 12 hex = byte 6 = data[4])
            if (data.size == 48 && (data[4].toInt() and 0xFF) == 0x02) {
                type = "Victron Smart Battery Sense"
                val vRaw = extractInt(data, 9, 2) // hex 22 -> byte 11 -> data[9]
                if (vRaw != 0xFFFF) voltage = (vRaw and 0x3FFF) / 100.0 // mask 14 bits

                // tempc: hex 32 -> byte 16 -> data[14]
                val tRaw = extractInt(data, 14, 2)
                if (tRaw != 0xFFFF) temperature = (tRaw.toDouble() - 27315.0) / 100.0
            } else if (data.size == 44 && (data[4].toInt() and 0xFF) == 0x09) {
                type = "Victron Smart BatteryProtect"
                val vIn = extractInt(data, 14, 2) and 0x7FFF // hex 32 -> byte 16 -> data[14]
                voltage = vIn / 100.0
            } else if (data.size == 42) {
                type = "Victron Blue Smart Charger"
                val vBatt = extractInt(data, 9, 2) and 0x3FFF // hex 22 -> byte 11 -> data[9]
                voltage = vBatt / 100.0
                val tRaw = data[18].toInt() and 0x7F // hex 40 -> byte 20 -> data[18]
                temperature = (tRaw - 40).toDouble()
            } else if (data.size == 44 && (data[4].toInt() and 0xFF) == 0x0F) {
                type = "Victron Orion XS"
                val vOut = extractInt(data, 9, 2) // hex 22 -> byte 11 -> data[9]
                voltage = vOut / 100.0
            }

            return SensorData(
                voltageV = voltage,
                temperatureCelcius = temperature,
                sensorStatus = status.ifEmpty { "Active" },
                beaconType = type,
                rawData = "Victron: %.2f V".format(voltage ?: 0.0),
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Victron (Parse Error)")
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
