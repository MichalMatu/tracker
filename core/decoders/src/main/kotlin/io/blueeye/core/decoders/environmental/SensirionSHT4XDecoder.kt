package io.blueeye.core.decoders.environmental

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** Sensirion SHT4X TH sensor decoder. Theengs: SHT4X_json.h Manufacturer ID: 0x06D5 */
@Singleton
class SensirionSHT4XDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Manufacturer ID 0x06D5 (LE "d506")
        if (manufacturerId != 0x06D5 || data == null) return false

        // Condition: starts with 00 06 (after ID) and length at least 20 hex = 10 bytes
        // But Theengs hex 0,4 is "d5060006"? Let's check hex 0, 1, 2, 3: D5 06 00 06
        if (data.size < 6) return false

        return (data[2].toInt() and 0xFF) == 0x00 && (data[3].toInt() and 0xFF) == 0x06
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 10) return SensorData(beaconType = "Sensirion SHT4X (Short)")

        try {
            // tempc: hex 12,4 -> byte 6-7, BE, unsigned
            val tHigh = data[6].toInt() and 0xFF
            val tLow = data[7].toInt() and 0xFF
            val tRaw = (tHigh shl 8) or tLow
            val temp = (tRaw * 175.0 / 65535.0) - 45.0

            // hum: hex 16,4 -> byte 8-9, BE, unsigned
            val hHigh = data[8].toInt() and 0xFF
            val hLow = data[9].toInt() and 0xFF
            val hRaw = (hHigh shl 8) or hLow
            val hum = (hRaw * 125.0 / 65535.0) - 6.0

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = hum,
                beaconType = "Sensirion SHT4X Sensor",
                rawData = "SHT4X: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Sensirion SHT4X (Parse Error)")
        }
    }
}
