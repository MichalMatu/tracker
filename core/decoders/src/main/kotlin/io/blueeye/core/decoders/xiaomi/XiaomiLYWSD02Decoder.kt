package io.blueeye.core.decoders.xiaomi

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/** Xiaomi LYWSD02 e-ink Clock decoder. Theengs: LYWSD02_json.h Service UUID: FE95 */
@Singleton
class XiaomiLYWSD02Decoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        // Service UUID FE95
        if (!serviceUuids.any { it.lowercase().contains("fe95") }) return false

        val serviceData = findServiceData(rawData, 0xFE95) ?: return false

        // Product ID 0x045B (hex index 4,5,6,7: 5b04)
        if (serviceData.size < 4) return false

        return serviceData[2].toInt() == 0x5B && serviceData[3].toInt() == 0x04
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Xiaomi LYWSD02 (Error)")

        val serviceData =
            findServiceData(rawData, 0xFE95)
                ?: return SensorData(beaconType = "Xiaomi LYWSD02 (No Data)")

        try {
            var temp: Double? = null
            var hum: Double? = null
            var batt: Int? = null

            // Scan for MiBeacon objects
            // Starts after FrameControl(2), ProductID(2), Counter(1) = byte 5
            var offset = 5

            // Check for MAC (fc1 bit 4)
            val fc1 = serviceData[0].toInt() and 0xFF
            if ((fc1 and 0x10) != 0) offset += 6
            // Capability (fc1 bit 5)
            if ((fc1 and 0x20) != 0) offset += 1

            while (offset < serviceData.size - 2) {
                val objId =
                    (serviceData[offset + 1].toInt() and 0xFF shl 8) or
                        (serviceData[offset].toInt() and 0xFF)
                val objLen = serviceData[offset + 2].toInt() and 0xFF

                if (offset + 3 + objLen > serviceData.size) break

                val valOffset = offset + 3

                when (objId) {
                    0x1004 -> { // Temp (2 bytes, /10)
                        if (objLen >= 2) {
                            val tRaw =
                                (serviceData[valOffset + 1].toInt() and 0xFF shl 8) or
                                    (serviceData[valOffset].toInt() and 0xFF)
                            temp = tRaw.toShort() / 10.0
                        }
                    }
                    0x1006 -> { // Hum (2 bytes, /10)
                        if (objLen >= 2) {
                            val hRaw =
                                (serviceData[valOffset + 1].toInt() and 0xFF shl 8) or
                                    (serviceData[valOffset].toInt() and 0xFF)
                            hum = hRaw / 10.0 // Actually unsigned in JSON but /10
                        }
                    }
                    0x100A -> { // Batt (1 byte)
                        if (objLen >= 1) {
                            batt = serviceData[valOffset].toInt() and 0xFF
                        }
                    }
                }
                offset += 3 + objLen
            }

            return SensorData(
                temperatureCelcius = temp,
                humidityPercent = hum,
                batteryLevel = batt,
                beaconType = "Xiaomi LYWSD02 e-ink Clock",
                rawData = "LYWSD02: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Xiaomi LYWSD02 (Parse Error)")
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
