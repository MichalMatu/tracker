
package io.blueeye.core.decoders.environmental

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ThermoBeacon (WS02/WS08) decoder.
 * Theengs: ThermoBeacon_json.h
 * Manufacturer data starts with 1000, 1100, 1500, 1800, or 1b00
 */
@Singleton
class ThermoBeaconDecoder
@Inject
constructor() : BleBeaconDecoder {
    private val validPrefixes = listOf(0x0010, 0x0011, 0x0015, 0x0018, 0x001B)

    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        if (manufacturerId == null || data == null) return false

        // Check if manufacturer ID matches any valid prefix (in LE format)
        if (!validPrefixes.contains(manufacturerId)) return false

        // Length check: >= 40 hex chars = 20 bytes, minus 2 for ID = 18 bytes
        return data.size >= 18
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 18) return SensorData(beaconType = "ThermoBeacon (Error)")

        try {
            // Data layout for 40 hex char (20 byte) format:
            // ID is 2 bytes, so Android data starts at byte 2
            // Theengs indices are hex chars on full data
            // volt: Hex 20 -> Byte 10 -> Android Byte 8
            // tempc: Hex 24 -> Byte 12 -> Android Byte 10
            // hum: Hex 28 -> Byte 14 -> Android Byte 12

            // Voltage: Bytes 8-9, LE, Signed, /1000
            val vLow = data[8].toInt() and 0xFF
            val vHigh = data[9].toInt() and 0xFF
            val voltRaw = ((vHigh shl 8) or vLow).toShort()
            val voltage = voltRaw / 1000.0

            // Temperature: Bytes 10-11, LE, Signed, /16
            val tLow = data[10].toInt() and 0xFF
            val tHigh = data[11].toInt() and 0xFF
            val tempRaw = ((tHigh shl 8) or tLow).toShort()
            val temp = tempRaw / 16.0

            // Humidity: Bytes 12-13, LE, Signed, /16
            val hLow = data[12].toInt() and 0xFF
            val hHigh = data[13].toInt() and 0xFF
            val humRaw = ((hHigh shl 8) or hLow).toShort()
            val humidity = humRaw / 16.0

            // Battery estimation from voltage (2.2V = 0%, 3.0V = 100%)
            val batteryPercent = ((voltage - 2.2) / 0.8 * 100).coerceIn(0.0, 100.0).toInt()

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = humidity.toDouble(),
                voltageV = voltage.toDouble(),
                batteryLevel = batteryPercent,
                beaconType = "ThermoBeacon",
                rawData = "Thermo: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "ThermoBeacon (Parse Error)")
        }
    }
}
