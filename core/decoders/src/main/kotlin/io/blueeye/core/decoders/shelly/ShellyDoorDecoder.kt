package io.blueeye.core.decoders.shelly

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shelly BLU Door/Window (SBDW-002C) decoder. Theengs: SBDW_002C_json.h BTHome format (FCD2), name
 * starts with "SBDW-"
 */
@Singleton
class ShellyDoorDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        if (!serviceUuids.any { it.lowercase().contains("fcd2") }) return false

        val localName = extractLocalName(rawData)
        if (localName?.startsWith("SBDW-") != true) return false

        val serviceData = findServiceDataFCD2(rawData) ?: return false

        if (serviceData.isEmpty()) return false
        val firstByte = serviceData[0].toInt() and 0xFF
        return firstByte == 0x44 || firstByte == 0x45
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Shelly Door (Error)")

        val serviceData =
            findServiceDataFCD2(rawData)
                ?: return SensorData(beaconType = "Shelly Door (No Data)")

        if (serviceData.isNotEmpty() && (serviceData[0].toInt() and 0xFF) == 0x45) {
            return SensorData(
                beaconType = "Shelly BLU Door/Window (Encrypted)",
                sensorStatus = "Encrypted Data",
                rawData = "SBDW Encr: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        }

        try {
            var battery: Int? = null
            var lux: Double? = null
            var isOpen: Boolean? = null

            var i = 1
            while (i < serviceData.size - 1) {
                val typeId = serviceData[i].toInt() and 0xFF
                i++

                when (typeId) {
                    0x00 -> i += 1 // Packet ID
                    0x01 -> { // Battery
                        if (i < serviceData.size) {
                            battery = serviceData[i].toInt() and 0xFF
                            i += 1
                        }
                    }
                    0x05 -> { // Illuminance (3 bytes, LE, /100)
                        if (i + 2 < serviceData.size) {
                            val l1 = serviceData[i].toLong() and 0xFF
                            val l2 = serviceData[i + 1].toLong() and 0xFF
                            val l3 = serviceData[i + 2].toLong() and 0xFF
                            val luxRaw = (l3 shl 16) or (l2 shl 8) or l1
                            lux = luxRaw / 100.0
                            i += 3
                        }
                    }
                    0x2D -> { // Open (1 byte, bit 0)
                        if (i < serviceData.size) {
                            isOpen = (serviceData[i].toInt() and 0x01) == 1
                            i += 1
                        }
                    }
                    0x3F -> i += 2 // Rotation
                    else -> i += 1
                }
            }

            val statusStr =
                when {
                    isOpen == true -> "Open"
                    isOpen == false -> "Closed"
                    else -> null
                }

            return SensorData(
                doorOpen = isOpen,
                illuminanceLux = lux,
                batteryLevel = battery,
                sensorStatus = statusStr,
                beaconType = "Shelly BLU Door/Window",
                rawData = "SBDW: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Shelly Door (Parse Error)")
        }
    }

    private fun extractLocalName(rawData: ByteArray): String? {
        var i = 0
        while (i < rawData.size - 1) {
            val len = rawData[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > rawData.size) break

            val type = rawData[i + 1].toInt() and 0xFF
            if (type == 0x08 || type == 0x09) {
                val nameBytes = rawData.copyOfRange(i + 2, i + 1 + len)
                return String(nameBytes, Charsets.UTF_8)
            }
            i += 1 + len
        }
        return null
    }

    private fun findServiceDataFCD2(rawData: ByteArray): ByteArray? {
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
                    if (uLow == 0xD2 && uHigh == 0xFC) {
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
