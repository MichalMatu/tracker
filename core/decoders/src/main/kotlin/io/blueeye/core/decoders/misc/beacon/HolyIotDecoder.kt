package io.blueeye.core.decoders.misc.beacon

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HolyIoT Beacon Tracker decoder. Theengs: HOLYIOT_json.h Service UUID: 0x5242 Manufacturer ID:
 * 0x004C (Apple iBeacon format)
 */
@Singleton
class HolyIotDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        // Check for Service UUID 0x5242
        if (!serviceUuids.any { it.lowercase().contains("5242") }) return false

        val serviceData = findServiceData(rawData, 0x5242) ?: return false

        // Service data length 26 hex = 13 bytes, starts with 0x41
        return serviceData.size == 13 && (serviceData[0].toInt() and 0xFF) == 0x41
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "HolyIoT (Error)")

        val serviceData =
            findServiceData(rawData, 0x5242)
                ?: return SensorData(beaconType = "HolyIoT (No Data)")

        try {
            // batt: servicedata byte 1 (index 2 hex)
            val batt = serviceData[1].toInt() and 0xFF

            return SensorData(
                batteryLevel = batt,
                beaconType = "HolyIoT Beacon Tracker",
                rawData = "HolyIoT: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "HolyIoT (Parse Error)")
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
