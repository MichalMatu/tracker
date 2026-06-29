package io.blueeye.core.decoders.misc.sensor

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** Xiaomi RoPot (HHCCPOT002) decoder. Theengs: HHCCPOT002_json.h Service UUID: FE95 */
@Singleton
class HHCCRoPotDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        // Service UUID FE95
        if (!serviceUuids.any { it.lowercase().contains("fe95") }) return false

        val serviceData = findServiceData(rawData, 0xFE95) ?: return false

        // Condition: hex index 2 (byte 1) = 20 (already used in findServiceData as part of
        // payload?)
        // Let's check hex index 2,3,4,5,6,7: "205d01"
        // Actually the hex starts after Length, Type, UUID.
        // Byte 1: 0x20, Byte 2: 0x5D, Byte 3: 0x01
        if (serviceData.size < 4) return false

        return serviceData[1].toInt() == 0x20 &&
            serviceData[2].toInt() == 0x5D &&
            serviceData[3].toInt() == 0x01
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "HHCC RoPot (Error)")

        val serviceData =
            findServiceData(rawData, 0xFE95)
                ?: return SensorData(beaconType = "HHCC RoPot (No Data)")

        try {
            var moisture: Double? = null
            var fertility: Int? = null

            // Byte 12 is data type (hex index 25 is char 25,26? No, byte 12?
            // "servicedata", 25, "8" -> char 25 is in byte 12 (chars 0-1 byte 0, 2-3 byte 1...)
            // 24, 25 is byte 12.
            if (serviceData.size > 15) {
                val dataType = serviceData[12].toInt() and 0xFF
                // hex 30 char -> byte 15
                if (dataType == 0x08) {
                    moisture = (serviceData[15].toInt() and 0xFF).toDouble()
                } else if (dataType == 0x09) {
                    val fLow = serviceData[15].toInt() and 0xFF
                    val fHigh = serviceData[16].toInt() and 0xFF
                    fertility = (fHigh shl 8) or fLow
                }
            }

            return SensorData(
                soilMoisturePercent = moisture,
                fertilityUsCm = fertility,
                beaconType = "Xiaomi RoPot (HHCCPOT002)",
                rawData = "RoPot: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Xiaomi RoPot (Parse Error)")
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
