package io.blueeye.core.decoders.misc.energy

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Otodata Rotarex Monitor (RC1010) decoder. Theengs: OTOD_json.h Manufacturer ID: 0x03B1 */
@Singleton
class OtodataDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Manufacturer ID 0x03B1 (LE "b103")
        if (manufacturerId != 0x03B1 || data == null) return false

        // Length 42 hex (21 bytes) -> 19 bytes payload
        // Length 48 hex (24 bytes) -> 22 bytes payload
        return data.size == 19 || data.size == 22
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        try {
            var level: Double? = null
            var status: Int? = null
            var serial: Long? = null
            var modelType: Long? = null

            if (data.size == 19) {
                // level: hex 22,4 -> byte 11 in Theengs (incl ID). Payload byte 9.
                val l1 = data[9].toInt() and 0xFF
                val l2 = data[10].toInt() and 0xFF
                level = ((l1 shl 8) or l2).toDouble() / 100.0

                // status: hex 26,4 -> byte 13 in Theengs (incl ID). Payload byte 11.
                val s1 = data[11].toInt() and 0xFF
                val s2 = data[12].toInt() and 0xFF
                status = (s1 shl 8) or s2
            } else if (data.size == 22) {
                // serial: hex 18,8 -> byte 9-12 in Theengs (incl ID). Payload byte 7.
                val buffer = ByteBuffer.wrap(data)
                buffer.order(ByteOrder.BIG_ENDIAN)
                serial = abs(buffer.getInt(7).toLong())

                // modeltype: hex 40,8 -> byte 20-23 in Theengs (incl ID). Payload byte 18.
                modelType = abs(buffer.getInt(18).toLong())
            }

            val statusStr =
                listOfNotNull(
                    status?.let { "Status: $it" },
                    serial?.let { "Serial: $it" },
                    modelType?.let { "ModelType: $it" },
                )
                    .joinToString(", ")

            return SensorData(
                soilMoisturePercent =
                level, // Use soilMoisture as generic 'level' if applicable, or keep in
                // raw
                sensorStatus = statusStr.ifEmpty { null },
                beaconType = "Otodata Rotarex Monitor",
                rawData = "Level: %.2f%%, %s".format(level ?: 0.0, statusStr),
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Otodata (Parse Error)")
        }
    }
}
