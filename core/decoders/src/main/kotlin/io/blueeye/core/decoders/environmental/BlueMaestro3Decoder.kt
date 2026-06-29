package io.blueeye.core.decoders.environmental

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlueMaestro3Decoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        if (manufacturerId != 0x0133 || data == null) return false
        if (data.size < 14) return false // Payload needs ~14 bytes

        // Version Byte at Index 0 (Full Index 2, Hex 4)
        val version = data[0].toInt() and 0xFF
        return version == 0x16 || version == 0x17
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        try {
            // Batt: Byte 1 (Full Hex 6 -> Full Byte 3 -> Payload Byte 1)
            val batt = data[1].toInt() and 0xFF

            // Temp: Byte 6-7 (Full Hex 16 -> Full Byte 8 -> Payload Byte 6)
            // BE
            val tHigh = data[6].toInt() and 0xFF
            val tLow = data[7].toInt() and 0xFF
            val temp = ((tHigh shl 8) or tLow) / 10.0

            // Hum: Byte 8-9 (Full Hex 20 -> Full Byte 10 -> Payload Byte 8)
            // LE
            val hLow = data[8].toInt() and 0xFF
            val hHigh = data[9].toInt() and 0xFF
            val hum = ((hHigh shl 8) or hLow) / 10.0

            // DewPoint: Byte 10-11. BE
            val dHigh = data[10].toInt() and 0xFF
            val dLow = data[11].toInt() and 0xFF
            val dew = ((dHigh shl 8) or dLow) / 10.0

            val statusStr = "Dew Point: %.1f°C".format(dew)

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = hum,
                batteryLevel = batt,
                sensorStatus = statusStr,
                beaconType = "Blue Maestro Tempo 3in1",
                rawData = "BM3: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Blue Maestro 3 (Parse Error)")
        }
    }
}
