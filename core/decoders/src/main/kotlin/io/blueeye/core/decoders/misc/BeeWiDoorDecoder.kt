package io.blueeye.core.decoders.misc

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Otio/BeeWi Door & Window Sensor decoder. Theengs: BWBSDOO_json.h Manufacturer data = 14 hex chars
 * (7 bytes), index 4 = "080c"
 */
@Singleton
class BeeWiDoorDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val data = input.data
        // Manufacturer data length = 14 hex chars = 7 bytes (including ID)
        // Android 'data' excludes ID (2 bytes), so 5 bytes expected
        if (data == null || data.size < 5) return false

        // Check index 4-5 of full manufacturer data = "080c"
        // Android data starts at byte 2, so we check index 0-1 for "080c"
        // Full: [ID0][ID1][??][??][08][0C][Batt]
        // Android: [??][??][08][0C][Batt]
        // Index 2-3 in Android = 08 0C
        if (data.size < 5) return false
        return data[2] == 0x08.toByte() && data[3] == 0x0C.toByte()
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        if (data.size < 5) return SensorData(beaconType = "BeeWi Door (Error)")

        try {
            // Open status: Bit 0 of nibble at hex index 9
            // Full hex index 9 = byte index 4, low nibble
            // But wait, Theengs says: bit_static_value, manufacturerdata, 9, 0
            // Hex index 9 corresponds to nibble, not byte.
            // Byte 4 = hex 8-9. Nibble at index 9 is the low nibble.
            // Android byte 2 (full byte 4).
            // Actually let me recalculate:
            // Full manuf data: [Byte0][Byte1][Byte2][Byte3][Byte4][Byte5][Byte6]
            //                   0-1    2-3    4-5    6-7    8-9   10-11  12-13
            // Hex index 9 is LOW nibble of Byte 4.
            // Android data starts at Byte 2.
            // Android Byte 2 = Full Byte 4.
            // So we need bit 0 of low nibble of Android data[2].

            val statusByte = data[2].toInt() and 0xFF
            val lowNibble = statusByte and 0x0F
            val isOpen = (lowNibble and 0x01) == 1

            // Battery: Hex 12-13 = Byte 6 = Android Byte 4
            val batt = data[4].toInt() and 0xFF

            return SensorData(
                doorOpen = isOpen,
                batteryLevel = batt,
                sensorStatus = if (isOpen) "Open" else "Closed",
                beaconType = "BeeWi Door/Window Sensor",
                rawData = "BSDOO: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "BeeWi Door (Parse Error)")
        }
    }
}
