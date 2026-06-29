package io.blueeye.core.data.evidence

/**
 * Parses persisted BLE advertising bytes into passive evidence inputs.
 *
 * Android's ScanRecord exposes only convenience views for some fields. The raw
 * advertising payload can still contain additional manufacturer records or
 * service-data UUIDs that should remain visible as evidence.
 */
internal object AdvertisementEvidenceParser {
    fun parse(rawHex: String?): AdvertisementEvidence {
        val bytes = rawHex.toBytesOrEmpty()
        val appearances = mutableListOf<Int>()
        val manufacturerIds = mutableListOf<Int>()
        val serviceUuids = mutableListOf<String>()

        var index = 0
        while (index < bytes.size) {
            val record = bytes.readRecord(index)
            if (record == null) {
                index = bytes.size
            } else {
                parseRecord(
                    type = record.type,
                    data = record.data,
                    appearances = appearances,
                    manufacturerIds = manufacturerIds,
                    serviceUuids = serviceUuids,
                )
                index = record.nextIndex
            }
        }

        return AdvertisementEvidence(
            appearance = appearances.firstOrNull(),
            manufacturerIds = manufacturerIds.distinct().sorted(),
            serviceUuids = serviceUuids.distinct().sorted(),
        )
    }

    private fun parseRecord(
        type: Int,
        data: ByteArray,
        appearances: MutableList<Int>,
        manufacturerIds: MutableList<Int>,
        serviceUuids: MutableList<String>,
    ) {
        when (type) {
            AD_TYPE_APPEARANCE -> parseAppearance(data)?.let(appearances::add)
            AD_TYPE_MANUFACTURER_DATA -> parseManufacturerId(data)?.let(manufacturerIds::add)
            AD_TYPE_UUID16_INCOMPLETE,
            AD_TYPE_UUID16_COMPLETE,
            -> serviceUuids += data.chunkedUuids(UUID16_SIZE, ::parseUuid16)
            AD_TYPE_UUID32_INCOMPLETE,
            AD_TYPE_UUID32_COMPLETE,
            -> serviceUuids += data.chunkedUuids(UUID32_SIZE, ::parseUuid32)
            AD_TYPE_UUID128_INCOMPLETE,
            AD_TYPE_UUID128_COMPLETE,
            -> serviceUuids += data.chunkedUuids(UUID128_SIZE, ::parseUuid128)
            AD_TYPE_SERVICE_DATA_UUID16 -> parseUuid16(data, 0)?.let(serviceUuids::add)
            AD_TYPE_SERVICE_DATA_UUID32 -> parseUuid32(data, 0)?.let(serviceUuids::add)
            AD_TYPE_SERVICE_DATA_UUID128 -> parseUuid128(data, 0)?.let(serviceUuids::add)
        }
    }

    private fun parseManufacturerId(data: ByteArray): Int? {
        if (data.size < COMPANY_ID_SIZE) return null
        return data[0].unsigned() or (data[1].unsigned() shl BYTE_BITS)
    }

    private fun parseAppearance(data: ByteArray): Int? {
        if (data.size < APPEARANCE_SIZE) return null
        return data[0].unsigned() or (data[1].unsigned() shl BYTE_BITS)
    }

    private fun ByteArray.chunkedUuids(
        chunkSize: Int,
        parser: (ByteArray, Int) -> String?,
    ): List<String> {
        val result = mutableListOf<String>()
        var offset = 0
        while (offset + chunkSize <= size) {
            parser(this, offset)?.let(result::add)
            offset += chunkSize
        }
        return result
    }

    private fun parseUuid16(data: ByteArray, offset: Int): String? {
        if (offset + UUID16_SIZE > data.size) return null
        val value = data[offset].unsigned() or (data[offset + 1].unsigned() shl BYTE_BITS)
        return value.toBluetoothUuid()
    }

    private fun parseUuid32(data: ByteArray, offset: Int): String? {
        if (offset + UUID32_SIZE > data.size) return null
        val value =
            data[offset].unsigned() or
                (data[offset + UUID32_BYTE_1].unsigned() shl BYTE_BITS) or
                (data[offset + UUID32_BYTE_2].unsigned() shl TWO_BYTES_BITS) or
                (data[offset + UUID32_BYTE_3].unsigned() shl THREE_BYTES_BITS)
        return "%08x-0000-1000-8000-00805f9b34fb".format(value)
    }

