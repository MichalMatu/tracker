package io.blueeye.core.decoders.misc

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** NodOn NIU Smart Button decoder. Theengs: NODONNIU_json.h */
@Singleton
class NodonDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val rawData = input.rawData
        if (rawData == null) return false

        // Service UUID 0000 (often used by Eddystone UID/URL or custom)
        // Correct check is finding Service Data with UUID 0000 and length 16 bytes.
        val serviceData = findServiceData(rawData, 0x0000) ?: return false

        return serviceData.size == 16
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "NodOn (Error)")

        val serviceData =
            findServiceData(rawData, 0x0000)
                ?: return SensorData(beaconType = "NodOn (No Data)")

        try {
            // button: index 30 hex -> byte 15
            val bVal = serviceData[15].toInt() and 0xFF
            val buttonType =
                when (bVal) {
                    0x01 -> "Single Press"
                    0x02 -> "Double Press"
                    0x03 -> "Long Press"
                    0x04 -> "Release"
                    0x05 -> "Single Press (Up)"
                    0x06 -> "Hold"
                    0x07 -> "Single Press (Down)"
                    else -> "Type $bVal"
                }

            // color: index 20 hex -> byte 10-11
            val c1 = serviceData[10].toInt() and 0xFF
            val c2 = serviceData[11].toInt() and 0xFF
            val colorHex = "%02X%02X".format(c1, c2)
            val color =
                when (colorHex) {
                    "0002" -> "White"
                    "0003" -> "TechBlue"
                    "0004" -> "CozyGrey"
                    "0005" -> "Wazabi"
                    "0006" -> "Lagoon"
                    "0007" -> "Softberry"
                    else -> colorHex
                }

            // batt: index 24 hex -> byte 12
            val battery = serviceData[12].toInt() and 0x7F

            return SensorData(
                batteryLevel = battery,
                sensorStatus = "$buttonType ($color)",
                beaconType = "NodOn NIU Smart Button",
                rawData = "NIU: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "NodOn (Parse Error)")
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
