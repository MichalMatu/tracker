package io.blueeye.core.decoders.parser.microsoft

import io.blueeye.core.model.DeviceType

/**
 * Data model for parsed Microsoft device data.
 */
data class MicrosoftDeviceData(
    val deviceModel: String? = null,
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    val isSwiftPair: Boolean = false,
    val sessionId: Int? = null, // from CDP
    val deviceHash: String? = null, // from Swift Pair or CDP
    // Swift Pair specific fields
    val scenarioId: Int? = null,
    val classOfDevice: Int? = null,
    val brEdrAddress: String? = null,
    val displayName: String? = null,
    // CDP specific fields
    val cdpDeviceType: Int? = null,
    val cdpSalt: String? = null,
)
