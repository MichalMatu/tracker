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
    private companion object {
        const val MAINTAINED_MASK = 0x04
        const val BATTERY_SHIFT = 6
        const val BATTERY_MASK = 0x03
        const val LOW_BATTERY_CODE = 2
        const val CRITICAL_BATTERY_CODE = 3
    }

    fun parse(data: ByteArray): AppleDeviceData? {
        if (data.size < 2) return null

        // Byte 0: Status
        val statusByte = data[0].toInt() and 0xFF

        // Maintained means an owner connection occurred in the current key period.
        // Battery bits 6-7 are meaningful only when maintained is set.
        // Bits 0-1 are reserved and must not be interpreted as low battery.
        val maintained = (statusByte and MAINTAINED_MASK) != 0
        val batteryCode = (statusByte ushr BATTERY_SHIFT) and BATTERY_MASK
        val lowBattery = maintained && batteryCode >= LOW_BATTERY_CODE

        // Public Key extraction
        // The payload (bytes 1..end) represents the truncated public key.
        // Usually 22-28 bytes depending on protocol version.
        val publicKey =
            if (data.size > 1) {
                data.copyOfRange(1, data.size)
            } else {
                null
            }

        var debugInfo = if (maintained) "Find My [Maintained]" else "Find My [Not Maintained]"
        if (lowBattery) {
            debugInfo += if (batteryCode == CRITICAL_BATTERY_CODE) " [Critical Batt]" else " [Low Batt]"
        }

        return AppleDeviceData(
            deviceModel = debugInfo,
            statusFlags = statusByte,
            findMyKey = publicKey,
        )
    }
}
