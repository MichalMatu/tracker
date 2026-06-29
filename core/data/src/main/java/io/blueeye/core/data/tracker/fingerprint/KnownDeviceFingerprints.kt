package io.blueeye.core.data.tracker.fingerprint

import io.blueeye.core.scanner.analysis.BleAdTypes
import io.blueeye.core.scanner.analysis.BleBinaryConstants
import java.util.Locale
import io.blueeye.core.data.tracker.fingerprint.FingerprintDefinitions as Defs

/**
 * Registry of known BLE device fingerprints for popular ecosystems (Apple, Google, etc.).
 * Used for precise device identification and spoofing detection.
 *
 * Based on research from SpeastTV/BLE-Payloads and other sources.
 */
object KnownDeviceFingerprints {
    private const val UUID_128_LEN = 16

    /**
     * Attempts to identify a device directly from its Raw Scan Record bytes.
     * Parses the AD structures to extract Service Data, Manufacturer Data, and Service Lists.
     */
    @Suppress("CyclomaticComplexMethod")
    fun identify(scanRecord: ByteArray?): String? {
        if (scanRecord == null || scanRecord.isEmpty()) return null

        val serviceDataMap = mutableMapOf<String, ByteArray>()
        val manufacturerDataMap = mutableMapOf<Int, ByteArray>()
        val serviceUuids = mutableSetOf<String>()
        var detectedName: String? = null

        var index = 0
        @Suppress("LoopWithTooManyJumpStatements")
        while (index < scanRecord.size) {
            val length = scanRecord[index].toInt() and BleBinaryConstants.MASK_BYTE
            if (length == 0) break // End of significant data

            val typeIndex = index + 1
            if (typeIndex >= scanRecord.size) break

            val type = scanRecord[typeIndex].toInt() and BleBinaryConstants.MASK_BYTE
            val dataLength = length - 1
            val dataStartIndex = index + 2

            if (dataStartIndex + dataLength > scanRecord.size) break // Malformed or truncated

            val content = scanRecord.copyOfRange(dataStartIndex, dataStartIndex + dataLength)
            parseRecordChunk(type, content, serviceUuids, serviceDataMap, manufacturerDataMap)

            // NAME handling moved to parseRecordChunk technically, but we need to update var
            if (type == BleAdTypes.NAME_SHORT || type == BleAdTypes.NAME_COMPLETE) {
                detectedName = String(content)
            }

            index += length + 1
        }

        return checkDirectMatches(serviceUuids, detectedName) ?: identify(serviceDataMap, manufacturerDataMap)
    }

    private fun checkDirectMatches(serviceUuids: Set<String>, name: String?): String? {
        return when {
            serviceUuids.containsUuid(Defs.TILE_SERVICE_UUID) -> "Tile Device"
            serviceUuids.any { it.contains(Defs.FITBIT_LE_SUFFIX) } -> "Fitbit Device"
            serviceUuids.any { it.contains(Defs.TESLA_LE_SUFFIX) } -> "Tesla Vehicle"
            serviceUuids.any { it.contains(Defs.CHIPOLO_LE_SUFFIX) } -> "Chipolo Tracker"
            serviceUuids.any { it.contains(Defs.TI_SENSORTAG_LE_SUFFIX) } -> "TI SensorTag"
            name == "CC2650 SensorTag" || name == "SensorTag 2.0" -> "TI SensorTag"
            else -> null
        }
    }

    private fun Set<String>.containsUuid(uuid: String): Boolean =
        any { it.equals(uuid, ignoreCase = true) || it.contains(uuid, ignoreCase = true) }

    /**
     * Attempts to identify a device based on its service data or manufacturer data.
     *
     * @param serviceDataMap Map of Service UUID (String) to Data (ByteArray)
     * @param manufacturerSpecificData Map of Manufacturer ID (Int) to Data (ByteArray)
     * @return Detected Model Name or null
     */
    fun identify(
        serviceDataMap: Map<String, ByteArray>?,
        manufacturerSpecificData: Map<Int, ByteArray>?
    ): String? {
        return checkServiceData(serviceDataMap) ?: checkManufacturerData(manufacturerSpecificData)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun checkServiceData(serviceDataMap: Map<String, ByteArray>?): String? {
        return FingerprintMatcher.checkServiceData(serviceDataMap)
    }

    private fun checkManufacturerData(manufacturerSpecificData: Map<Int, ByteArray>?): String? {
        if (manufacturerSpecificData == null) return null

        var result: String? = null

        // Apple
        val appleData = manufacturerSpecificData[Defs.APPLE_MANUFACTURER_ID]
        if (appleData != null) {
            val hexData = appleData.toHexString().lowercase(Locale.ROOT)
            result = Defs.APPLE_MODELS.entries.firstOrNull { (prefix, _) ->
                hexData.startsWith(prefix.lowercase(Locale.ROOT))
            }?.value
        }

        // Simple ID matches
        if (result == null) {
            result = when {
                manufacturerSpecificData.containsKey(Defs.SAMSUNG_MANUFACTURER_ID) -> "Samsung Device"
                manufacturerSpecificData.containsKey(Defs.PARTICLE_MANUFACTURER_ID) -> "Particle Device"
                manufacturerSpecificData.containsKey(Defs.AMAZON_MANUFACTURER_ID) -> "Amazon Sidewalk Device"
                else -> null
            }
        }

        // AltBeacon
        if (result == null) {
            val isAltBeacon = manufacturerSpecificData.values.any { data ->
                if (data.size >= 2) {
                    val highByte = (data[0].toInt() and BleBinaryConstants.MASK_BYTE) shl
                        BleBinaryConstants.SHIFT_8
                    val lowByte = data[1].toInt() and BleBinaryConstants.MASK_BYTE
                    val preamble = highByte or lowByte
                    preamble == Defs.ALTBEACON_PREAMBLE
                } else {
                    false
                }
            }
            if (isAltBeacon) result = "AltBeacon"
        }

        return result
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    private fun parseRecordChunk(
        type: Int,
        content: ByteArray,
        serviceUuids: MutableSet<String>,
        serviceDataMap: MutableMap<String, ByteArray>,
        manufacturerDataMap: MutableMap<Int, ByteArray>
    ) {
        when (type) {
            BleAdTypes.UUID16_INCOMPLETE, BleAdTypes.UUID16_COMPLETE -> {
                var i = 0
                while (i + 2 <= content.size) {
                    serviceUuids.add("%02X%02X".format(content[i + 1], content[i]))
                    i += 2
                }
            }
            BleAdTypes.UUID128_INCOMPLETE, BleAdTypes.UUID128_COMPLETE -> {
                var i = 0
                while (i + UUID_128_LEN <= content.size) {
                    serviceUuids.add(content.copyOfRange(i, i + UUID_128_LEN).toHexString())
                    i += UUID_128_LEN
                }
            }
            BleAdTypes.SERVICE_DATA_16 -> if (content.size >= 2) {
                val uuid = "%02X%02X".format(content[1], content[0])
                serviceDataMap[uuid] = content.copyOfRange(2, content.size)
            }
            BleAdTypes.MANUFACTURER_SPECIFIC -> if (content.size >= 2) {
                val partialMfgId = (content[1].toInt() and BleBinaryConstants.MASK_BYTE) shl
                    BleBinaryConstants.SHIFT_8
                val mfgId = partialMfgId or (content[0].toInt() and BleBinaryConstants.MASK_BYTE)
                manufacturerDataMap[mfgId] = content.copyOfRange(2, content.size)
            }
        }
    }
}
