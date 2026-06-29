package io.blueeye.core.decoders.misc

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** Google/Apple Exposure Notification (GAEN) decoder. Theengs: GAEN_json.h Service UUID: FD6F */
@Singleton
class GAENDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        // Service UUID FD6F
        return serviceUuids.any { it.lowercase().contains("fd6f") }
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "GAEN (Error)")

        val serviceData =
            findServiceDataFD6F(rawData)
                ?: return SensorData(beaconType = "Exposure Notification (GAEN)")

        // Typically 20 bytes (16 RPI + 4 AEM)
        val rpi = serviceData.take(16).joinToString("") { "%02X".format(it) }
        val aem =
            if (serviceData.size >= 20) {
                serviceData.copyOfRange(16, 20).joinToString("") { "%02X".format(it) }
            } else {
                ""
            }

        return SensorData(
            beaconType = "Exposure Notification (GAEN)",
            rawData = "RPI: $rpi, AEM: $aem",
        )
    }

    private fun findServiceDataFD6F(rawData: ByteArray): ByteArray? {
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
                    // FD6F in LE: 6F FD
                    if (uLow == 0x6F && uHigh == 0xFD) {
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
