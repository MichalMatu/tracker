package io.blueeye.core.decoders.misc.energy

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BM6BatteryMonitorDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        if (manufacturerId != 0x004C || data == null) return false
        if (data.size < 23) return false
        if (data[0] != 0x02.toByte() || data[1] != 0x15.toByte()) return false

        // UUID: 3BA29CD9A42C894856BADAF2606EF777
        val expectedUuid =
            byteArrayOf(
                0x3B.toByte(),
                0xA2.toByte(),
                0x9C.toByte(),
                0xD9.toByte(),
                0xA4.toByte(),
                0x2C.toByte(),
                0x89.toByte(),
                0x48.toByte(),
                0x56.toByte(),
                0xBA.toByte(),
                0xDA.toByte(),
                0xF2.toByte(),
                0x60.toByte(),
                0x6E.toByte(),
                0xF7.toByte(),
                0x77.toByte(),
            )

        for (i in expectedUuid.indices) {
            if (data[2 + i] != expectedUuid[i]) return false
        }
        return true
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        // Batt: Hex 42 -> Byte 21. Android Index: 21 - 2 = 19.
        // Index 19 corresponds to last byte of Major.
        // Full Data: [ID][02][15][UUID...][MajH][MajL][MinH][MinL][TX]
        //             0   2   3   4...19    20    21    22    23    24
        // Android Data:  [02][15][UUID...][MajH][MajL][MinH][MinL][TX]
        //                 0   1   2...17    18    19    20    21    22
        // So Android Index 19 is indeed correct.

        try {
            if (data.size <= 19) return SensorData(beaconType = "BM6 (Short)")

            val batt = data[19].toInt() and 0xFF

            return SensorData(
                batteryLevel = batt,
                beaconType = "BM6 Battery Monitor",
                rawData = "BM6: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "BM6 (Parse Error)")
        }
    }
}
