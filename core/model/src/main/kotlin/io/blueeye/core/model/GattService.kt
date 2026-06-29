package io.blueeye.core.model

data class GattService(
    val uuid: String,
    val name: String,
    val characteristics: List<GattCharacteristic>,
)

data class GattCharacteristic(
    val uuid: String,
    val name: String,
    val properties: List<String>,
    val value: String? = null,
)
