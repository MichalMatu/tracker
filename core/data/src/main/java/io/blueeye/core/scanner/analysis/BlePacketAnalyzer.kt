package io.blueeye.core.scanner.analysis

import io.blueeye.core.scanner.analysis.parsers.BleCommonDataParser
import io.blueeye.core.scanner.analysis.parsers.BleManufacturerDataParser
import io.blueeye.core.scanner.analysis.parsers.BleServiceDataParser
import java.lang.StringBuilder

/**
 * Utility to analyze raw BLE advertising packets (Scan Record).
 */
object BlePacketAnalyzer {

    fun analyze(scanRecord: ByteArray?): String {
        if (scanRecord == null || scanRecord.isEmpty()) return "Empty Record"

        val sb = StringBuilder()
        var index = 0

        try {
            while (index < scanRecord.size && scanRecord[index].toInt() != 0) {
                val length = scanRecord[index].toInt() and BleBinaryConstants.MASK_BYTE
                val nextIndex = index + length + 1

                if (nextIndex > scanRecord.size) {
                    sb.append("Malformed Packet at $index\n")
                    break
                }

                val type = scanRecord[index + 1].toInt() and BleBinaryConstants.MASK_BYTE
                val data = scanRecord.copyOfRange(index + 2, nextIndex)

                sb.append("• ${BleAdTypes.getName(type)} (0x%02X):\n".format(type))
                sb.append("  ${parseContent(type, data)}\n")

                index = nextIndex
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            sb.append("Error: ${e.message}")
        }

        return sb.toString().trim()
    }

    private fun parseContent(type: Int, data: ByteArray): String {
        val c = BlePacketConstants
        return when (type) {
            BleAdTypes.FLAGS -> BleCommonDataParser.parseFlags(data)
            in c.AD_TYPE_STANDARD_MIN..c.AD_TYPE_BLOCK_A_MAX -> parseStandardTypeBlockA(type, data)
            in c.AD_TYPE_BLOCK_B_MIN..c.AD_TYPE_BLOCK_B_MAX -> parseStandardTypeBlockB(type, data)
            BleAdTypes.MANUFACTURER_SPECIFIC -> BleManufacturerDataParser.parse(data)
            else -> "HEX: " + data.joinToString("") { "%02X".format(it) }
        }
    }

    private fun parseStandardTypeBlockA(type: Int, data: ByteArray): String {
        return when (type) {
            in BleAdTypes.UUID16_INCOMPLETE..BleAdTypes.UUID128_COMPLETE,
            BleAdTypes.SOLICITATION_UUID16, BleAdTypes.SOLICITATION_UUID32,
            BleAdTypes.SOLICITATION_UUID128 -> BleCommonDataParser.parseUuids(type, data)
            BleAdTypes.NAME_SHORT, BleAdTypes.NAME_COMPLETE -> String(data, Charsets.UTF_8)
            BleAdTypes.TX_POWER -> if (data.isNotEmpty()) "${data[0].toInt()} dBm" else "Empty"
            BleAdTypes.CLASS_OF_DEVICE -> io.blueeye.core.scanner.analysis.parsers.BleClassOfDeviceParser.parse(data)
            BleAdTypes.SIMPLE_PAIRING_HASH, BleAdTypes.SIMPLE_PAIRING_RANDOMIZER -> parsePairingData(data, type)
            BleAdTypes.DEVICE_ID -> "Device ID: " + data.joinToString("") { "%02X".format(it) }
            BleAdTypes.OOB_FLAGS -> BleCommonDataParser.parseOobFlags(data)
            BleAdTypes.SLAVE_CONN_INTERVAL -> BleCommonDataParser.parseConnectionInterval(data)
            BleAdTypes.SERVICE_DATA_16 -> BleServiceDataParser.parse16(data)
            else -> "HEX: " + data.joinToString("") { "%02X".format(it) }
        }
    }

    private fun parseStandardTypeBlockB(type: Int, data: ByteArray): String {
        val common = BleCommonDataParser
        return when (type) {
            BleAdTypes.PUBLIC_TARGET_ADDR, BleAdTypes.RAND_ADDR_TYPE -> common.parseTargetAddress(data, type)
            BleAdTypes.APPEARANCE -> io.blueeye.core.scanner.analysis.parsers.BleAppearanceParser.parse(data)
            BleAdTypes.ADVERTISING_INTERVAL -> common.parseAdvertisingInterval(data)
            BleAdTypes.LE_DEVICE_ADDR -> common.parseLeDeviceAddress(data)
            BleAdTypes.LE_ROLE -> common.parseLeRole(data)
            BleAdTypes.SERVICE_DATA_32 -> BleServiceDataParser.parse32(data)
            BleAdTypes.SERVICE_DATA_128 -> BleServiceDataParser.parse128(data)
            BleAdTypes.URI -> String(data, Charsets.UTF_8)
            else -> "HEX: " + data.joinToString("") { "%02X".format(it) }
        }
    }

    private fun parsePairingData(data: ByteArray, type: Int): String {
        val name = if (type == BleAdTypes.SIMPLE_PAIRING_HASH) "Hash" else "Rand"
        return "$name: " + data.joinToString("") { "%02X".format(it) }
    }
}
