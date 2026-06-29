package io.blueeye.core.decoders.switchbot

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** SwitchBot Meter Pro (CO2) (W490001X) decoder. Theengs: SBMP_json.h */
@Singleton
class SwitchBotMeterProDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Manufacturer ID 0x0969, length 36 bytes (after ID)
        if (manufacturerId != 0x0969 || data == null || data.size < 34) return false

        // Custom condition: index 0 is 0x35 in service data? Theengs says:
        // condition:["uuid", "index", 0, "fd3d", "&", "servicedata", "=", 6, "index", 0, "35", "&",
        // "manufacturerdata", "=", 36, "index", 0, "6909"]
        // Wait, manufacturerdata = 36 usually means 2 (ID) + 34 (Payload). We have 34 (Payload).
        // Correct.
        return true
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 34) return SensorData(beaconType = "SwitchBot Meter Pro (Short)")

        try {
            // .cal: byte 21 / 10.0
            val cal =
                (data[19].toInt() and 0xFF) /
                    10.0 // data[19] because data starts after ID (byte 2)

            // temp: byte 22-23 (index 22,2 in hex_data), signed?
            // Theengs: ["value_from_hex_data", "manufacturerdata", 22, 2, true, false]
            // and condition on bit 3 of byte 22
            val b22 = data[20].toInt() and 0xFF
            val b23 = data[21].toInt() and 0xFF
            var tRaw = ((b22 and 0x7F) shl 8) or b23

            val bit3 = (b22 and 0x08) != 0
            val temp =
                if (!bit3) {
                    // post_proc: ["+", ".cal", "*", -1]
                    (tRaw.toDouble() + cal) * -1.0
                } else {
                    // post_proc: ["+", ".cal", "-", 128]
                    tRaw.toDouble() + cal - 128.0
                }

            // hum: byte 24-25 & 127
            val hum = data[23].toInt() and 0x7F

            // co2: byte 30-33 (hex 30,4)
            val co2 =
                (data[28].toInt() and 0xFF shl 24) or
                    (data[29].toInt() and 0xFF shl 16) or
                    (data[30].toInt() and 0xFF shl 8) or
                    (data[31].toInt() and 0xFF)

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = hum.toDouble(),
                co2Ppm = co2,
                beaconType = "SwitchBot Meter Pro (CO2)",
                rawData = "CO2: $co2 ppm, Temp: %.1f C".format(co2, temp),
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "SwitchBot Meter Pro (Parse Error)")
        }
    }
}
