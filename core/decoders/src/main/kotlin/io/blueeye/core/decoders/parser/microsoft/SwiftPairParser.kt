package io.blueeye.core.decoders.parser.microsoft

import io.blueeye.core.model.DeviceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for Microsoft Swift Pair (Manufacturer ID 0x0006).
 * Based on Microsoft Official Documentation:
 * https://learn.microsoft.com/en-us/windows-hardware/design/component-guidelines/bluetooth-swift-pair
 *
 * Payload Structure:
 * [0x03] [Scenario ID] [RSSI Byte=0x80] [Optional: CoD/BR_EDR Address/Display Name]
 *
 * Scenario IDs:
 * - 0x01 = LE Only pairing (Figure 2)
 * - 0x02 = LE + BR/EDR with Secure Connections (Figure 3)
 * - 0x06 = BR/EDR only, LE for discovery (Figure 4)
 */
@Singleton
class SwiftPairParser
@Inject
constructor() {
    companion object {
        const val BEACON_TYPE_SWIFT_PAIR = 0x03

        // Scenario IDs from Microsoft Docs
        const val SCENARIO_LE_ONLY = 0x01
        const val SCENARIO_LE_AND_BREDR_SECURE = 0x02
        const val SCENARIO_BREDR_ONLY = 0x06

        // Class of Device Major Classes (Bluetooth SIG)
        private const val COD_MAJOR_COMPUTER = 0x01
        private const val COD_MAJOR_PHONE = 0x02
        private const val COD_MAJOR_AUDIO_VIDEO = 0x04
        private const val COD_MAJOR_PERIPHERAL = 0x05
        private const val COD_MAJOR_IMAGING = 0x06
        private const val COD_MAJOR_WEARABLE = 0x07
    }

    fun parse(data: ByteArray): MicrosoftDeviceData? {
        if (data.size < 2) return null

        val type = data[0].toInt() and 0xFF
        if (type != BEACON_TYPE_SWIFT_PAIR) return null

        val scenarioId = data[1].toInt() and 0xFF

        // RSSI byte (reserved) is at index 2, ignored.

        var deviceType = DeviceType.UNKNOWN
        var deviceModel: String?
        var brEdrAddress: String? = null
        var classOfDevice: Int? = null
        var displayName: String? = null

        when (scenarioId) {
            SCENARIO_LE_ONLY -> {
                // LE Only - no BR/EDR address, may have display name
                deviceModel = "Bluetooth LE Device"
                // Display name starts at byte 3 if present
                if (data.size > 3) {
                    displayName = parseDisplayName(data, 3)
                }
            }
            SCENARIO_LE_AND_BREDR_SECURE -> {
                // LE + BR/EDR with Secure Connections
                // Byte 3-5: Class of Device (3 bytes)
                deviceModel = "Dual Mode Device"
                if (data.size >= 6) {
                    classOfDevice = parseClassOfDevice(data, 3)
                    deviceType = mapClassOfDeviceToType(classOfDevice)
                    deviceModel = mapClassOfDeviceToModel(classOfDevice)
                }
                // Display name after CoD
                if (data.size > 6) {
                    displayName = parseDisplayName(data, 6)
                }
            }
            SCENARIO_BREDR_ONLY -> {
                // BR/EDR Only - LE used for discovery
                // Byte 3-5: Class of Device (3 bytes)
                // Byte 6-11: BR/EDR Address (6 bytes, little endian)
                deviceModel = "Classic Bluetooth Device"
                if (data.size >= 6) {
                    classOfDevice = parseClassOfDevice(data, 3)
                    deviceType = mapClassOfDeviceToType(classOfDevice)
                    deviceModel = mapClassOfDeviceToModel(classOfDevice)
                }
                if (data.size >= 12) {
                    brEdrAddress = parseBrEdrAddress(data, 6)
                }
                // Display name after address
                if (data.size > 12) {
                    displayName = parseDisplayName(data, 12)
                }
            }
            else -> {
                // Unknown scenario
                deviceModel = "Swift Pair (Scenario 0x${"%02X".format(scenarioId)})"
            }
        }

        // Use display name if available
        if (!displayName.isNullOrEmpty()) {
            deviceModel = displayName
        }

        return MicrosoftDeviceData(
            deviceModel = deviceModel,
            deviceType = deviceType,
            isSwiftPair = true,
            scenarioId = scenarioId,
            classOfDevice = classOfDevice,
            brEdrAddress = brEdrAddress,
            displayName = displayName,
            deviceHash = if (data.size > 2) {
                data.drop(2).joinToString("") { "%02X".format(it) }
            } else {
                null
            },
        )
    }

    private fun parseClassOfDevice(data: ByteArray, offset: Int): Int {
        if (data.size < offset + 3) return 0
        // 3 bytes, little endian
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16)
    }

    private fun parseBrEdrAddress(data: ByteArray, offset: Int): String? {
        if (data.size < offset + 6) return null
        // 6 bytes, little endian (reversed)
        val bytes = data.copyOfRange(offset, offset + 6).reversedArray()
        return bytes.joinToString(":") { "%02X".format(it) }
    }

    private fun parseDisplayName(data: ByteArray, offset: Int): String? {
        if (data.size <= offset) return null
        return try {
            String(data.copyOfRange(offset, data.size), Charsets.UTF_8).trim('\u0000')
        } catch (e: Exception) {
            null
        }
    }

    private fun mapClassOfDeviceToType(cod: Int): DeviceType {
        val majorClass = (cod shr 8) and 0x1F
        val minorClass = (cod shr 2) and 0x3F

        return when (majorClass) {
            COD_MAJOR_COMPUTER -> DeviceType.PC
            COD_MAJOR_PHONE -> DeviceType.PHONE
            COD_MAJOR_AUDIO_VIDEO -> {
                when (minorClass) {
                    0x01, 0x02 -> DeviceType.WEARABLE // Headset
                    0x06 -> DeviceType.AUDIO // Headphones
                    0x07 -> DeviceType.AUDIO // Portable Audio
                    0x0B -> DeviceType.AUDIO // VCR
                    else -> DeviceType.AUDIO
                }
            }
            COD_MAJOR_PERIPHERAL -> {
                when (minorClass and 0x30) {
                    0x10 -> DeviceType.PC // Keyboard
                    0x20 -> DeviceType.PC // Pointing device (Mouse)
                    0x30 -> DeviceType.PC // Combo keyboard/pointing
                    else -> DeviceType.PERIPHERAL
                }
            }
            COD_MAJOR_IMAGING -> DeviceType.PERIPHERAL
            COD_MAJOR_WEARABLE -> DeviceType.WEARABLE
            else -> DeviceType.UNKNOWN
        }
    }

    private fun mapClassOfDeviceToModel(cod: Int): String {
        val majorClass = (cod shr 8) and 0x1F
        val minorClass = (cod shr 2) and 0x3F

        return when (majorClass) {
            COD_MAJOR_COMPUTER -> {
                when (minorClass) {
                    0x01 -> "Desktop Computer"
                    0x02 -> "Server"
                    0x03 -> "Laptop"
                    0x04 -> "Handheld PC/PDA"
                    0x05 -> "Palm Size PC/PDA"
                    0x06 -> "Wearable Computer"
                    0x07 -> "Tablet"
                    else -> "Computer"
                }
            }
            COD_MAJOR_PHONE -> {
                when (minorClass) {
                    0x01 -> "Cellular Phone"
                    0x02 -> "Cordless Phone"
                    0x03 -> "Smartphone"
                    0x04 -> "Wired Modem"
                    0x05 -> "ISDN Access"
                    else -> "Phone"
                }
            }
            COD_MAJOR_AUDIO_VIDEO -> {
                when (minorClass) {
                    0x01 -> "Wearable Headset"
                    0x02 -> "Hands-free Device"
                    0x04 -> "Microphone"
                    0x05 -> "Loudspeaker"
                    0x06 -> "Headphones"
                    0x07 -> "Portable Audio"
                    0x08 -> "Car Audio"
                    0x09 -> "Set-top Box"
                    0x0A -> "HiFi Audio"
                    0x0B -> "VCR"
                    0x0C -> "Video Camera"
                    0x0D -> "Camcorder"
                    0x0E -> "Video Monitor"
                    0x0F -> "Video Display and Loudspeaker"
                    0x10 -> "Video Conferencing"
                    0x12 -> "Gaming/Toy"
                    else -> "Audio/Video Device"
                }
            }
            COD_MAJOR_PERIPHERAL -> {
                val subMinor = minorClass and 0x0F
                val pointing = (minorClass and 0x30) shr 4
                when (pointing) {
                    1 -> "Keyboard"
                    2 -> "Mouse"
                    3 -> "Combo Keyboard/Mouse"
                    else -> when (subMinor) {
                        0x01 -> "Joystick"
                        0x02 -> "Gamepad"
                        0x03 -> "Remote Control"
                        0x04 -> "Sensing Device"
                        0x05 -> "Digitizer Tablet"
                        0x06 -> "Card Reader"
                        0x07 -> "Digital Pen"
                        0x08 -> "Handheld Scanner"
                        0x09 -> "Handheld Gestural Input"
                        else -> "Peripheral"
                    }
                }
            }
            COD_MAJOR_IMAGING -> {
                val imagingBits = (minorClass shr 2) and 0x0F
                when {
                    (imagingBits and 0x01) != 0 -> "Display"
                    (imagingBits and 0x02) != 0 -> "Camera"
                    (imagingBits and 0x04) != 0 -> "Scanner"
                    (imagingBits and 0x08) != 0 -> "Printer"
                    else -> "Imaging Device"
                }
            }
            COD_MAJOR_WEARABLE -> {
                when (minorClass) {
                    0x01 -> "Wristwatch"
                    0x02 -> "Pager"
                    0x03 -> "Jacket"
                    0x04 -> "Helmet"
                    0x05 -> "Glasses"
                    else -> "Wearable"
                }
            }
            else -> "Swift Pair Device"
        }
    }
}
