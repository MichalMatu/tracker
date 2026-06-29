package io.blueeye.core.decoders.misc.energy

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TPMS BR / Truck / Generic TPMS Decoder.
 * Based on Theengs TPMSBR_json.h.
 * Manufacturer ID: 0x030A (LE "0a03")
 */
@Singleton
class TPMSBRDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        val rawData = input.rawData
        // Manufacturer ID 0x030A (LE "0a03").
        // In Android "data" might be NULL if parser ignores 0x030A, but usually it works if ID known.
        // If manufacturerId is provided as 0x030A.

        // Theengs condition: manufacturerdata starts with 14 (len), index 0 = "BR"?
        // wait, check JSON: "manufacturerdata", "=", 14, "&", "name", "index", 0, "BR"
        // This means Length of MD is 14 bytes (0x0E).
        // AND Name starts with "BR".

        val name = rawData?.let { extractLocalName(it) } ?: ""

        // Check Name prefix "BR"
        if (!name.startsWith("BR")) return false

        // Check Manufacturer Data Length.
        // If Android scan result provides 'data', it is payload only (ID stripped).
        // Total 14 bytes -> 2 bytes ID + 12 bytes Payload.
        // So data.size should be 12.

        // Allow matching if we have correct ID and size
        if (manufacturerId == 0x030A && data != null && data.size == 12) return true

        return false
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        try {
            // Layout from JSON:
            // tempc: manufacturerdata 4, 2 bytes (index 4 in Full MD) -> index 2 in Payload
            // volt: manufacturerdata 2, 2 bytes (index 2 in Full MD) -> index 0 in Payload
            // pres: manufacturerdata 6, 4 bytes (index 6 in Full MD) -> index 4 in Payload

            // data[0-1] = Voltage
            // data[2-3] = Temp
            // data[4-7] = Pressure

            // Volt: value / 10
            val voltRaw = extractInt(data, 0, 2)
            val voltage = voltRaw.toDouble() / 10.0

            // Temp: value
            val tempRaw = extractInt(data, 2, 2)
            val temp = tempRaw.toDouble()

            // Pressure: (value / 10 - 14.5) / 14.5 -> Convert to bar?
            // Theengs post_proc: ["/", 10, "-", 14.5, "/", 14.5]
            // Unit is "bar" in props.
            val presRaw = extractLong(data, 4, 4)
            val presCalc = ((presRaw.toDouble() / 10.0) - 14.5) / 14.5
            // Convert bar to hPa (x1000)
            val pressureHpa = presCalc * 1000.0

            return SensorData(
                temperatureCelcius = temp,
                voltageV = voltage,
                pressureHpa = pressureHpa,
                beaconType = "TPMS BR",
                rawData = "P: %.2f bar, T: %.0f C, V: %.1f V".format(presCalc, temp, voltage)
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "TPMS BR (Parse Error)")
        }
    }

    private fun extractInt(
        data: ByteArray,
        start: Int,
        len: Int,
    ): Int {
        var res = 0
        for (i in 0 until len) {
            res = (res shl 8) or (data[start + i].toInt() and 0xFF)
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
            res = (res shl 8) or (data[start + i].toLong() and 0xFF)
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
