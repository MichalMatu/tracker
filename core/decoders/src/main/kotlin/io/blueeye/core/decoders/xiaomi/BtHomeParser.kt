package io.blueeye.core.decoders.xiaomi

import io.blueeye.core.model.SensorData

/**
 * BTHome v2 format parser for Xiaomi devices with PVVX custom firmware.
 * https://bthome.io/format/
 */
object BtHomeParser {

    fun decode(data: ByteArray): SensorData {
        android.util.Log.d(
            "XiaomiDecoder",
            "BTHome Raw (${data.size} bytes): ${data.joinToString("") { "%02X".format(it) }}"
        )

        // Check for encrypted BTHome (Starts with 0x41)
        if (data.isNotEmpty() && (data[0].toInt() and 0xFF) == 0x41 && data.size >= 16) {
            return SensorData(
                beaconType = "Xiaomi LYWSD03MMC (BTHome Encrypted)",
                rawData = "Encrypted BTHome: ${data.joinToString("") { "%02X".format(it) }}",
            )
        }

        var temp: Double? = null
        var hum: Double? = null
        var batt: Int? = null
        var volt: Double? = null

        var i = 1 // Skip Device Info byte
        while (i < data.size) {
            val objType = data[i].toInt() and 0xFF
            i++ // Move past object ID

            when (objType) {
                0x01 -> { // Battery (1 byte, uint8, %)
                    if (i < data.size) {
                        batt = data[i].toInt() and 0xFF
                        i += 1
                    }
                }
                0x02 -> { // Temperature (2 bytes, int16 LE, 0.01)
                    if (i + 1 < data.size) {
                        val b1 = data[i].toInt() and 0xFF
                        val b2 = data[i + 1].toInt() and 0xFF
                        val raw = ((b2 shl 8) or b1).toShort()
                        temp = raw * 0.01
                        i += 2
                    }
                }
                0x03 -> { // Humidity (2 bytes, uint16 LE, 0.01)
                    if (i + 1 < data.size) {
                        val b1 = data[i].toInt() and 0xFF
                        val b2 = data[i + 1].toInt() and 0xFF
                        val raw = ((b2 shl 8) or b1)
                        hum = raw * 0.01
                        i += 2
                    }
                }
                0x0C -> { // Voltage (2 bytes, uint16 LE, mV)
                    if (i + 1 < data.size) {
                        val b1 = data[i].toInt() and 0xFF
                        val b2 = data[i + 1].toInt() and 0xFF
                        val raw = ((b2 shl 8) or b1)
                        volt = raw / 1000.0
                        i += 2
                    }
                }
                0x45 -> { // Temperature Precise (2 bytes, int16 LE, 0.1)
                    if (i + 1 < data.size) {
                        val b1 = data[i].toInt() and 0xFF
                        val b2 = data[i + 1].toInt() and 0xFF
                        val raw = ((b2 shl 8) or b1).toShort()
                        temp = raw * 0.1
                        i += 2
                    }
                }
                0x46 -> { // Humidity Precise (2 bytes, uint16 LE, 0.1)
                    if (i + 1 < data.size) {
                        val b1 = data[i].toInt() and 0xFF
                        val b2 = data[i + 1].toInt() and 0xFF
                        val raw = ((b2 shl 8) or b1)
                        hum = raw * 0.1
                        i += 2
                    }
                }
                else -> {
                    // Skip unknown object
                    val skip = getObjectSize(objType)
                    if (skip > 0 && i + skip <= data.size) {
                        i += skip
                    } else {
                        break
                    }
                }
            }
        }

        return SensorData(
            temperatureCelcius = temp,
            humidityPercent = hum,
            batteryLevel = batt,
            voltageV = volt,
            beaconType = "Xiaomi LYWSD03MMC (BTHome)",
            rawData = "BTHome: ${data.joinToString("") { "%02X".format(it) }}",
        )
    }

    private fun getObjectSize(objectId: Int): Int {
        return when (objectId) {
            // 1-byte
            0x00, 0x01, 0x2E, 0x3A, 0x3C -> 1
            // 2-byte
            0x02, 0x03, 0x0C, 0x0D, 0x43, 0x45, 0x46, 0x4A -> 2
            // 3-byte
            0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x40, 0x41, 0x42 -> 3
            // 4-byte
            0x4D, 0x4E, 0x4F -> 4
            else -> 0
        }
    }
}
