package io.blueeye.core.scanner.model

/**
 * Encapsulates all data derived from a low-level BLE scan result.
 * Used to pass data between Scanner and Handlers without long parameter lists.
 */
data class BleScanResultData(
    val mac: String,
    val rssi: Int,
    val timestamp: Long,
    val technology: String,
    val name: String? = null,
    val manufacturerId: Int? = null,
    val manufacturerData: ByteArray? = null,
    val manufacturerDataById: Map<Int, ByteArray> = emptyMap(),
    val serviceUuids: List<String> = emptyList(),
    val serviceDataByUuid: Map<String, ByteArray> = emptyMap(),
    val appearance: Int? = null,
    val txPower: Int? = null,
    val isConnectable: Boolean = false,
    val primaryPhy: Int? = null,
    val secondaryPhy: Int? = null,
    val rawData: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BleScanResultData) return false

        return matchPrimitives(other) && matchArrays(other) && matchByteArrayMaps(other)
    }

    private fun matchPrimitives(other: BleScanResultData): Boolean {
        return mac == other.mac &&
            rssi == other.rssi &&
            timestamp == other.timestamp &&
            technology == other.technology &&
            name == other.name &&
            manufacturerId == other.manufacturerId &&
            serviceUuids == other.serviceUuids &&
            appearance == other.appearance &&
            txPower == other.txPower &&
            isConnectable == other.isConnectable &&
            primaryPhy == other.primaryPhy &&
            secondaryPhy == other.secondaryPhy
    }

    private fun matchArrays(other: BleScanResultData): Boolean {
        return (manufacturerData contentEquals other.manufacturerData) &&
            (rawData contentEquals other.rawData)
    }

    private fun matchByteArrayMaps(other: BleScanResultData): Boolean =
        manufacturerDataById.contentEquals(other.manufacturerDataById) &&
            serviceDataByUuid.contentEquals(other.serviceDataByUuid)

    override fun hashCode(): Int {
        var result = mac.hashCode()
        result = HASH_FACTOR * result + rssi
        result = HASH_FACTOR * result + timestamp.hashCode()
        result = HASH_FACTOR * result + technology.hashCode()
        result = HASH_FACTOR * result + (name?.hashCode() ?: 0)
        result = HASH_FACTOR * result + (manufacturerId ?: 0)
        result = HASH_FACTOR * result + (manufacturerData?.contentHashCode() ?: 0)
        result = HASH_FACTOR * result + manufacturerDataById.contentHashCodeByKey()
        result = HASH_FACTOR * result + serviceUuids.hashCode()
        result = HASH_FACTOR * result + serviceDataByUuid.contentHashCodeByKey()
        result = HASH_FACTOR * result + (appearance ?: 0)
        result = HASH_FACTOR * result + (txPower ?: 0)
        result = HASH_FACTOR * result + isConnectable.hashCode()
        result = HASH_FACTOR * result + (primaryPhy ?: 0)
        result = HASH_FACTOR * result + (secondaryPhy ?: 0)
        result = HASH_FACTOR * result + (rawData?.contentHashCode() ?: 0)
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
