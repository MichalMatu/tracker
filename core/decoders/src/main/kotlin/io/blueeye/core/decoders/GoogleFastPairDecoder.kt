package io.blueeye.core.decoders

import io.blueeye.core.model.DeviceType

/**
 * Decoder for Google Fast Pair Service Data (UUID 0xFE2C).
 *
 * Fast Pair is Google's protocol for quick Bluetooth pairing on Android.
 * Many headphones (Bose, Sony, JBL, etc.) use this protocol.
 *
 * Specification: https://developers.google.com/nearby/fast-pair/specifications/introduction
 */
object GoogleFastPairDecoder {
    const val SERVICE_UUID = 0xFE2C

    data class FastPairInfo(
        val deviceType: DeviceType,
        val modelId: Int? = null,
        val modelName: String? = null,
        val isDiscoverable: Boolean = false,
        val batteryLevel: Int? = null,
        val batteryLeft: Int? = null,
        val batteryRight: Int? = null,
        val batteryCase: Int? = null,
    )

    // Known Model IDs from Google Fast Pair device database
    // Source: https://developers.google.com/nearby/devices
    private val knownModels = mapOf(
        // Bose
        0x0050_81 to "Bose QC Ultra Headphones",
        0x30_39_F5 to "Bose QuietComfort Earbuds II",
        0xF0_04_08 to "Bose QuietComfort 45",
        0x00_00_39 to "Bose Frames",

        // Sony
        0x00_D4_46 to "Sony WF-1000XM4",
        0x02_12_47 to "Sony WH-1000XM4",
        0x02_9B_B6 to "Sony WH-1000XM5",
        0x02_9B_0A to "Sony WF-1000XM5",

        // JBL
        0x00_43_45 to "JBL Tune Flex",
        0x00_D8_48 to "JBL Live Pro+ TWS",

        // Samsung (via Fast Pair)
        0x00_01_00 to "Galaxy Buds",
        0x00_01_01 to "Galaxy Buds+",
        0x00_01_02 to "Galaxy Buds Live",
        0x00_01_03 to "Galaxy Buds Pro",
        0x00_01_04 to "Galaxy Buds2",
        0x00_01_05 to "Galaxy Buds2 Pro",
        0x00_01_06 to "Galaxy Buds FE",

        // Google
        0x00_00_82 to "Pixel Buds",
        0x00_F0_00 to "Pixel Buds A-Series",
        0x00_F1_00 to "Pixel Buds Pro",

        // Beats
        0x00_00_01 to "Beats Studio Buds",
    )

    /**
     * Decode Fast Pair Service Data.
     * @param serviceData The service data bytes (after the UUID).
     */
    fun decode(serviceData: ByteArray): FastPairInfo? {
        if (serviceData.isEmpty()) return null

        // Check for Nearby Share / Quick Share (starts with 0xFC12)
        // Service data = fc 12 8e 01 42 00 ...
        if (serviceData.size >= 2 && serviceData[0] == 0xFC.toByte() && serviceData[1] == 0x12.toByte()) {
            return decodeNearbyShare(serviceData)
        }

        return when {
            // Discoverable mode: 3 bytes = Model ID
            serviceData.size == 3 -> decodeModelId(serviceData)

            // Not discoverable: Version/Flags + Account Key Filter + Salt
            serviceData.size >= 1 -> decodeAccountData(serviceData)

            else -> null
        }
    }

