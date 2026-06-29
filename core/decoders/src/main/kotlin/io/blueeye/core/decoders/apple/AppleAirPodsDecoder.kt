package io.blueeye.core.decoders.apple

import io.blueeye.core.decoders.BleBeaconDecoder
import io.blueeye.core.decoders.BleBeaconScanInput
import io.blueeye.core.model.SensorData
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.experimental.and

@Singleton
class AppleAirPodsDecoder
@Inject
constructor() : BleBeaconDecoder {
    override fun supports(input: BleBeaconScanInput): Boolean {
        val manufacturerId = input.manufacturerId
        val data = input.data
        // Apple ID = 0x004C
        if (manufacturerId != 0x004C || data == null) return false

        // Structure for AirPods (0x07 - Proximity Pair)
        // [0x07] [Length] [0x01] ...
        // Typically Length is 0x19 (25 bytes) => Total Mfg Data size 2+25 = 27 bytes.
        // But Android might strip the ID.
        // data array passed here usually excludes Manufacturer ID bytes (0x4C 0x00).
        // So 'data' starts with 0x07.

        if (data.size < 25) return false

        // Type 0x07
        if (data[0] != 0x07.toByte()) return false

        // Check byte 2 (Index 2) -> 0x01 ?? (Based on Theengs condition '4c00071901')
        // In 'data': [07] [19] [01] ...
        if (data.size > 2 && data[2] != 0x01.toByte()) return false

        return true
    }

    override fun decode(input: BleBeaconScanInput): SensorData {
        val data = input.data ?: byteArrayOf()
        // Data Structure (Theengs offsets are hex chars, we use bytes)
        // Data starts at 0x07 (Index 0)

        // Model: Hex 10 (Nibbles) -> Byte 5. Length 2 bytes.
        // [07] [19] [01] [xx] [xx] [Model_Hi] [Model_Lo]
        // Indices: 0, 1, 2, 3, 4, 5, 6
        // Wait, '4c00 07 19 01' is 5 bytes.
        // Android 'data' excludes 4c00.
        // So 'data': [07] [19] [01] [xx] ...
        // Index 0: 07
        // Index 1: 19
        // Index 2: 01

        // Theengs Condition: 4c00071901
        // Theengs 'version' decoder: [string_from_hex_data, manufacturerdata, 10, 4]
        // Hex 10 = 5th byte (0-indexed bytes: 0,1,2,3,4).
        // Bytes: [4C][00][07][19][01]
        // Indices: 0   1   2   3   4
        // Theengs implies Version starts at Byte 5 (Index 5).
        // So in Android 'data' (shifted by 2):
        // [07][19][01]...
        // 0   1   2
        // We need Byte 3 relative to 'data'?
        // No.
        // 4C 00 07 19 01 [VerHi] [VerLo] ...
        // 0  1  2  3  4  5       6
        // Android Data:
        // 07 19 01 [VerHi] [VerLo]
        // 0  1  2  3       4
        // So Android Index = Original Index - 2.
        // Version starts at 5-2 = 3.

        // Color: Hex 22 -> Byte 11. Android Index: 11-2 = 9.

        // Status: Hex 14 -> Byte 7. Android Index: 7-2 = 5.
        // 'batt_r' condition: bit 1 of byte 7.
        // 'batt_r' loc: Hex 16/17 -> Byte 8. Android Index: 6.
        // 'batt_case' loc: Hex 19 -> Byte 9 (High nibble?). Hex 19 is odd.

        // Wait, Theengs hex logic:
        // Byte 8 is hex-chars 16,17.
        // Byte 9 is hex-chars 18,19.
        // 'batt_r' decoder: [value, hex 16, 1]. Nibble at 16 (Byte 8 High).
        // 'batt_l' decoder: [value, hex 17, 1]. Nibble at 17 (Byte 8 Low).
        // 'batt_case': [value, hex 19, 1]. Nibble at 19 (Byte 9 low).

        // Android Indices:
        // Byte 5 (Status): Index 5.
        // Byte 6 (Bat1): Index 6. (Contains R/L info)
        // Byte 7 (Bat2): Index 7. (Contains Case info)

        try {
            if (data.size <= 7) return SensorData(beaconType = "AirPods (Short)")

            // 1. Model
            val modelId =
                if (data.size >= 5) {
                    // Bytes 3,4
                    "%02x%02x".format(data[3], data[4])
                } else {
                    ""
                }

            val modelName =
                when (modelId) {
                    "0220" -> "AirPods 1st gen"
                    "0f20" -> "AirPods 2nd gen"
                    "0e20" -> "AirPods Pro 1st gen"
                    "1420" -> "AirPods Pro 2 L"
                    "2420" -> "AirPods Pro 2 C"
                    "0a20" -> "AirPods Max"
                    "0320" -> "Powerbeats 3"
                    "0520" -> "BeatsX"
                    "0620" -> "Beats Solo 3"
                    else -> "AirPods/Beats ($modelId)"
                }

            // 2. Status
            val statusByte = data[5].toInt()
            // Using logic: Check bit 1 (mask 0x02)??
            // Theengs: "bit", 1, 1. Theengs usually 0-indexed bits from LSB?
            // Let's assume Mask 0x02.
            // val isFlip = (statusByte and 0x02) == 0 // Unused
            // Actually Theengs says:
            // Condition batt_r: bit 1 is 1 -> index 16 (Byte 6 Hi).
            // Condition _batt_r: bit 1 is 0 -> index 17 (Byte 6 Lo).

            // Let's extract nibbles from Byte 6 (Index 6)
            val batByte = data[6].toInt()
            val batHi = (batByte shr 4) and 0x0F
            val batLo = batByte and 0x0F

            // Case Battery from Byte 7 (Index 7), low nibble
            val caseByte = if (data.size > 7) data[7].toInt() else 0
            val batCaseRaw = caseByte and 0x0F

            val batRightRaw = if ((statusByte and 0x02) != 0) batHi else batLo
            val batLeftRaw = if ((statusByte and 0x02) != 0) batLo else batHi

            // Values 0-10 -> 0-100% (multiply by 10).
            // If 15 (0xF) -> Disconnected/Unknown ??

            val batR = if (batRightRaw <= 10) batRightRaw * 10 else null
            val batL = if (batLeftRaw <= 10) batLeftRaw * 10 else null
            val batCase = if (batCaseRaw <= 10) batCaseRaw * 10 else null

            // Construct string
            val statusStr =
                buildString {
                    if (batL != null) append("L: $batL% ")
                    if (batR != null) append("R: $batR% ")
                    if (batCase != null) append("Case: $batCase%")
                }

            return SensorData(
                beaconType = modelName,
                sensorStatus = statusStr.trim(),
                batteryLevel = batCase ?: batL ?: batR, // Returns one as generic batt
                rawData = "AirPods: ${data.joinToString("") { "%02X".format(it) }}",
            )
        } catch (e: Exception) {
            return SensorData(beaconType = "AirPods (Parse Error)")
        }
    }
}
