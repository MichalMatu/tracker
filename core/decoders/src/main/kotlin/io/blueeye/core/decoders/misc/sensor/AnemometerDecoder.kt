package io.blueeye.core.decoders.misc.sensor

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** UNI-T UT363 BT Anemometer decoder. Theengs: UT363BT_json.h */
@Singleton
class AnemometerDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val data = input.data
        // manufacturerdata length 38, index 22-27 is "4d2f53" ("m/s" in hex), index 0-1 is "aabb"
        if (data == null || data.size < 26) return false

        // After ID (2 bytes):
        // hex 0,4 = ID. hex 4 = index 0 of payload.
        // Wait, Theengs: index 0 is "aabb". If ID is AABB, then ID is 0xBBAA?
        // Uni-T usually uses custom ID or no ID. Theengs says "manufacturerdata", "=", 38.
        // If it's 38 bytes, and condition is index 0 "aabb", then byte 0=AA, 1=BB.

        val d0 = data[0].toInt() and 0xFF
        val d1 = data[1].toInt() and 0xFF
        if (d0 != 0xAA || d1 != 0xBB) return false

        // hex 22,6 hex chars = byte 11, length 3.
        // data[0] is byte 2 (hex 4). data[9] is byte 11 (hex 22).
        if (data.size < 12) return false
        val d9 = data[9].toInt() and 0xFF
        val d10 = data[10].toInt() and 0xFF
        val d11 = data[11].toInt() and 0xFF
        // "4d2f53" -> 4D 2F 53
        return d9 == 0x4D && d10 == 0x2F && d11 == 0x53
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        try {
            // windspeed: ascii from hex index 10,12 hex chars
            // hex 10 = byte 5. data[0] is byte 2. data[3] is byte 5.
            // 12 hex chars = 6 bytes.
            val speedBytes = data.copyOfRange(3, 9)
            val speedStr = String(speedBytes, Charsets.US_ASCII).trim()

            return SensorData(
                sensorStatus = "Wind: $speedStr m/s",
                beaconType = "UT363 BT Anemometer",
                rawData = "Speed: $speedStr m/s",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Anemometer (Parse Error)")
        }
    }
}
