package io.blueeye.core.scanner.analysis.parsers

import io.blueeye.core.scanner.analysis.BleBinaryConstants

/**
 * Parser for BLE Advertisement UUID lists (16-bit, 32-bit, 128-bit).
 */
object BleUuidParser {
    private const val UUID_16_LEN = 2
    private const val UUID_128_LEN = 16

    fun parse16(data: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < data.size - 1) {
            val uuid = (data[i + 1].toInt() and BleBinaryConstants.MASK_BYTE shl BleBinaryConstants.SHIFT_8) or
                (data[i].toInt() and BleBinaryConstants.MASK_BYTE)
            sb.append("0x%04X, ".format(uuid))
            i += UUID_16_LEN
        }
        return sb.toString().trimEnd(',', ' ')
    }

    fun parse32(data: ByteArray): String {
        return "HEX: " + data.joinToString("") { "%02X".format(it) }
    }

    fun parse128(data: ByteArray): String {
        val count = data.size / UUID_128_LEN
        return "$count UUIDs (128-bit)"
    }
}
