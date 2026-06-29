package io.blueeye.core.decoders.environmental

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sensirion SCD4X CO2 sensor decoder. Theengs: SCD4X_json.h Manufacturer ID 0x06D5 (Sensirion),
 * data starts with 0008 or 000a
 */
@Singleton
class SensirionSCD4XDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Sensirion manufacturer ID 0x06D5
        if (manufacturerId != 0x06D5 || data == null) return false

        // Data starts with 00 08 or 00 0a
        if (data.size < 10) return false
        return (data[0] == 0x00.toByte() && data[1] == 0x08.toByte()) ||
            (data[0] == 0x00.toByte() && data[1] == 0x0A.toByte())
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 10) return SensorData(beaconType = "SCD4X (Error)")

        try {
            // Temperature: Bytes 4-5 (hex 12-15), BE, signed
            // Formula: value * 175 / 65535 - 45
            val tHigh = data[4].toInt() and 0xFF
            val tLow = data[5].toInt() and 0xFF
            val tempRaw = (tHigh shl 8) or tLow
            val temp = tempRaw * 175.0 / 65535.0 - 45.0

            // Humidity: Bytes 6-7 (hex 16-19), LE, signed
            // Formula: value * 100 / 65535
            val hLow = data[6].toInt() and 0xFF
            val hHigh = data[7].toInt() and 0xFF
            val humRaw = (hHigh shl 8) or hLow
            val humidity = humRaw * 100.0 / 65535.0

            // CO2: Bytes 8-9 (hex 20-23), LE
            val co2Low = data[8].toInt() and 0xFF
            val co2High = data[9].toInt() and 0xFF
            val co2 = (co2High shl 8) or co2Low

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = humidity,
                co2Ppm = co2,
                beaconType = "Sensirion SCD4X",
                rawData = "SCD4X: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "SCD4X (Parse Error)")
        }
    }
}
