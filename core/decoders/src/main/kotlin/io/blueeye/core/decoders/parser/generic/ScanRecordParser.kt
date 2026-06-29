package io.blueeye.core.decoders.parser.generic

import android.bluetooth.BluetoothDevice
import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRecordParser
@Inject
constructor() {
    /**
     * Extract BLE Appearance from raw scan record bytes. Appearance is AD Type 0x19, contains 2
     * bytes (little-endian).
     */
    fun extractAppearance(bytes: ByteArray?): Int? {
        if (bytes == null) return null

        var i = 0
        while (i < bytes.size - 1) {
            val length = bytes[i].toInt() and 0xFF
            if (length == 0) break
            if (i + length >= bytes.size) break

            val type = bytes[i + 1].toInt() and 0xFF

            // AD Type 0x19 = Appearance
            if (type == 0x19 && length >= 3) {
                val low = bytes[i + 2].toInt() and 0xFF
                val high = bytes[i + 3].toInt() and 0xFF
                return (high shl 8) or low
            }

            i += length + 1
        }
        return null
    }

    /** Determine BLE technology version based on scan result properties. */
    fun determineTechnology(
        isLegacy: Boolean,
        primaryPhy: Int,
    ): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return "BLE"
        }

        if (!isLegacy) {
            return "BLE 5+ (Ext)"
        } else if (primaryPhy == BluetoothDevice.PHY_LE_2M ||
            primaryPhy == BluetoothDevice.PHY_LE_CODED
        ) {
            return "BLE 5+ (Phy)"
        }

        return "BLE"
    }
}
