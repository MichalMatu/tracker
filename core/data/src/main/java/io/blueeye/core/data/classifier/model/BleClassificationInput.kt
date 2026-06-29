package io.blueeye.core.data.classifier.model

/**
 * Encapsulates input data for BLE device classification.
 */
data class BleClassificationInput(
    val manufacturerRecords: Map<Int, ByteArray>,
    val serviceUuids: List<String>?,
    val serviceDataByUuid: Map<String, ByteArray>,
    val appearance: Int?,
    val deviceName: String?,
    val vendorName: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BleClassificationInput

        if (!manufacturerRecords.contentEquals(other.manufacturerRecords)) return false
        if (serviceUuids != other.serviceUuids) return false
        if (!serviceDataByUuid.contentEquals(other.serviceDataByUuid)) return false
        if (appearance != other.appearance) return false
        if (deviceName != other.deviceName) return false
        if (vendorName != other.vendorName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = manufacturerRecords.contentHashCodeByKey()
        result = 31 * result + (serviceUuids?.hashCode() ?: 0)
        result = 31 * result + serviceDataByUuid.contentHashCodeByKey()
        result = 31 * result + (appearance ?: 0)
        result = 31 * result + (deviceName?.hashCode() ?: 0)
        result = 31 * result + (vendorName?.hashCode() ?: 0)
        return result
    }

    private fun <K> Map<K, ByteArray>.contentEquals(other: Map<K, ByteArray>): Boolean =
        keys == other.keys &&
            all { (key, data) ->
                data contentEquals other[key]
            }

    private fun <K : Comparable<K>> Map<K, ByteArray>.contentHashCodeByKey(): Int =
        entries.sortedBy { it.key }.fold(0) { acc, entry ->
            HASH_FACTOR * (HASH_FACTOR * acc + entry.key.hashCode()) + entry.value.contentHashCode()
        }

    private companion object {
        private const val HASH_FACTOR = 31
    }
}
