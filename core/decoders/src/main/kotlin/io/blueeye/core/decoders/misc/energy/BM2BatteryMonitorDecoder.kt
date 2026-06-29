package io.blueeye.core.decoders.misc.energy

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BM2BatteryMonitorDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Apple ID 0x004C
        if (manufacturerId != 0x004C || data == null) return false

        // iBeacon with specific UUID 655f83ca-ae16-a10a-702e-31f30d58dd82
        if (data.size < 23) return false

        // iBeacon Prefix 02 15
        if (data[0] != 0x02.toByte() || data[1] != 0x15.toByte()) return false

        val expectedUuid =
            byteArrayOf(
                0x65.toByte(),
                0x5F.toByte(),
                0x83.toByte(),
                0xCA.toByte(),
                0xAE.toByte(),
                0x16.toByte(),
                0xA1.toByte(),
                0x0A.toByte(),
                0x70.toByte(),
                0x2E.toByte(),
                0x31.toByte(),
                0xF3.toByte(),
                0x0D.toByte(),
                0x58.toByte(),
                0xDD.toByte(),
                0x82.toByte(),
            )

        for (i in expectedUuid.indices) {
            if (data[2 + i] != expectedUuid[i]) return false
        }

        return true
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        // Batt: Hex 48 -> Byte 24. Android Index: 24 - 2 = 22.
        // Wait, 'data' (payload) starts at index 0 (which was index 2 in full packet).
        // Full packet byte 24 is index 24.
        // Android payload 'data' starts at Byte 2.
        // So Android Index = 24 - 2 = 22.

        // Length check: iBeacon payload is usually 23 bytes (02 15 + 16 UUID + 2 Maj + 2 Min + 1
        // TX).
        // 0+1+2 (UUID end at 17) + 2 + 2 + 1 = 23 bytes.
        // Index 22 is the last byte.

        try {
            if (data.size <= 22) return SensorData(beaconType = "BM2 (Short)")

            val batt = data[22].toInt() and 0xFF

            return SensorData(
                batteryLevel = batt,
                beaconType = "BM2 Battery Monitor",
                rawData = "BM2: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "BM2 (Parse Error)")
        }
    }
}
