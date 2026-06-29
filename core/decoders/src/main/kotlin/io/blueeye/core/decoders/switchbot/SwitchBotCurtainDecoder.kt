package io.blueeye.core.decoders.switchbot

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** SwitchBot Curtain (W070160X) decoder. Theengs: SBCU_json.h */
@Singleton
class SwitchBotCurtainDecoder
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
        if (serviceData == null || serviceData.isEmpty()) return false

        val firstByte = serviceData[0].toInt() and 0xFF
        return firstByte == 0x63 || firstByte == 0x7B
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "SwitchBot Curtain (Error)")
        val serviceData =
            findServiceData(rawData, 0xFD3D)
                ?: findServiceData(rawData, 0x0D00)
                ?: return SensorData(beaconType = "SwitchBot Curtain (No Data)")

        try {
            // moving: byte 6 bit 3
            val b6 = serviceData[6].toInt() and 0xFF
            val moving = (b6 and 0x08) != 0

            // position: byte 6 bit 0-6
            val position = b6 and 0x7F

            // battery: byte 4 & 127
            val battery = serviceData[4].toInt() and 0x7F

            return SensorData(
                batteryLevel = battery,
                sensorStatus = if (moving) "Moving ($position%)" else "Stationary ($position%)",
                beaconType = "SwitchBot Curtain",
                rawData = "SBCU: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "SwitchBot Curtain (Parse Error)")
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
