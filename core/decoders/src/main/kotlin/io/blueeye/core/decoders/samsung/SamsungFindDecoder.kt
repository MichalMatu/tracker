package io.blueeye.core.decoders.samsung

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Samsung SmartThings Find Decoder (Service Data 0xFD5A).
 * Based on KieronQuinn's uTag implementation (SmartTagRepository.decodeServiceData).
 */
@Singleton
class SamsungFindDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val rawData = input.rawData
        if (rawData == null) return false

        // Check for SmartThings Find Service UUID 0xFD5A in Service Data
        // We use rawData helper to find it.
        return findServiceData(rawData, 0xFD5A) != null
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val rawData = input.rawData
        if (rawData == null) return SensorData(beaconType = "Samsung Find (Error)")

        val serviceData = findServiceData(rawData, 0xFD5A)
            ?: return SensorData(beaconType = "Samsung Find (No Data)")

        if (serviceData.size < 12) return SensorData(beaconType = "Samsung Find (Short Data)")

        try {
            // Layout per uTag:
            // Byte 0: State (Bits 0-2), Adv Type (Bit 3), Version (High Nibble)
            val fullByte0 = serviceData[0].toInt() and 0xFF
            // val version = (fullByte0 and 0xF0) shr 4 // Unused
            val tagStateVal = fullByte0 and 7
            // val advType = (fullByte0 shr 3) and 1

            val tagStateStr = when (tagStateVal) {
                1 -> "Premature Offline"
                2 -> "Offline"
                3 -> "Overmature Offline"
                4 -> "Connected (Owner)"
                5 -> "Connected (Other)"
                6 -> "Shared"
                else -> "Unknown ($tagStateVal)"
            }

            // Bytes 4-11: ID (Privacy ID)
            val idBytes = if (serviceData.size >= 12) serviceData.copyOfRange(4, 12) else ByteArray(0)
            val privacyId = idBytes.joinToString("") { "%02X".format(it) }

            // Byte 12: Flags
            // High Nibble: Region ID
            // Low Nibble: Flags
            // Bit 0-1: Battery
            // Bit 2: UWB
            // Bit 3: Encryption
            val fullByte12 = if (serviceData.size > 12) serviceData[12].toInt() and 0xFF else 0
            val batteryVal = fullByte12 and 3
            val uwb = ((fullByte12 shr 2) and 1) == 1

            // Map Battery to % (Approximation)
            val batteryPercent = when (batteryVal) {
                0 -> 5 // Very Low
                1 -> 20 // Low
                2 -> 60 // Medium
                3 -> 100 // Full
                else -> null
            }

            // Byte 13: Motion (Bit 7)
            val fullByte13 = if (serviceData.size > 13) serviceData[13].toInt() and 0xFF else 0
            val motion = ((fullByte13 shr 7) and 1) == 1

            val status = "State: $tagStateStr, Bat: $batteryVal, UWB: $uwb, Motion: $motion"

            return SensorData(
                beaconType = "Samsung SmartTag ($tagStateStr)",
                batteryLevel = batteryPercent,
                sensorStatus = status,
                rawData = "ID: $privacyId, $status",
                // We can map motion to specific field if SensorData has one?
                // SensorData has 'movementCounter' but not a boolean.
                // We'll leave it in status.
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "Samsung Find (Parse Error)")
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
            if (type == 0x16 && i + 3 < rawData.size) { // Service Data 16-bit UUID
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