    private fun parseUuid128(data: ByteArray, offset: Int): String? {
        if (offset + UUID128_SIZE > data.size) return null
        val uuidBytes = data.copyOfRange(offset, offset + UUID128_SIZE).reversedArray()
        val hex = uuidBytes.joinToString("") { "%02x".format(it.unsigned()) }
        return listOf(
            hex.substring(0, UUID128_GROUP_1_END),
            hex.substring(UUID128_GROUP_1_END, UUID128_GROUP_2_END),
            hex.substring(UUID128_GROUP_2_END, UUID128_GROUP_3_END),
            hex.substring(UUID128_GROUP_3_END, UUID128_GROUP_4_END),
            hex.substring(UUID128_GROUP_4_END),
        ).joinToString("-")
    }

    private fun String?.toBytesOrEmpty(): ByteArray {
        val clean = this?.filter { it.digitToIntOrNull(HEX_RADIX) != null }.orEmpty()
        return if (clean.length >= HEX_BYTE_CHARS && clean.length % HEX_BYTE_CHARS == 0) {
            runCatching {
                ByteArray(clean.length / HEX_BYTE_CHARS) { index ->
                    val start = index * HEX_BYTE_CHARS
                    clean.substring(start, start + HEX_BYTE_CHARS).toInt(HEX_RADIX).toByte()
                }
            }.getOrDefault(byteArrayOf())
        } else {
            byteArrayOf()
        }
    }

    private fun ByteArray.readRecord(index: Int): AdvertisementRecord? {
        val length = this[index].unsigned()
        val nextIndex = index + length + AD_LENGTH_SIZE
        return if (length >= AD_TYPE_SIZE && nextIndex <= size) {
            AdvertisementRecord(
                type = this[index + AD_LENGTH_SIZE].unsigned(),
                data = copyOfRange(index + AD_HEADER_SIZE, nextIndex),
                nextIndex = nextIndex,
            )
        } else {
            null
        }
    }

    private class AdvertisementRecord(
        val type: Int,
        val data: ByteArray,
        val nextIndex: Int,
    )

    data class AdvertisementEvidence(
        val appearance: Int? = null,
        val manufacturerIds: List<Int> = emptyList(),
        val serviceUuids: List<String> = emptyList(),
    )

    private const val AD_LENGTH_SIZE = 1
    private const val AD_HEADER_SIZE = 2
    private const val AD_TYPE_SIZE = 1
    private const val COMPANY_ID_SIZE = 2
    private const val APPEARANCE_SIZE = 2
    private const val UUID16_SIZE = 2
    private const val UUID32_SIZE = 4
    private const val UUID128_SIZE = 16
    private const val UUID32_BYTE_1 = 1
    private const val UUID32_BYTE_2 = 2
    private const val UUID32_BYTE_3 = 3
    private const val UUID128_GROUP_1_END = 8
    private const val UUID128_GROUP_2_END = 12
    private const val UUID128_GROUP_3_END = 16
    private const val UUID128_GROUP_4_END = 20
    private const val BYTE_BITS = 8
    private const val TWO_BYTES_BITS = 16
    private const val THREE_BYTES_BITS = 24
    private const val HEX_RADIX = 16
    private const val HEX_BYTE_CHARS = 2
    private const val AD_TYPE_UUID16_INCOMPLETE = 0x02
    private const val AD_TYPE_UUID16_COMPLETE = 0x03
    private const val AD_TYPE_UUID32_INCOMPLETE = 0x04
    private const val AD_TYPE_UUID32_COMPLETE = 0x05
    private const val AD_TYPE_UUID128_INCOMPLETE = 0x06
    private const val AD_TYPE_UUID128_COMPLETE = 0x07
    private const val AD_TYPE_SERVICE_DATA_UUID16 = 0x16
    private const val AD_TYPE_APPEARANCE = 0x19
    private const val AD_TYPE_SERVICE_DATA_UUID32 = 0x20
    private const val AD_TYPE_SERVICE_DATA_UUID128 = 0x21
    private const val AD_TYPE_MANUFACTURER_DATA = 0xFF
}

private fun Int.toBluetoothUuid(): String =
    "0000%04x-0000-1000-8000-00805f9b34fb".format(this)

private fun Byte.unsigned(): Int = java.lang.Byte.toUnsignedInt(this)
