package io.blueeye.core.decoders.apple

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppleWatchDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Apple ID 0x004C
        if (manufacturerId != 0x004C || data == null) return false

        // Condition: manufacturerdata = 18 bytes (Theengs count includes ID)
        // Android data size = 18 - 2 = 16 bytes.
        // Check size roughly.
        if (data.size < 9) return false // We need index 8 (Theengs index 10)

        // Prefix: 4C00 10 05
        // Android data starts at 10.
        // data[0] == 0x10 (Type Nearby)
        // data[1] == 0x05 (Length/Flags)
        if (data[0] != 0x10.toByte() || data[1] != 0x05.toByte()) return false

        // Check index 10 (Android index 8) for 0x98 or 0x18
        // Indices:
        // MfgData: [4C][00][10][05][xx][xx][xx][xx][xx][xx][Status]
        //           0   1   2   3   4  5  6  7  8  9  10
        // Android:      [10][05][xx][xx][xx][xx][xx][xx][Status]
        //                0   1   2  3  4  5  6  7  8

        if (data.size <= 8) return false

        val statusByte = data[8].toInt() and 0xFF
        return statusByte == 0x98 || statusByte == 0x18
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        // We know supports() passed, so data[8] is interesting.
        val statusByte = data[8].toInt() and 0xFF

        // 0x98 = Unlocked
        // 0x18 = Locked ( _unlocked = false )

        val lockStatus =
            when (statusByte) {
                0x98 -> "Unlocked"
                0x18 -> "Locked"
                else -> "Unknown"
            }

        return SensorData(
            beaconType = "Apple Watch",
            sensorStatus = lockStatus,
            rawData = "Apple Watch: ${data.joinToString("") { "%02X".format(it) }}",
        )
    }
}
