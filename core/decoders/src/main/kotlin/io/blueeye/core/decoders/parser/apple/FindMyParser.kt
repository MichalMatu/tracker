package io.blueeye.core.decoders.parser.apple

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for Apple Find My / Offline Finding (Type 0x12). Based on OpenHaystack / Seemoo Lab
 * research.
 */
@Singleton
class FindMyParser
@Inject
constructor() {
    fun parse(data: ByteArray): AppleDeviceData? {
        if (data.size < 2) return null

        // Byte 0: Status
        val statusByte = data[0].toInt() and 0xFF

        // Status Flags (OpenHaystack / Reverse Engineering)
        // Bit 0: Low Battery
        // Bit 2: Separated from owner (Key rotation state?)
        val lowBattery = (statusByte and 0x01) != 0
        val separated = (statusByte and 0x04) != 0

        // Public Key extraction
        // The payload (bytes 1..end) represents the truncated public key.
        // Usually 22-28 bytes depending on protocol version.
        val publicKey =
            if (data.size > 1) {
                data.copyOfRange(1, data.size)
            } else {
                null
            }

        var debugInfo = "Find My"
        if (separated) debugInfo += " [Separated]"
        if (lowBattery) debugInfo += " [Low Batt]"

        return AppleDeviceData(
            deviceModel = debugInfo,
            statusFlags = statusByte,
            findMyKey = publicKey,
            batteryLevelLeft = if (lowBattery) 10 else null, // Heuristic
        )
    }
}
