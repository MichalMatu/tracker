package io.blueeye.core.decoders.switchbot

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * SwitchBot Blind Tilt (W270160X) decoder. Theengs: SBBT_json.h Condition involves Service Data
 * 0x78 and Manufacturer Data 0x0969
 */
@Singleton
class SwitchBotBlindTiltDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val serviceUuids = input.serviceUuids
        val data = input.data
        val rawData = input.rawData
        if (rawData == null) return false

        // Manufacturer ID 0x0969 (LE "6909")
        if (manufacturerId != 0x0969 || data == null) return false

        // Service UUID 0xFD3D or 0x0D00
        val isServiceUuidCorrect =
            serviceUuids.any {
                it.lowercase().contains("fd3d") || it.lowercase().contains("0d00")
            }
        if (!isServiceUuidCorrect) return false

        // Service Data starts with 0x78 and length 6
        val serviceData = findServiceData(rawData, 0xFD3D) ?: findServiceData(rawData, 0x0D00)
        if (serviceData == null ||
            serviceData.isEmpty() ||
            (serviceData[0].toInt() and 0xFF) != 0x78
        ) {
            return false
        }

        return true
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "SwitchBot Blind Tilt (Error)")

        val serviceData = findServiceData(rawData, 0xFD3D) ?: findServiceData(rawData, 0x0D00)

        try {
            // open: manufacturerdata hex 20,2 -> byte 10
            // post_proc: ["&", 127, "-", 50, "*", 2, "±", 100, "abs"]
            val openByte = data[10].toInt() and 0x7F
            val openPercent = abs((openByte - 50) * 2 - 100)

            // motion: bit 3 of byte 10
            val motion = (data[10].toInt() and 0x08) != 0

            // lightlevel: byte 9
            val lightLevel = data[9].toInt() and 0xFF

            // batt: servicedata byte 4 (hex 8,2) & 127
            val battery =
                if (serviceData != null && serviceData.size > 4) {
                    serviceData[4].toInt() and 0x7F
                } else {
                    null
                }

            return SensorData(
                batteryLevel = battery,
                movementDetected = motion,
                illuminanceLux = lightLevel.toDouble(), // Using as generic intensity
                sensorStatus = "Open: $openPercent%",
                beaconType = "SwitchBot Blind Tilt",
                rawData = "SBBT: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "SwitchBot Blind Tilt (Parse Error)")
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
