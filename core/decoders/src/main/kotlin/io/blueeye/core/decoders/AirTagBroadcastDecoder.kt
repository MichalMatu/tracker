package io.blueeye.core.decoders

import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AirTagBroadcastDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Apple ID = 76 (0x004C)
        if (manufacturerId != 76 || data == null || data.size < 2) return false

        // Type 0x12 = Find My
        return data[0] == 0x12.toByte()
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        // Format: [12] [Len] [Status] [22 bytes Key Part] ...

        // Status Byte (index 2):
        // Bits 6-7: Battery Level (00=Full, 01=Medium, 10=Low, 11=Critical)
        // Bit 2: Maintainer / State

        var batteryStatus = "Unknown"
        var statusByte = 0

        if (data.size > 2) {
            statusByte = data[2].toInt() and 0xFF
            batteryStatus =
                when ((statusByte shr 6) and 0x03) {
                    0x00 -> "Full"
                    0x01 -> "Medium"
                    0x02 -> "Low"
                    0x03 -> "Critical"
                    else -> "Unknown"
                }
        }

        // Public Key Part (22 bytes starting at offset 3)
        // This key part combined with MAC address forms the full tracking key.
        val publicParts =
            if (data.size >= 25) {
                data.copyOfRange(3, 25).joinToString("") { "%02X".format(it) }
            } else {
                "Partial Data"
            }

        return SensorData(
            sensorStatus = "Bat: $batteryStatus (St: 0x%02X)".format(statusByte),
            beaconType = "Apple FindMy / AirTag",
            rawData = "KeyPart: $publicParts",
        )
    }
}
