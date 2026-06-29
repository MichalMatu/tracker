package io.blueeye.core.decoders.misc.tuya

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decoder for Tuya/Syska/Generic RGB BLE Light Bulbs.
 *
 * Common characteristics:
 * - Names: Cnligh, LED_BLE, Triones, LEDBlue, LEDBLE, Smart Light, etc.
 * - Often use Texas Instruments CC254x or Telink chips
 * - Service UUID: Often FFE5 (control) and FFE0 (status)
 * - Simple RGB protocol: [header][R][G][B][brightness]
 *
 * Reference: "Reverse Engineering Smart BLE Devices" (Instructables)
 */
@Singleton
class TuyaRGBBulbDecoder
@Inject
constructor() : BleBeaconDecoder {
    companion object {
        // Common name patterns for cheap RGB BLE bulbs
        private val BULB_NAME_PATTERNS =
            listOf(
                "cnligh", // Syska/Cnlight
                "led_ble", // Generic LED
                "ledble", // Generic LED
                "triones", // Triones/HappyLighting
                "ledblue", // Magic Blue
                "elk-ble", // ELK-BLEDOM
                "ble-led", // Generic
                "smart light", // Generic smart bulb
                "magic", // Magic Hue
                "hue", // Generic (not Philips)
                "rgbw", // RGB+White
                "rgb", // RGB bulb
                "bulb", // Generic bulb
            )

        // Common service UUIDs for RGB bulbs
        private const val SERVICE_FFE5 = 0xFFE5 // Control service
        private const val SERVICE_FFE0 = 0xFFE0 // Status service
    }

    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        // Check by name pattern
        val name = rawData?.let { extractLocalName(it) }?.lowercase() ?: ""
        val nameMatches = BULB_NAME_PATTERNS.any { pattern -> name.contains(pattern) }

        // Check for common bulb service UUIDs
        val hasRgbService =
            serviceUuids.any { uuid ->
                uuid.uppercase().contains("FFE5") ||
                    uuid.uppercase().contains("FFE0") ||
                    uuid.uppercase().contains("FFF0")
            }

        return nameMatches || (hasRgbService && name.isNotEmpty())
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        val rawData = input.rawData
        val name = rawData?.let { extractLocalName(it) } ?: "Unknown"

        // Try to extract color from manufacturer data if available
        var colorInfo: String? = null
        var brightness: Int? = null

        if (data.size >= 4) {
            // Many bulbs send [prefix][R][G][B] in mfg data
            // Or [prefix][brightness][R][G][B]
            // val hexData = data.joinToString("") { "%02X".format(it) } // Unused

            // Look for RGB pattern (3 consecutive bytes that could be color)
            // Simple heuristic: last 3-4 bytes often contain color info
            if (data.size >= 6) {
                val r = data[data.size - 3].toInt() and 0xFF
                val g = data[data.size - 2].toInt() and 0xFF
                val b = data[data.size - 1].toInt() and 0xFF

                // Only report if it looks like a valid color (not all zeros or all FF)
                if ((r + g + b) > 0 && (r + g + b) < 765) {
                    colorInfo = "RGB($r,$g,$b)"
                    // Estimate brightness from max component
                    brightness = maxOf(r, g, b) * 100 / 255
                }
            }
        }

        val status =
            buildString {
                if (colorInfo != null) append(colorInfo)
                if (brightness != null) {
                    if (isNotEmpty()) append(" ")
                    append("$brightness%")
                }
            }
                .takeIf { it.isNotEmpty() }

        val bulbType =
            when {
                name.lowercase().contains("cnligh") -> "Syska/Cnlight RGB Bulb"
                name.lowercase().contains("triones") -> "Triones RGB Bulb"
                name.lowercase().contains("magic") -> "Magic Hue Bulb"
                name.lowercase().contains("elk") -> "ELK-BLEDOM LED"
                else -> "BLE RGB Light"
            }

        return SensorData(
            beaconType = bulbType,
            sensorStatus = status,
            rawData = "LED: ${data.joinToString("") { "%02X".format(it) }}",
        )
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
