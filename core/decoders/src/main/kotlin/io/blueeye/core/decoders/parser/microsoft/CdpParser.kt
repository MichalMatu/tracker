package io.blueeye.core.decoders.parser.microsoft

import io.blueeye.core.model.DeviceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for Microsoft CDP (Connected Devices Platform).
 * Manufacturer ID 0x0006 with Type 0x01.
 *
 * CDP Advertisement Format:
 * [0x01] [0x09] [0x20] [0x00] [4 bytes salt] [SHA256 hash of device thumbprint...]
 *
 * The byte at offset 1 (0x09) indicates device type:
 * - 0x01 = Xbox One
 * - 0x06 = Apple iPhone
 * - 0x07 = Apple iPad
 * - 0x08 = Android Device
 * - 0x09 = Windows 10 Desktop
 * - 0x0B = Windows 10 Phone
 * - 0x0C = Linux Device
 * - 0x0D = Windows IoT
 * - 0x0E = Surface Hub
 * - 0x0F = Windows Laptop
 * - 0x10 = Windows Tablet
 * - 0x11 = Xbox Series X/S
 *
 * Based on reverse engineering from:
 * - StackOverflow analysis
 * - 'Joker' research
 * - nRF Sniffer captures
 */
@Singleton
class CdpParser
@Inject
constructor() {
    companion object {
        const val CDP_TYPE_BEACON = 0x01

        // Device Type IDs (byte at offset 1 in some CDP formats)
        private const val DEVICE_XBOX_ONE = 0x01
        private const val DEVICE_APPLE_IPHONE = 0x06
        private const val DEVICE_APPLE_IPAD = 0x07
        private const val DEVICE_ANDROID = 0x08
        private const val DEVICE_WINDOWS_DESKTOP = 0x09
        private const val DEVICE_WINDOWS_PHONE = 0x0B
        private const val DEVICE_LINUX = 0x0C
        private const val DEVICE_WINDOWS_IOT = 0x0D
        private const val DEVICE_SURFACE_HUB = 0x0E
        private const val DEVICE_WINDOWS_LAPTOP = 0x0F
        private const val DEVICE_WINDOWS_TABLET = 0x10
        private const val DEVICE_XBOX_SERIES = 0x11
    }

    fun parse(data: ByteArray): MicrosoftDeviceData? {
        if (data.size < 2) return null

        val type = data[0].toInt() and 0xFF

        // CDP uses Type 0x01
        if (type != CDP_TYPE_BEACON) return null

        // Try to extract device type from byte 1
        val deviceTypeByte = data[1].toInt() and 0xFF

        val (deviceType, deviceModel) = mapDeviceType(deviceTypeByte)

        // Extract salt (bytes 4-7) if available
        val salt = if (data.size >= 8) {
            data.copyOfRange(4, 8).joinToString("") { "%02X".format(it) }
        } else {
            null
        }

        // Extract device hash (remaining bytes after salt)
        val deviceHash = if (data.size > 8) {
            data.copyOfRange(8, minOf(data.size, 24)).joinToString("") { "%02X".format(it) }
        } else {
            null
        }

        // Check for specific patterns
        val isWindows10Pattern = data.size >= 4 &&
            data[1].toInt() and 0xFF == 0x09 &&
            data[2].toInt() and 0xFF == 0x20 &&
            data[3].toInt() and 0xFF == 0x00

        val finalModel = if (isWindows10Pattern) {
            "Windows 10 Desktop"
        } else {
            deviceModel
        }

        return MicrosoftDeviceData(
            deviceModel = finalModel,
            deviceType = deviceType,
            isSwiftPair = false,
            sessionId = deviceTypeByte,
            deviceHash = deviceHash ?: salt,
            cdpDeviceType = deviceTypeByte,
            cdpSalt = salt,
        )
    }

    private fun mapDeviceType(typeByte: Int): Pair<DeviceType, String> {
        return when (typeByte) {
            DEVICE_XBOX_ONE -> DeviceType.CONSOLE to "Xbox One"
            DEVICE_APPLE_IPHONE -> DeviceType.PHONE to "iPhone (via CDP)"
            DEVICE_APPLE_IPAD -> DeviceType.TABLET to "iPad (via CDP)"
            DEVICE_ANDROID -> DeviceType.PHONE to "Android Device"
            DEVICE_WINDOWS_DESKTOP -> DeviceType.PC to "Windows 10 Desktop"
            DEVICE_WINDOWS_PHONE -> DeviceType.PHONE to "Windows Phone"
            DEVICE_LINUX -> DeviceType.PC to "Linux Device"
            DEVICE_WINDOWS_IOT -> DeviceType.SMART_HOME to "Windows IoT Device"
            DEVICE_SURFACE_HUB -> DeviceType.PC to "Surface Hub"
            DEVICE_WINDOWS_LAPTOP -> DeviceType.LAPTOP to "Windows Laptop"
            DEVICE_WINDOWS_TABLET -> DeviceType.TABLET to "Windows Tablet"
            DEVICE_XBOX_SERIES -> DeviceType.CONSOLE to "Xbox Series X/S"
            else -> DeviceType.PC to "Windows Device (0x${"%02X".format(typeByte)})"
        }
    }
}
