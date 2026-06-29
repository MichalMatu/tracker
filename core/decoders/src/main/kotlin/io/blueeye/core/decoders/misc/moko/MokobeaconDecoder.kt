package io.blueeye.core.decoders.misc.moko

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** Mokosmart Beacon (Generic) decoder. Theengs: Mokobeacon_json.h Service UUID: FF01 */
@Singleton
class MokobeaconDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        // Service UUID FF01
        return serviceUuids.any { it.lowercase().contains("ff01") }
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Mokobeacon (Error)")

        val serviceData =
            findServiceData(rawData, 0xFF01)
                ?: return SensorData(beaconType = "Mokobeacon (No Data)")

        try {
            // batt: index 0 hex -> byte 0 (Theengs says 0,2 so 1 byte)
            val batt = if (serviceData.isNotEmpty()) serviceData[0].toInt() and 0xFF else null

            var accX: Double? = null
            var accY: Double? = null
            var accZ: Double? = null

            // Theengs indices: 14(7), 18(9), 22(11)
            if (serviceData.size >= 13) {
                val xRaw =
                    (serviceData[7].toInt() and 0xFF shl 8) or (serviceData[8].toInt() and 0xFF)
                accX = (xRaw / 10000.0) * 9.80665
                val yRaw =
                    (serviceData[9].toInt() and 0xFF shl 8) or
                        (serviceData[10].toInt() and 0xFF)
                accY = (yRaw / 10000.0) * 9.80665
                val zRaw =
                    (serviceData[11].toInt() and 0xFF shl 8) or
                        (serviceData[12].toInt() and 0xFF)
                accZ = (zRaw / 10000.0) * 9.80665
            }

            return SensorData(
                batteryLevel = batt,
                accelerationX = accX,
                accelerationY = accY,
                accelerationZ = accZ,
                beaconType = "Mokosmart Beacon",
                rawData = "Moko: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Mokobeacon (Parse Error)")
        }
    }

    private fun findServiceData(
        rawData: ByteArray,
        uuid16: Int,
    ): ByteArray? {
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break

            val type = rawData[i + 1].toInt() and 0xFF
            if (type == 0x16) {
                if (i + 3 < rawData.size) {
                    val uLow = rawData[i + 2].toInt() and 0xFF
                    val uHigh = rawData[i + 3].toInt() and 0xFF
                    val foundUuid = (uHigh shl 8) or uLow
                    if (foundUuid == uuid16) {
                        val payloadLen = len - 3
                        if (payloadLen > 0) {
                            return rawData.copyOfRange(i + 4, i + 4 + payloadLen)
                        }
                    }
                }
            }
            i += 1 + len
        }
        return null
    }
}
