package io.blueeye.core.decoders.govee

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Govee Smart Air Quality Monitor (H5106) decoder. Theengs: H5106_json.h Manufacturer ID: 0x0001
 */
@Singleton
class GoveeH5106Decoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Manufacturer ID 0x0001
        if (manufacturerId != 0x0001 || data == null) return false

        // Data length usually 16+ bytes in full packet, but mfg data here should be at least 8
        return data.size >= 8
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 8) return SensorData(beaconType = "Govee H5106 (Short)")

        try {
            // Data for sensors starts at offset 8 hex = byte 4
            // We need 4 bytes for the 32-bit value
            val b1 = data[4].toInt() and 0xFF
            val b2 = data[5].toInt() and 0xFF
            val b3 = data[6].toInt() and 0xFF
            val b4 = data[7].toInt() and 0xFF

            // Theengs uses 8 hex chars (4 bytes)
            val fullValue =
                (b1.toLong() shl 24) or
                    (b2.toLong() shl 16) or
                    (b3.toLong() shl 8) or
                    b4.toLong()

            // tempc calculation from Theengs:
            // bit 31 is sign (bit 7 of b1)
            val isNegative = (b1 and 0x80) != 0
            val cleanValue = fullValue and 0x7FFFFFFFL

            val temp =
                if (isNegative) {
                    -(
                        cleanValue /
                            1000000.0 /
                            10.0
                        ) // This seems weird in Theengs post_proc, but following logic
                    // Actually: cleanValue / 1000000 = X.Y, then X.Y / 10 = Temp
                    (cleanValue / 1000000.0) / 10.0 * -1.0
                } else {
                    (fullValue / 1000000.0) / 10.0
                }

            // humidity: (fullValue & 0x7FFFFFFF) % 1000000 / 1000 / 10
            val humidity = ((cleanValue % 1000000) / 1000.0) / 10.0

            // pm25: (fullValue & 0x7FFFFFFF) - cal
            // cal: (fullValue & 0x7FFFFFFF) / 1000 * 1000 (which is floor to thousands)
            val cal = (cleanValue / 1000) * 1000
            val pm25 = (cleanValue - cal).toInt()

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = humidity,
                pm25Ugm3 = pm25,
                beaconType = "Govee Smart Air Quality Monitor (H5106)",
                rawData = "H5106: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Govee H5106 (Parse Error)")
        }
    }
}
