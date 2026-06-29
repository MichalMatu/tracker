package io.blueeye.core.decoders.misc.health

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** Polar H10 and XOSS X2 Heart Rate Sensor decoders. Theengs: PH10_json.h, XOSSX2_json.h */
@Singleton
class HeartRateDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        if (data == null) return false

        // Polar H10: manufacturerId 0x006B (LE "6b00"), length 12
        if (manufacturerId == 0x006B && data.size == 10) return true

        // XOSS X2: manufacturerId 0xFF04 (LE "04ff"), length 12
        if (manufacturerId == 0xFF04 && data.size == 10) return true

        return false
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val manufacturerId = input.manufacturerId
        val data = input.data ?: byteArrayOf()
        try {
// Hex index 10 (Byte 5) in Full MD.
// Android 'data' is payload (Byte 2 of Full MD is index 0).
// So Byte 5 of Full MD is index 3 in 'data'.
            val bpm = data[3].toInt() and 0xFF

            var battery: Int? = null
            var brand = "Heart Rate Sensor"

            if (manufacturerId == 0x006B) {
                brand = "Polar H10"
            } else if (manufacturerId == 0xFF04) {
                brand = "XOSS X2"
// XOSS Battery: manufacturerdata 6, 2.
// Hex 6 (Byte 3) in Full MD.
// Byte 3 in Full MD is index 1 in 'data'.
                battery = data[1].toInt() and 0x7F
            }

            return SensorData(
                batteryLevel = battery,
                sensorStatus = "Pulse: $bpm bpm",
                beaconType = brand,
                rawData = "HR: $bpm bpm",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Heart Rate (Parse Error)")
        }
    }

    private fun extractInt(
        data: ByteArray,
        start: Int,
        len: Int,
    ): Int {
        var res = 0
        for (i in 0 until len) {
            if (start + i < data.size) {
                res = (res shl 8) or (data[start + i].toInt() and 0xFF)
            }
        }
        return res
    }
}
