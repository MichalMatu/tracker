package io.blueeye.core.decoders.xiaomi

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Xiaomi Scales decoder (Smart Scale 1/2, Body Composition Scale). Theengs: XMTZC04HMKG_json.h,
 * XMTZC05HMKG_json.h
 */
@Singleton
class XiaomiScaleDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val rawData = input.rawData
        if (rawData == null) return false

        // Scale 1: Service UUID 181D, size 20 (hex chars or bytes? Theengs says 20 hex = 10 bytes?)
        // Theengs: servicedata = 20 & uuid contain 181d
        val sd181D = findServiceData(rawData, 0x181D)
        if (sd181D != null && sd181D.size >= 10) return true

        // Scale 2/BC: Service UUID 181B, size 26 (hex chars? 13 bytes?)
        // Theengs: servicedata = 26 & uuid contain 181b
        val sd181B = findServiceData(rawData, 0x181B)
        if (sd181B != null && sd181B.size >= 13) return true

        return false
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Xiaomi Scale (Error)")

        val sd181B = findServiceData(rawData, 0x181B)
        val sd181D = findServiceData(rawData, 0x181D)

        try {
            if (sd181B != null && sd181B.size >= 13) {
                // Body Composition Scale
                // weight: hex 22,4 (after ID) -> bytes 11, 12?
                // Wait, servicedata for 181B Theengs says hex 22,4. 22 hex = 11 bytes.
                val wHigh = sd181B[11].toInt() and 0xFF
                val wLow = sd181B[12].toInt() and 0xFF
                val weight = ((wHigh shl 8) or wLow) / 200.0

                // impedance: hex 18,4 -> byte 9, 10
                val iHigh = sd181B[9].toInt() and 0xFF
                val iLow = sd181B[10].toInt() and 0xFF
                val impedance = (iHigh shl 8) or iLow

                val modeBit = (sd181B[1].toInt() and 0x04) != 0

                return SensorData(
                    sensorStatus =
                    "Weight: %.2f kg, Impedance: %d Ω (%s)".format(
                        weight,
                        impedance,
                        if (modeBit) "Object" else "Person",
                    ),
                    beaconType = "Xiaomi Body Composition Scale",
                    rawData = "Weight: %.2f kg".format(weight),
                )
            } else if (sd181D != null && sd181D.size >= 10) {
                // Smart Scale
                // weight: hex 2,4 -> byte 1, 2
                val wHigh = sd181D[1].toInt() and 0xFF
                val wLow = sd181D[2].toInt() and 0xFF
                val weight = ((wHigh shl 8) or wLow) / 200.0

                val modeBit = (sd181D[0].toInt() and 0x04) != 0

                return SensorData(
                    sensorStatus =
                    "Weight: %.2f kg (%s)".format(
                        weight,
                        if (modeBit) "Object" else "Person",
                    ),
                    beaconType = "Xiaomi Smart Scale",
                    rawData = "Weight: %.2f kg".format(weight),
                )
            }
            return SensorData(beaconType = "Xiaomi Scale (Partial Data)")
        } catch (e: Exception) {
            return SensorData(beaconType = "Xiaomi Scale (Parse Error)")
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
            if (type == 0x16 && i + 3 < rawData.size) {
                val uLow = rawData[i + 2].toInt() and 0xFF
                val uHigh = rawData[i + 3].toInt() and 0xFF
                if (((uHigh shl 8) or uLow) == uuid16) {
                    val payloadLen = len - 3
                    if (payloadLen > 0) return rawData.copyOfRange(i + 4, i + 4 + payloadLen)
                }
            }
            i += 1 + len
        }
        return null
    }
}