    private fun decodeNearbyShare(data: ByteArray): FastPairInfo {
        // Format: 0xFC 0x12 [Header 1 byte] [Auth Token/Salt 2 bytes] ...
        // Header (data[2]):
        // Bit 0-2: Version
        // Bit 3: Visibility (0=Visible, 1=Hidden)
        // Bit 4-6: Device Type

        var deviceType = DeviceType.UNKNOWN
        var visibility = "Unknown"
        var isSharing = false

        if (data.size >= 3) {
            val header = data[2].toInt() and 0xFF
            val visibilityBit = (header shr 3) and 0x01
            val deviceTypeBits = (header shr 4) and 0x07

            visibility = if (visibilityBit == 0) "Visible" else "Hidden"

            // Device Types from grishka/NearDrop PROTOCOL.md
            deviceType = when (deviceTypeBits) {
                0 -> DeviceType.UNKNOWN // Unknown
                1 -> DeviceType.PHONE // Phone
                2 -> DeviceType.TABLET // Tablet
                3 -> DeviceType.LAPTOP // Laptop (was COMPUTER)
                4 -> DeviceType.CAR // Car (was OTHER)
                5 -> DeviceType.PHONE // Foldable Phone
                6 -> DeviceType.WEARABLE // XR Device?
                else -> DeviceType.UNKNOWN
            }

            isSharing = true
        }

        return FastPairInfo(
            deviceType = deviceType,
            modelName = "Quick Share ($visibility)",
            isDiscoverable = isSharing
        )
    }

    private fun decodeModelId(data: ByteArray): FastPairInfo {
        // Model ID is 24-bit (3 bytes), Big Endian
        val modelId = ((data[0].toInt() and 0xFF) shl 16) or
            ((data[1].toInt() and 0xFF) shl 8) or
            (data[2].toInt() and 0xFF)

        val modelName = knownModels[modelId] ?: "Fast Pair Device (0x${modelId.toString(16).uppercase().padStart(6, '0')})"

        return FastPairInfo(
            deviceType = DeviceType.HEADPHONES,
            modelId = modelId,
            modelName = modelName,
            isDiscoverable = true
        )
    }

    private fun decodeAccountData(data: ByteArray): FastPairInfo {
        // Byte 0: Version (upper nibble) + Flags (lower nibble)
        // val versionAndFlags = data[0].toInt() and 0xFF
        // val version = (versionAndFlags shr 4) and 0x0F
        // val flags = versionAndFlags and 0x0F

        // Check for battery info (flag bit 1 = Show UI indication)
        // Extended format may include battery data
        var batteryLeft: Int? = null
        var batteryRight: Int? = null
        var batteryCase: Int? = null

        // Some implementations include battery in extended data
        // Format varies, but commonly: [L][R][Case] each as percentage with charging flag in MSB
        // Battery data often starts at index 1 or after account key (which is variable length)
        // Without exact spec for account data mode, heuristics are tricky.
        // Simple heuristic: look for 3 bytes that are valid percentages (0-100) at the end of packet
        if (data.size >= 4) {
            val last3 = data.takeLast(3)
            if (last3.size == 3) {
                val b1 = last3[0].toInt() and 0x7F
                val b2 = last3[1].toInt() and 0x7F
                val b3 = last3[2].toInt() and 0x7F

                // Strict check: must be percentages and non-zero (to avoid false positives with salt)
                if (b1 in 0..100 && b2 in 0..100 && b3 in 0..100) {
                    // If all are 0, it's likely salt, not empty batteries
                    if (b1 != 0 || b2 != 0 || b3 != 0) {
                        batteryLeft = b1
                        batteryRight = b2
                        batteryCase = b3
                    }
                }
            }
        }

        return FastPairInfo(
            deviceType = DeviceType.HEADPHONES,
            isDiscoverable = false,
            batteryLeft = batteryLeft,
            batteryRight = batteryRight,
            batteryCase = batteryCase
        )
    }

    /**
     * Get a human-readable summary of the Fast Pair info.
     */
    fun getSummary(info: FastPairInfo): String {
        val sb = StringBuilder()

        if (info.modelName != null) {
            sb.append(info.modelName)
        } else {
            sb.append("Fast Pair Device")
        }

        if (info.isDiscoverable) {
            sb.append(" [Pairing Mode]")
        }

        if (info.batteryLeft != null || info.batteryRight != null) {
            sb.append(" [")
            if (info.batteryLeft != null) sb.append("L:${info.batteryLeft}%")
            if (info.batteryRight != null) sb.append(" R:${info.batteryRight}%")
            if (info.batteryCase != null) sb.append(" C:${info.batteryCase}%")
            sb.append("]")
        }

        return sb.toString()
    }
}
