package io.blueeye.core.decoders.misc.health

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** Oral-B Bluetooth Toothbrush decoder. Theengs: OralB_json.h Manufacturer ID starts with DC00 */
@Singleton
class OralBDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Manufacturer ID 0x00DC (Oral-B)
        if (manufacturerId != 0x00DC || data == null) return false

        // Length >= 22 hex chars = 11 bytes, minus 2 for ID = 9 bytes
        return data.size >= 9
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 9) return SensorData(beaconType = "Oral-B (Error)")

        try {
            // State: Byte 5 (hex index 10-11 -> byte 5 -> Android byte 3)
            val stateByte = data[3].toInt() and 0xFF
            val state =
                when (stateByte) {
                    0x01 -> "Initialising"
                    0x02 -> "Idle"
                    0x03 -> "Running"
                    0x04 -> "Charging"
                    0x73 -> "Sleeping"
                    else -> "Unknown"
                }

            // Pressure: Byte 6 (hex 12)
            val pressure = data[4].toInt() and 0xFF

            // Duration: Byte 7 minutes + Byte 8 seconds
            val minutes = data[5].toInt() and 0xFF
            val seconds = data[6].toInt() and 0xFF
            val durationSec = minutes * 60 + seconds

            // Mode: Byte 9 (hex 18-19)
            val modeByte = if (data.size > 7) data[7].toInt() and 0xFF else 0
            val mode =
                when (modeByte) {
                    0x00 -> "Off"
                    0x01 -> "Daily Clean"
                    0x02 -> "Sensitive"
                    0x03 -> "Massage"
                    0x04 -> "Whitening"
                    0x05 -> "Deep Clean"
                    0x06 -> "Tongue Cleaning"
                    0x07 -> "Turbo"
                    else -> "Unknown"
                }

            val statusStr =
                buildString {
                    append("State: $state")
                    if (state == "Running") {
                        append(", Mode: $mode")
                        append(", Duration: ${durationSec}s")
                        if (pressure > 0) append(", Pressure: $pressure")
                    }
                }

            return SensorData(
                sensorStatus = statusStr,
                beaconType = "Oral-B Toothbrush",
                rawData = "OralB: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Oral-B (Parse Error)")
        }
    }
}
