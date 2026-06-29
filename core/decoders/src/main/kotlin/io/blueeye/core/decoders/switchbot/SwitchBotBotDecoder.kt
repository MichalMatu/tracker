package io.blueeye.core.decoders.switchbot

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** SwitchBot Bot (X1) decoder. Theengs: SBS1_json.h */
@Singleton
class SwitchBotBotDecoder
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
            serviceData.size >= 6 &&
            (serviceData[0].toInt() and 0xFF) == 0x48
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "SwitchBot Bot (Error)")
        val serviceData =
            findServiceData(rawData, 0xFD3D)
                ?: findServiceData(rawData, 0x0D00)
                ?: return SensorData(beaconType = "SwitchBot Bot (No Data)")

        try {
            // SwitchBot Bot: battery at hex 12 (byte 6 of record) -> payload[2]
            // mode/state at hex 10 (byte 5 of record) -> payload[1]
            // Assuming serviceData is payload starting at hex 8 (byte 4).
            val b1 = serviceData[1].toInt() and 0xFF
            val isOnOffMode = (b1 and 0x80) != 0 // bit 7? Theengs says index 1 bit 7
            val isOn = (b1 and 0x40) != 0 // bit 6

            val battery = serviceData[2].toInt() and 0x7F

            return SensorData(
                batteryLevel = battery,
                sensorStatus =
                "Mode: ${if (isOnOffMode) "On/Off" else "Press"}, State: ${if (isOn) "On" else "Off"}",
                beaconType = "SwitchBot Bot",
                rawData = "SBS1: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "SwitchBot Bot (Parse Error)")
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
