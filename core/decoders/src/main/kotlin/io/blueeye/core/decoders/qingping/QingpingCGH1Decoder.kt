package io.blueeye.core.decoders.qingping

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Qingping Contact Sensor (CGH1) decoder. Theengs: CGH1_json.h Service UUID: FDCD, index 2 (byte 1)
 * = 04
 */
@Singleton
class QingpingCGH1Decoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        // Check for Service UUID FDCD
        if (!serviceUuids.any { it.lowercase().contains("fdcd") }) return false

        val serviceData = findServiceDataFDCD(rawData) ?: return false

        // Supported lengths: 14 bytes (28 hex) or 17 bytes (34 hex)
        if (serviceData.size != 14 && serviceData.size != 17) return false

        // Check index 2 (byte 1) for "04"
        val typeByte = serviceData[1].toInt() and 0xFF
        return typeByte == 0x04
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Qingping CGH1 (Error)")

        val serviceData =
            findServiceDataFDCD(rawData)
                ?: return SensorData(beaconType = "Qingping CGH1 (No Data)")

        try {
            val isOpen =
                when (serviceData.size) {
                    14 -> (serviceData[10].toInt() and 0x01) != 0
                    17 -> (serviceData[16].toInt() and 0x01) != 0
                    else -> null
                }

            return SensorData(
                doorOpen = isOpen,
                beaconType = "Qingping Contact Sensor (CGH1)",
                rawData = "CGH1: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Qingping CGH1 (Parse Error)")
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
