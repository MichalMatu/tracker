package io.blueeye.core.scanner.analysis.parsers

import io.blueeye.core.scanner.analysis.BleAdTypes
import io.blueeye.core.scanner.analysis.BleBinaryConstants
import io.blueeye.core.scanner.analysis.BlePacketConstants

/**
 * Parser for common BLE AD Types (Flags, Role, Conn Interval, Addresses).
 */
object BleCommonDataParser {

    fun parseFlags(data: ByteArray): String {
        if (data.isEmpty()) return "Empty Flags"
        val flag = data[0].toInt()
        val desc = mutableListOf<String>()
        val c = BlePacketConstants
        if ((flag and c.FLAG_LE_LIMITED_DISC) != 0) desc.add("LE Limited Discoverable Mode")
        if ((flag and c.FLAG_LE_GENERAL_DISC) != 0) desc.add("LE General Discoverable Mode")
        if ((flag and c.FLAG_BR_EDR_NOT_SUPPORTED) != 0) desc.add("BR/EDR Not Supported")
        if ((flag and c.FLAG_SIMULTANEOUS_LE_BR_CONTROLLER) != 0) {
            desc.add("Simultaneous LE and BR/EDR (Controller)")
        }
        if ((flag and c.FLAG_SIMULTANEOUS_LE_BR_HOST) != 0) {
            desc.add("Simultaneous LE and BR/EDR (Host)")
        }
        return if (desc.isEmpty()) "No Flags Set (0x%02X)".format(flag) else desc.joinToString("\n  ")
    }

    fun parseOobFlags(data: ByteArray): String {
        if (data.isEmpty()) return "Empty OOB Flags"
        val flag = data[0].toInt()
        val desc = mutableListOf<String>()
        val c = BlePacketConstants

        val oobPresent = (flag and c.OOB_FLAG_DATA_PRESENT) != 0
        desc.add("OOB Data Present: $oobPresent")

        if ((flag and c.OOB_FLAG_LE_SUPPORTED_HOST) != 0) desc.add("LE Supported (Host)")
        if ((flag and c.OOB_FLAG_SIMULTANEOUS_LE_BR_HOST) != 0) {
            desc.add("Simultaneous LE and BR/EDR (Host)")
        }

        val addressType = if ((flag and c.OOB_FLAG_RANDOM_ADDRESS) != 0) "Random" else "Public"
        desc.add("Address Type: $addressType")

        return desc.joinToString("\n  ")
    }

    fun parseLeRole(data: ByteArray): String {
        if (data.isEmpty()) return "Empty"
        val c = BlePacketConstants
        return when (data[0].toInt() and BleBinaryConstants.MASK_BYTE) {
            c.ROLE_PERIPHERAL_ONLY -> "Peripheral Only"
            c.ROLE_CENTRAL_ONLY -> "Central Only"
            c.ROLE_PERIPHERAL_PREFERRED -> "Peripheral Preferred"
            c.ROLE_CENTRAL_PREFERRED -> "Central Preferred"
            else -> "Unknown (0x%02X)".format(data[0])
        }
    }

    fun parseConnectionInterval(data: ByteArray): String {
        if (data.size < BlePacketConstants.CONN_INT_LEN) return "Invalid Length"
        val mask = BleBinaryConstants.MASK_BYTE
        val shift8 = BleBinaryConstants.SHIFT_8
        val ofs1 = BleBinaryConstants.OFS_1
        val ofs2 = BleBinaryConstants.OFS_2
        val ofs3 = BleBinaryConstants.OFS_3
        val min = (data[0].toInt() and mask) or (data[ofs1].toInt() and mask shl shift8)
        val max = (data[ofs2].toInt() and mask) or (data[ofs3].toInt() and mask shl shift8)
        val unit = BlePacketConstants.CONN_INTERVAL_UNIT
        return "Min: ${min * unit}ms, Max: ${max * unit}ms"
    }

    fun parseTargetAddress(data: ByteArray, type: Int): String {
        val typeName = if (type == BleAdTypes.PUBLIC_TARGET_ADDR) "Public" else "Random"
        if (data.size < BlePacketConstants.ADDR_LEN) return "Invalid"
        val addr = data.take(BlePacketConstants.ADDR_LEN).reversed().joinToString(":") { "%02X".format(it) }
        return "$typeName Address: $addr"
    }

    fun parseAdvertisingInterval(data: ByteArray): String {
        if (data.size < BlePacketConstants.ADV_INT_LEN) return "Invalid"
        val mask = BleBinaryConstants.MASK_BYTE
        val ofs1 = BleBinaryConstants.OFS_1
        val interval = (data[ofs1].toInt() and mask shl BleBinaryConstants.SHIFT_8) or (data[0].toInt() and mask)
        return "%.2f ms".format(interval * BlePacketConstants.ADV_INTERVAL_UNIT)
    }

    fun parseLeDeviceAddress(data: ByteArray): String {
        if (data.size < BlePacketConstants.LE_ADDR_LEN) return "Invalid"
        val addressType = if (data[BlePacketConstants.OFS_ADDR_TYPE].toInt() == 0) "Public" else "Random"
        val addr = data.take(BlePacketConstants.ADDR_LEN).reversed().joinToString(":") { "%02X".format(it) }
        return "$addr ($addressType)"
    }

    fun parseUuids(type: Int, data: ByteArray): String = when (type) {
        BleAdTypes.UUID16_INCOMPLETE, BleAdTypes.UUID16_COMPLETE,
        BleAdTypes.SOLICITATION_UUID16 -> BleUuidParser.parse16(data)
        BleAdTypes.UUID32_INCOMPLETE, BleAdTypes.UUID32_COMPLETE,
        BleAdTypes.SOLICITATION_UUID32 -> BleUuidParser.parse32(data)
        else -> BleUuidParser.parse128(data)
    }
}
