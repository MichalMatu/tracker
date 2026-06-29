package io.blueeye.core.data.classifier.vendor

import io.blueeye.core.model.DeviceType

data class VendorScanResult(
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    val modelName: String? = null,
    val batteryLevel: Int? = null,
    val isConnectable: Boolean? = null,
    val extraInfo: String? = null, // For specific flags
)

data class VendorScanInput(
    val manufacturerRecords: Map<Int, ByteArray>,
    val serviceUuids: List<String>,
) {
    fun hasManufacturer(manufacturerId: Int): Boolean = manufacturerRecords.containsKey(manufacturerId)

    fun manufacturerData(manufacturerId: Int): ByteArray? = manufacturerRecords[manufacturerId]

    fun hasServiceUuid(shortOrFullUuid: String): Boolean =
        serviceUuids.any { uuid -> uuid.contains(shortOrFullUuid, ignoreCase = true) }
}

interface VendorStrategy {
    fun canHandle(input: VendorScanInput): Boolean

    fun decode(input: VendorScanInput): VendorScanResult
}
