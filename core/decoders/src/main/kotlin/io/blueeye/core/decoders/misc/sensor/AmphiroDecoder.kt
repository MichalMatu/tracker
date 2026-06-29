package io.blueeye.core.decoders.misc.sensor

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AmphiroDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        if (manufacturerId != 0xFAEE || data == null) return false
        // Theengs: index 0 "eefa" -> 0xFAEE LE used by Oras/Amphiro
        // Length check: Theengs says 42 chars (21 bytes). ID is 2 bytes.
        // So payload data size should be 19 bytes.

        return data.size >= 19
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        // Data Start at 0 relative to 'data' (which is after ID).
        // Theengs indices (Hex):
        // Session: Hex 4 -> Byte 2. Android Index: 2-2 = 0.
        // Seconds: Hex 10 -> Byte 5. Android Index: 5-2 = 3.
        // Litres: Hex 20 -> Byte 10. Android Index: 10-2 = 8.
        // Temp: Hex 26 -> Byte 13. Android Index: 13-2 = 11.
        // Energy: Hex 28 -> Byte 14. Android Index: 14-2 = 12.

        try {
            // Seconds (Index 3, 2 bytes LE)
            val secLow = data[3].toInt() and 0xFF
            val secHigh = data[4].toInt() and 0xFF
            val seconds = (secHigh shl 8) or secLow

            // Litres (Index 8, 3 bytes LE)
            val lit1 = data[8].toLong() and 0xFF
            val lit2 = data[9].toLong() and 0xFF
            val lit3 = data[10].toLong() and 0xFF
            val litresRaw = (lit3 shl 16) or (lit2 shl 8) or lit1
            val litres = litresRaw.toDouble() / 2560.0

            // Temp (Index 11, 1 byte)
            val temp = (data[11].toInt() and 0xFF).toDouble()

            // Energy (Index 12, 2 bytes LE)
            val enLow = data[12].toInt() and 0xFF
            val enHigh = data[13].toInt() and 0xFF
            val energy = ((enHigh shl 8) or enLow).toDouble() / 100.0

            val statusStr =
                "Water: %.1fL, Energy: %.2fkWh, Dur: %ds".format(litres, energy, seconds)

            return SensorData(
                temperatureCelcius = temp,
                sensorStatus = statusStr,
                beaconType = "Oras/Amphiro Hydractiva",
                rawData = "Amphiro: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Amphiro (Parse Error)")
        }
    }
}
