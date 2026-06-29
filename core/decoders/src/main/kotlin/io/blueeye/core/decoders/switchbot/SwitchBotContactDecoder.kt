package io.blueeye.core.decoders.switchbot

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** SwitchBot Contact Sensor (W120150X) decoder. Theengs: SBCS_json.h */
@Singleton
class SwitchBotContactDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false
        val isServiceUuidCorrect =
            serviceUuids.any {
                it.lowercase().contains("fd3d") || it.lowercase().contains("0d00")
            }
        if (!isServiceUuidCorrect) return false

        val serviceData = findServiceData(rawData, 0xFD3D) ?: findServiceData(rawData, 0x0D00)
        return serviceData != null &&
            serviceData.size == 9 &&
            (serviceData[0].toInt() and 0xFF) == 0x64
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "SwitchBot Contact (Error)")
        val serviceData =
            findServiceData(rawData, 0xFD3D)
                ?: findServiceData(rawData, 0x0D00)
                ?: return SensorData(beaconType = "SwitchBot Contact (No Data)")

        try {
            // contact: byte 7 bit 1
            val b7 = serviceData[7].toInt() and 0xFF
            val doorClosed = (b7 and 0x02) != 0 // Theengs says index 7 bit 1: closed/open

            // motion: byte 2 bit 2
            val b2 = serviceData[2].toInt() and 0xFF
            val motion = (b2 and 0x04) != 0

            // light: byte 7 bit 0
            val bright = (b7 and 0x01) != 0

            // batt: byte 4-5 & 127
            val battery = serviceData[4].toInt() and 0x7F

            return SensorData(
                batteryLevel = battery,
                doorOpen = !doorClosed,
                movementDetected = motion,
                sensorStatus = if (bright) "Bright" else "Dark",
                beaconType = "SwitchBot Contact Sensor",
                rawData = "SBCS: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "SwitchBot Contact (Parse Error)")
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
