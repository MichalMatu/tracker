
package io.blueeye.core.decoders.misc

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tilt Brewing Hydrometer decoder.
 * Theengs: TILT_json.h
 * iBeacon format with specific UUID pattern for brewing hydrometers.
 */
@Singleton
class TiltDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Apple manufacturer ID (iBeacon)
        if (manufacturerId != 0x004C || data == null) return false

        // 50 hex chars = 25 bytes, minus ID = 23 bytes
        if (data.size < 23) return false

        // Check iBeacon prefix 02 15
        if (data[0] != 0x02.toByte() || data[1] != 0x15.toByte()) return false

        // Check UUID pattern: starts with A495BB (byte 2-4) and specific bytes at offset
        // UUID: A495BBxx-C5B1-4B44-B512-1370F02D74DE
        if (data[2] != 0xA4.toByte() || data[3] != 0x95.toByte() || data[4] != 0xBB.toByte()) {
            return false
        }

        return true
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 23) return SensorData(beaconType = "Tilt (Error)")

        try {
            // Color: Byte 7 (Index 7 in 'data', hex 14-15 in full manuf data)
            val colorByte = data[7].toInt() and 0xFF
            val color =
                when (colorByte) {
                    0x10 -> "Red"
                    0x20 -> "Green"
                    0x30 -> "Black"
                    0x40 -> "Purple"
                    0x50 -> "Orange"
                    0x60 -> "Blue"
                    0x70 -> "Yellow"
                    0x80 -> "Pink"
                    else -> "Unknown"
                }

            // Temperature: Bytes 18-19 (Major value), BE, in Fahrenheit
            val tempHigh = data[18].toInt() and 0xFF
            val tempLow = data[19].toInt() and 0xFF
            val tempF = (tempHigh shl 8) or tempLow
            val tempC = (tempF - 32) * 5.0 / 9.0

            // Specific Gravity: Bytes 20-21 (Minor value), BE, /1000
            val gravHigh = data[20].toInt() and 0xFF
            val gravLow = data[21].toInt() and 0xFF
            val gravityRaw = (gravHigh shl 8) or gravLow
            val gravity = gravityRaw / 1000.0

            val statusStr = "Color: $color, Gravity: %.3f SG".format(gravity)

            return SensorData(
                temperatureCelcius = tempC,
                sensorStatus = statusStr,
                beaconType = "Tilt Hydrometer ($color)",
                rawData = "Tilt: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Tilt (Parse Error)")
        }
    }
}
