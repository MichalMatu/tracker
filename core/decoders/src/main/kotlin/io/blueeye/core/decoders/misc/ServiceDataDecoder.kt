package io.blueeye.core.decoders.misc

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generic Service Data Battery decoder. Theengs: ServiceData_json.h UUID: 0x180F (Battery Service)
 */
@Singleton
class ServiceDataDecoder
@Inject
constructor() : BleBeaconDecoder {
    override val priority: Int = -100

    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false
        return serviceUuids.any { it.contains("180f") }
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Battery (Error)")
        val serviceData =
            findServiceData(rawData, 0x180F)
                ?: return SensorData(beaconType = "Battery (No Data)")

        try {
            // batt: index 0 (hex 0,2)
            val battery = serviceData[0].toInt() and 0xFF
            return SensorData(
                batteryLevel = battery,
                beaconType = "Generic Battery Service",
                rawData = "Batt: $battery%",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Battery (Parse Error)")
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
