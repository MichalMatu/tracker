package io.blueeye.core.decoders.xiaomi

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Xiaomi Mi Band / Amazfit Smart Watch decoder. Theengs: Miband_json.h Manufacturer data starts
 * with 5701
 */
@Singleton
class MiBandDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Xiaomi manufacturer ID 0x0157
        if (manufacturerId != 0x0157 || data == null) return false

        // Length = 52 hex chars = 26 bytes, minus 2 for ID = 24 bytes
        return data.size >= 20
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 5) return SensorData(beaconType = "Mi Band (Error)")

        try {
            // Check for heart rate data: byte 0 should be 02 for heart rate type
            var heartRate: Int? = null
            if (data.size > 3 && data[0] == 0x02.toByte()) {
                val hrByte = data[3].toInt() and 0xFF
                if (hrByte != 0x0F && hrByte > 0) {
                    heartRate = hrByte
                }
            }

            val statusStr =
                if (heartRate != null) {
                    "Heart Rate: $heartRate bpm"
                } else {
                    "Active"
                }

            return SensorData(
                sensorStatus = statusStr,
                beaconType = "Xiaomi/Amazfit Tracker",
                rawData = "MiBand: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Mi Band (Parse Error)")
        }
    }
}
