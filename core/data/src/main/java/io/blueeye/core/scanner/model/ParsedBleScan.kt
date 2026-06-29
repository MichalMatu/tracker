package io.blueeye.core.scanner.model

data class ParsedBleScan(
    val mac: String,
    val rssi: Int,
    val timestamp: Long,
    val technology: String,
    val name: String?,
    val manufacturerId: Int?,
    val manufacturerData: ByteArray?,
    val serviceUuids: List<String>,
    val appearance: Int?,
)
