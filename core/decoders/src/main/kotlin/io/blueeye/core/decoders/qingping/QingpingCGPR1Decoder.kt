package io.blueeye.core.decoders.qingping

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Qingping Motion & Light (CGPR1) decoder. Theengs: CGPR1_json.h Service UUID: FDCD, index 2 (byte
 * 1) = 12
 */
@Singleton
class QingpingCGPR1Decoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        // Check for Service UUID FDCD
        if (!serviceUuids.any { it.lowercase().contains("fdcd") }) return false

        val serviceData = findServiceDataFDCD(rawData) ?: return false

        // Supported lengths: 14, 17, 20 bytes
        if (serviceData.size != 14 && serviceData.size != 17 && serviceData.size != 20) return false

        // Check index 2 (byte 1) for "12"
        val typeByte = serviceData[1].toInt() and 0xFF
        return typeByte == 0x12
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Qingping CGPR1 (Error)")

        val serviceData =
            findServiceDataFDCD(rawData)
                ?: return SensorData(beaconType = "Qingping CGPR1 (No Data)")

        try {
            var lux: Double? = null
            var motion: Boolean? = null
            var batt: Int? = null

            when (serviceData.size) {
                20 -> {
                    // lux: byte 16, 2 bytes
                    val lLow = serviceData[16].toInt() and 0xFF
                    val lHigh = serviceData[17].toInt() and 0xFF
                    lux = ((lHigh shl 8) or lLow).toDouble()

                    // batt: byte 10, 1 byte
                    batt = serviceData[10].toInt() and 0xFF
                }
                17 -> {
                    // lux: byte 11, 2 bytes
                    val lLow = serviceData[11].toInt() and 0xFF
                    val lHigh = serviceData[12].toInt() and 0xFF
                    lux = ((lHigh shl 8) or lLow).toDouble()

                    // motion: byte 10, bit 0
                    motion = (serviceData[10].toInt() and 0x01) != 0
                }
                14 -> {
                    // motion: byte 10, bit 0
                    motion = (serviceData[10].toInt() and 0x01) != 0
                }
            }

            return SensorData(
                illuminanceLux = lux,
                movementDetected = motion,
                batteryLevel = batt,
                beaconType = "Qingping Motion & Light (CGPR1)",
                rawData = "CGPR1: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Qingping CGPR1 (Parse Error)")
        }
    }

    private fun findServiceDataFDCD(rawData: ByteArray): ByteArray? {
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
                    // FDCD in LE: CD FD
                    if (uLow == 0xCD && uHigh == 0xFD) {
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
