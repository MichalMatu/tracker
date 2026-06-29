package io.blueeye.core.decoders.misc.sensor

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Onset Hobo Water Level Sensor (MX2001) decoder. Theengs: HOBOMX2001_json.h Manufacturer ID:
 * 0x00C5
 */
@Singleton
class HoboDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Manufacturer ID 0x00C5 (LE in JSON: "c500")
        if (manufacturerId != 0x00C5 || data == null) return false

        // Data length 44 hex = 22 bytes
        return data.size == 22
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 22) return SensorData(beaconType = "Hobo MX2001 (Short)")

        try {
            // Level (cm): hex 36 -> byte 18. 4 bytes Float.
            // Theengs uses true, true, true for signed, big-endian, float?
            // Actually, standard IEEE 754 float
            val levelBytes = data.copyOfRange(18, 22)
            val buffer = ByteBuffer.wrap(levelBytes)
            // Theengs value_from_hex_data params: [..., is_signed, is_big_endian, is_float]
            // From JSON: 36, 8, true, true, true
            buffer.order(ByteOrder.BIG_ENDIAN)
            val levelM = buffer.float
            val levelCm = levelM * 100.0

            return SensorData(
                beaconType = "Hobo Water Level Sensor (MX2001)",
                rawData = "Level: %.2f cm".format(levelCm),
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Hobo MX2001 (Parse Error)")
        }
    }
}
