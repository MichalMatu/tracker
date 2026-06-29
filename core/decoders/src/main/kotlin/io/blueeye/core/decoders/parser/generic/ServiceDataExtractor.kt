package io.blueeye.core.decoders.parser.generic

/**
 * Extracts Service Data AD structures (0x16) from a raw BLE scan record.
 *
 * Android's ScanRecord exposes service UUIDs but not always the raw service data payloads.
 * For protocol fingerprinting (Fast Pair, AMA/Sidewalk, Eddystone, etc.) we parse AD structures
 * directly.
 */
object ServiceDataExtractor {

    /**
     * Returns a map of 16-bit service UUID -> service data payload (bytes after UUID).
     */
    fun extract16(scanRecord: ByteArray?): Map<Int, ByteArray> {
        if (scanRecord == null || scanRecord.isEmpty()) return emptyMap()

        val result = mutableMapOf<Int, ByteArray>()
        var index = 0

        while (index < scanRecord.size) {
            val length = scanRecord[index].toInt() and 0xFF
            if (length == 0) break

            val typeIndex = index + 1
            if (typeIndex >= scanRecord.size) break

            val type = scanRecord[typeIndex].toInt() and 0xFF
            val dataLength = length - 1
            val dataStart = index + 2
            val dataEnd = dataStart + dataLength

            if (dataLength < 0 || dataEnd > scanRecord.size) break

            if (type == 0x16 && dataLength >= 2) {
                val content = scanRecord.copyOfRange(dataStart, dataEnd)

                // UUID is little-endian inside the AD structure
                val uuid16 = ((content[1].toInt() and 0xFF) shl 8) or (content[0].toInt() and 0xFF)
                val payload = if (content.size > 2) content.copyOfRange(2, content.size) else byteArrayOf()

                result[uuid16] = payload
            }

            index += length + 1
        }

        return result
    }
}
