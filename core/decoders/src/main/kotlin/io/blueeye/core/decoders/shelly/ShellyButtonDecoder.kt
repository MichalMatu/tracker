package io.blueeye.core.decoders.shelly

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shelly BLU Button1 (SBBT-002C) decoder. Theengs: SBBT_002C_json.h BTHome format (FCD2), name
 * starts with "SBBT-"
 */
@Singleton
class ShellyButtonDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val serviceUuids = input.serviceUuids
        val rawData = input.rawData
        if (rawData == null) return false

        // Check for UUID FCD2
        if (!serviceUuids.any { it.lowercase().contains("fcd2") }) return false

        // Check for name starting with "SBBT-"
        val localName = extractLocalName(rawData)
        if (localName?.startsWith("SBBT-") != true) return false

        val serviceData = findServiceDataFCD2(rawData) ?: return false

        // Service data = 14 hex chars = 7 bytes, starts with 40 or 44
        // Or 13 bytes for Switch4 (26 hex)
        // Or encrypted (starts with 41 or 45)
        if (serviceData.isEmpty()) return false
        val firstByte = serviceData[0].toInt() and 0xFF
        return firstByte == 0x40 || firstByte == 0x44 || firstByte == 0x41 || firstByte == 0x45
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Shelly Button (Error)")

        val serviceData =
            findServiceDataFCD2(rawData)
                ?: return SensorData(beaconType = "Shelly Button (No Data)")

        try {
            val firstByte = serviceData[0].toInt() and 0xFF
            if (firstByte == 0x41 || firstByte == 0x45) {
                return SensorData(
                    beaconType = "Shelly BLU Button (Encrypted)",
                    rawData = "Encrypted: ${serviceData.joinToString("") { "%02X".format(it) }}",
                )
            }

            var battery: Int? = null
            val buttonPresses = mutableListOf<String>()

            // Parse BTHome TLV format
            var i = 1 // Skip first byte (0x40/0x44)
            while (i < serviceData.size - 1) {
                val typeId = serviceData[i].toInt() and 0xFF
                i++

                when (typeId) {
                    0x00 -> i += 1 // Packet ID
                    0x01 -> { // Battery
                        battery = serviceData[i].toInt() and 0xFF
                        i += 1
                    }
                    0x3A -> { // Button
                        val buttonValue = serviceData[i].toInt() and 0xFF
                        val press =
                            when (buttonValue) {
                                0x00 -> "None"
                                0x01 -> "Single"
                                0x02 -> "Double"
                                0x03 -> "Triple"
                                0x04 -> "Long"
                                0x0B -> "Release" // From Switch4 lookup
                                0xFE -> "Hold"
                                else -> "Btn ($buttonValue)"
                            }
                        buttonPresses.add(press)
                        i += 1
                    }
                    else -> i += 1
                }
            }

            val typeName =
                if (serviceData.size >= 10) "Shelly BLU Switch4" else "Shelly BLU Button1"

            return SensorData(
                batteryLevel = battery,
                sensorStatus = buttonPresses.joinToString(", ").ifEmpty { "Idle" },
                beaconType = typeName,
                rawData = "Shelly: ${serviceData.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Shelly Button (Parse Error)")
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
