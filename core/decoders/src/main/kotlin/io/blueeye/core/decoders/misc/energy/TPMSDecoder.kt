package io.blueeye.core.decoders.misc.energy

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** Generic TPMS decoder and Oria T201/T301. Theengs: TPMS_json.h, T201_json.h, T301_json.h */
@Singleton
class TPMSDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val data = input.data
        val rawData = input.rawData
        if (data == null) return false

        // T201 / T301: manufacturerdata 38, name T201/T301
        val name = rawData?.let { extractLocalName(it) }
        if ((name == "T201" || name == "T301") && data.size >= 36) return true

        // Generic TPMS: manufacturerdata 36, starts with "000" in hex?
        // No, "index", 0, "000" in hex is 00 0... Theengs hex 0, 3 chars is 00 0.
        if (data.size == 34 && (data[0].toInt() == 0)) return true

        return false
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        val rawData = input.rawData
        val name = rawData?.let { extractLocalName(it) }

        try {
            if (name == "T201" || name == "T301") {
                // T201/T301: byte 24-27 temp / 100, 28-31 hum / 100, 32-33 batt
                // indices relative to payload (after ID 2 bytes)
                val tRaw = extractInt(data, 22, 4) // data[22..25]
                val temp = tRaw.toDouble() / 100.0
                val hRaw = extractInt(data, 26, 4)
                val hum = hRaw.toDouble() / 100.0
                val battery = extractInt(data, 30, 2)

                return SensorData(
                    temperatureCelcius = temp,
                    humidityPercent = hum,
                    batteryLevel = battery,
                    beaconType = "Oria $name TH Sensor",
                    rawData = "$name: %.1f C, %.1f%%".format(temp, hum),
                )
            } else {
                // Generic TPMS: hex 16,8 pressure / 100000, 24,8 temp / 100, 32,2 batt
                // bytes: hex 16 = byte 8, length 8 hex = 4 bytes
                extractLong(data, 6, 4)
                // If manufacturerdata is 36, and we skip ID (2 bytes), then:
                // Byte 0-1: ID, Byte 2..8: ?
                // Theengs hex indexing starts from 0 of manufacturerdata.
                // ID is hex 0,4. Hex 4,2 is byte 2. Hex 16 is byte 8.
                // Since 'data' starts at byte 2 of MD:
                // hex 16 -> byte 8 of MD -> byte 6 of 'data'. Correct.

                val pres = extractLong(data, 6, 4).toDouble() / 100000.0 // bar
                val temp = extractLong(data, 10, 4).toDouble() / 100.0
                val battery =
                    extractInt(
                        data,
                        14,
                        1,
                    ) // hex 32,2 is byte 16 of MD -> byte 14 of data. Correct.

                return SensorData(
                    temperatureCelcius = temp,
                    batteryLevel = battery,
                    pressureHpa = pres * 1000.0, // Convert bar to hPa roughly or keep in raw
                    beaconType = "Generic TPMS",
                    rawData = "P: %.2f bar, T: %.1f C".format(pres, temp),
                )
            }
        } catch (e: Exception) {
            return SensorData(beaconType = "TPMS (Parse Error)")
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

    private fun extractLocalName(rawData: ByteArray): String? {
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break
            val type = rawData[i + 1].toInt() and 0xFF
            if (type == 0x08 || type == 0x09) {
                return String(rawData.copyOfRange(i + 2, i + 1 + len), Charsets.UTF_8)
            }
            i += 1 + len
        }
        return null
    }
}
