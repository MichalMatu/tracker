package io.blueeye.core.scanner.analysis.parsers

import io.blueeye.core.scanner.analysis.BleBinaryConstants
import io.blueeye.core.scanner.analysis.BleServiceUuids

/**
 * Parser for BLE Service Data AD Types.
 */
object BleServiceDataParser {
    private const val UUID_16_LEN = 2
    private const val UUID_32_LEN = 4
    private const val UUID_128_LEN = 16

    fun parse16(data: ByteArray): String {
        if (data.size < UUID_16_LEN) return "Invalid"
        val uuid = (data[1].toInt() and BleBinaryConstants.MASK_BYTE shl BleBinaryConstants.SHIFT_8) or
            (data[0].toInt() and BleBinaryConstants.MASK_BYTE)
        val rest = data.copyOfRange(UUID_16_LEN, data.size)
        var result = "UUID: 0x%04X, Data: ".format(uuid) + rest.joinToString("") { "%02X".format(it) }

        // Google Fast Pair decoder integration
        if (uuid == io.blueeye.core.decoders.GoogleFastPairDecoder.SERVICE_UUID) {
            val fastPairInfo = io.blueeye.core.decoders.GoogleFastPairDecoder.decode(rest)
            if (fastPairInfo != null) {
                result += "\n    └─ Fast Pair: " +
                    io.blueeye.core.decoders.GoogleFastPairDecoder.getSummary(fastPairInfo)
            }
        }

        // Eddystone decoder
        if (uuid == BleServiceUuids.EDDYSTONE) {
            val eddystoneInfo = EddystoneParser.parse(uuid, rest)
            if (eddystoneInfo != null) {
                val infoFormatted = eddystoneInfo.replace("\n", "\n       ")
                result += "\n    └─ $infoFormatted"
            }
        }

        return result
    }

    fun parse32(data: ByteArray): String {
        if (data.size < UUID_32_LEN) return "Invalid"
        return "UUID32: " + data.copyOfRange(0, UUID_32_LEN).joinToString("") { "%02X".format(it) } +
            ", Data: " + data.copyOfRange(UUID_32_LEN, data.size).joinToString("") { "%02X".format(it) }
    }

    fun parse128(data: ByteArray): String {
        if (data.size < UUID_128_LEN) return "Invalid"
        return "UUID128: " + data.copyOfRange(0, UUID_128_LEN).joinToString("") { "%02X".format(it) } +
            ", Data: " + data.copyOfRange(UUID_128_LEN, data.size).joinToString("") { "%02X".format(it) }
    }
}
