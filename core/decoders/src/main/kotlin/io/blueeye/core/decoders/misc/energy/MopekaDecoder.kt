package io.blueeye.core.decoders.misc.energy

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/** Mopeka Pro Check / BottleCheck sensor decoder. Theengs: Mopeka_json.h Manufacturer ID: 0x0059 */
@Singleton
class MopekaDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        if (manufacturerId != 0x0059 || data == null) return false

        // Condition: first 3 bytes are 59 00 03, 59 00 06, or 59 00 0c
        // Since manufacturerId is 0x0059, data[0]=59, data[1]=00.
        // We check byte 2 (index 4 in hex if data includes ID).
        // Theengs Json says "index", 0, "590003".
        if (data.size < 6) return false

        val type = data[2].toInt() and 0xFF
        return type == 0x03 || type == 0x06 || type == 0x0C
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 12) return SensorData(beaconType = "Mopeka (Short)")

        try {
            // tempc: hex 8,2 -> byte 4
            val tRaw = data[4].toInt() and 0x7F
            val temp = (tRaw - 40).toDouble()

            // lvl_cm: hex 10,4 -> byte 5-6
            val lLow = data[5].toInt() and 0xFF
            val lHigh = data[6].toInt() and 0xFF
            val levelRaw = ((lHigh shl 8) or lLow) and 0x3FFF
            // Theengs post_proc: ["&", 16383, "*", ".cal", "/", 10]
            // .cal is just (byte 4 & 127) which is tRaw
            val levelCm = (levelRaw * tRaw) / 10.0

            // volt: hex 6,2 -> byte 3
            val vRaw = data[3].toInt() and 0x7F
            val voltage = vRaw / 32.0

            // batt: (voltage - 2.2) / 0.65 * 100
            val battery = max(0.0, min(100.0, (voltage - 2.2) / 0.65 * 100.0)).toInt()

            // sync: bit 3 of byte 4 (hex 8)
            val syncPressed = (data[4].toInt() and 0x08) != 0

            return SensorData(
                temperatureCelcius = temp,
                batteryLevel = battery,
                voltageV = voltage,
                sensorStatus = if (syncPressed) "Sync Pressed" else null,
                beaconType = "Mopeka Gas Level Sensor",
                rawData = "Lvl: %.1f cm, Qual: %d".format(levelCm, data[6].toInt() and 0xFF),
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Mopeka (Parse Error)")
        }
    }
}
