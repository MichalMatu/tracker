package io.blueeye.core.decoders.misc.health

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ABTempDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        if (manufacturerId != 0x004C || data == null || data.size < 23) return false

        // Check for iBeacon type 0x0215
        if (data[0] != 0x02.toByte() || data[1] != 0x15.toByte()) return false

        // Check Specific UUID: B5 B1 82 C7 EA B1 49 88 AA 99 B5 C1 51 70 08 D9
        // Data index 2 to 17
        val expectedUuid =
            byteArrayOf(
                0xB5.toByte(),
                0xB1.toByte(),
                0x82.toByte(),
                0xC7.toByte(),
                0xEA.toByte(),
                0xB1.toByte(),
                0x49.toByte(),
                0x88.toByte(),
                0xAA.toByte(),
                0x99.toByte(),
                0xB5.toByte(),
                0xC1.toByte(),
                0x51.toByte(),
                0x70.toByte(),
                0x08.toByte(),
                0xD9.toByte(),
            )

        for (i in expectedUuid.indices) {
            if (data[2 + i] != expectedUuid[i]) return false
        }

        return true
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        // Data layout (offset by -2 bytes relative to MfgData in JSON because manufacturerId is
        // stripped)
        // JSON Hex Indices:
        // Batt: 44 -> Byte 22 -> Index 20 in 'data'
        // Temp: 46 -> Byte 23 -> Index 21 in 'data'

        try {
            if (data.size <= 21) return SensorData(beaconType = "ABTemp (Short)")

            // Major (Index 18-19) - Optional info

            // Battery (Index 20)
            val batt = data[20].toInt() and 0xFF

            // Temp (Index 21)
            // JSON says Unsigned.
            val tempVal = data[21].toInt() and 0xFF

            return SensorData(
                temperatureCelcius = tempVal.toDouble(),
                batteryLevel = batt,
                beaconType = "April Brother Temp (ABTemp)",
                rawData = "ABTemp: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "ABTemp (Parse Error)")
        }
    }
}
