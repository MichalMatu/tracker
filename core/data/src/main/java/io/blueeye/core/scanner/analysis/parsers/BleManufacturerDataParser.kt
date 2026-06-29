package io.blueeye.core.scanner.analysis.parsers

import io.blueeye.core.scanner.analysis.BleBinaryConstants
import io.blueeye.core.scanner.analysis.BleCompanyIds

/**
 * Parser for BLE Manufacturer Specific Data (0xFF).
 */
object BleManufacturerDataParser {
    private const val IBEACON_TYPE = 0x02
    private const val IBEACON_LEN = 0x15
    private const val IBEACON_MIN_SIZE = 23

    private const val OFS_TYPE = 0
    private const val OFS_LEN = 1
    private const val OFS_UUID_START = 2
    private const val OFS_UUID_LEN = 16
    private const val OFS_MAJOR = 18
    private const val OFS_MINOR = 20
    private const val OFS_TX_POWER = 22

    private val UUID_SEG_1 = 0..3
    private val UUID_SEG_2 = 4..5
    private val UUID_SEG_3 = 6..7
    private val UUID_SEG_4 = 8..9
    private val UUID_SEG_5 = 10..15

    fun parse(data: ByteArray): String {
        if (data.size < 2) return "Invalid"
        val id = (data[1].toInt() and BleBinaryConstants.MASK_BYTE shl BleBinaryConstants.SHIFT_8) or
            (data[0].toInt() and BleBinaryConstants.MASK_BYTE)
        val rest = data.copyOfRange(2, data.size)

        var details = "Company ID: 0x%04X, Data: ".format(id) + rest.joinToString("") { "%02X".format(it) }

        // Apple Parsers (iBeacon)
        val appleInfo = parseIBeacon(id, rest)
        if (appleInfo != null) {
            details += "\n    └─ " + appleInfo.replace("\n", "\n       ")
        } else if (id == BleCompanyIds.APPLE) {
            details += "\n    └─ Apple Manufacturer Data"
        }

        // Garmin Decoder
        if (id == io.blueeye.core.decoders.GarminDecoder.MANUFACTURER_ID_GARMIN) {
            val garminInfo = io.blueeye.core.decoders.GarminDecoder.decode(rest)
            if (garminInfo != null) {
                val summary = io.blueeye.core.decoders.GarminDecoder.getSummary(garminInfo)
                details += "\n    └─ Garmin: $summary"
            }
        }

        return details
    }

    private fun parseIBeacon(companyId: Int, data: ByteArray): String? {
        if (companyId != BleCompanyIds.APPLE) return null

        val isBeaconType = data.size >= IBEACON_MIN_SIZE &&
            data[OFS_TYPE].toInt() and BleBinaryConstants.MASK_BYTE == IBEACON_TYPE &&
            data[OFS_LEN].toInt() and BleBinaryConstants.MASK_BYTE == IBEACON_LEN

        return if (isBeaconType) {
            val uuidBytes = data.sliceArray(OFS_UUID_START until OFS_UUID_START + OFS_UUID_LEN)
            val uuid = formatIBeaconUuid(uuidBytes)

            val mask = BleBinaryConstants.MASK_BYTE
            val major = ((data[OFS_MAJOR].toInt() and mask) shl BleBinaryConstants.SHIFT_8) or
                (data[OFS_MAJOR + 1].toInt() and mask)
            val minor = ((data[OFS_MINOR].toInt() and mask) shl BleBinaryConstants.SHIFT_8) or
                (data[OFS_MINOR + 1].toInt() and mask)
            val txPower = data[OFS_TX_POWER].toInt()

            "iBeacon: UUID=$uuid\n       Major=$major, Minor=$minor, TxPower@1m=${txPower}dBm"
        } else {
            null
        }
    }

    private fun formatIBeaconUuid(bytes: ByteArray): String = buildString {
        append(bytes.sliceArray(UUID_SEG_1).joinToString("") { "%02X".format(it) })
        append("-")
        append(bytes.sliceArray(UUID_SEG_2).joinToString("") { "%02X".format(it) })
        append("-")
        append(bytes.sliceArray(UUID_SEG_3).joinToString("") { "%02X".format(it) })
        append("-")
        append(bytes.sliceArray(UUID_SEG_4).joinToString("") { "%02X".format(it) })
        append("-")
        append(bytes.sliceArray(UUID_SEG_5).joinToString("") { "%02X".format(it) })
    }
}
