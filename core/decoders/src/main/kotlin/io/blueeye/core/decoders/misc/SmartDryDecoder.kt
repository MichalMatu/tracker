package io.blueeye.core.decoders.misc

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** SmartDry Laundry Sensor decoder. Theengs: SmartDry_json.h */
@Singleton
class SmartDryDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Manufacturer ID 0x01AE (LE "ae01")
        return manufacturerId == 0x01AE && data != null && data.size == 26
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        try {
            // tempc: hex 4,8 -> byte 2-5 (4 bytes floating?)
            // Theengs "true, false, true" in decoder means maybe float?
            // "value_from_hex_data","manufacturerdata",4,8,true,false,true
            // 8 hex = 4 bytes.
            val temp = extractFloat(data, 2)
            val hum = extractFloat(data, 6)

            // shake: hex 20,4 -> byte 10-11
            val sHigh = data[10].toInt() and 0xFF
            val sLow = data[11].toInt() and 0xFF
            val shake = (sHigh shl 8) or sLow

            // volt: hex 24,2 (byte 12) + 2847 / 1000
            val vByte = data[12].toInt() and 0xFF
            val voltage = (vByte + 2847.0) / 1000.0

            return SensorData(
                temperatureCelcius = temp.toDouble(),
                humidityPercent = hum.toDouble(),
                voltageV = voltage,
                sensorStatus = "Shake: $shake",
                beaconType = "SmartDry Laundry Sensor",
                rawData = "Temp: %.1f C, Hum: %.1f%%, Shake: %d".format(temp, hum, shake),
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "SmartDry (Parse Error)")
        }
    }

    private fun extractFloat(
        data: ByteArray,
        start: Int,
    ): Float {
        val bits =
            (data[start].toInt() and 0xFF shl 24) or
                (data[start + 1].toInt() and 0xFF shl 16) or
                (data[start + 2].toInt() and 0xFF shl 8) or
                (data[start + 3].toInt() and 0xFF)
        return Float.fromBits(bits)
    }
}
