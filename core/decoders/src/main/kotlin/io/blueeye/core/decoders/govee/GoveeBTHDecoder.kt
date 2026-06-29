package io.blueeye.core.decoders.govee

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Govee BTH format decoder (H5100/H5101/H5102/H5104/H5105/H5108/H5174/H5177). Theengs: H5102_json.h
 * Manufacturer ID: 0x0001
 */
@Singleton
class GoveeBTHDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Manufacturer ID 0x0001 (in JSON as "0100" which is LE)
        if (manufacturerId != 0x0001 || data == null) return false

        // Data length usually 7+ bytes
        return data.size >= 8
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 8) return SensorData(beaconType = "Govee BTH (Short)")

        try {
            // Temperature + Humidity combined in 3 bytes starting at byte 4 (offset 8 hex)
            val b1 = data[4].toInt() and 0xFF
            val b2 = data[5].toInt() and 0xFF
            val b3 = data[6].toInt() and 0xFF
            val rawValue = (b1 shl 16) or (b2 shl 8) or b3

            // Check sign bit (bit 23 of the 24-bit value)
            val isNegative = (rawValue and 0x800000) != 0
            val tempValue = rawValue and 0x7FFFFF

            val temp =
                if (isNegative) {
                    -(tempValue / 10000.0)
                } else {
                    rawValue / 10000.0
                }

            val humidity = (tempValue % 1000) / 10.0

            // Battery: Byte 7 (offset 14 hex)
            val batt = if (data.size > 7) data[7].toInt() and 0xFF else null

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = humidity,
                batteryLevel = batt,
                beaconType = "Govee Smart Thermo-Hygrometer",
                rawData = "BTH: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Govee BTH (Parse Error)")
        }
    }
}
