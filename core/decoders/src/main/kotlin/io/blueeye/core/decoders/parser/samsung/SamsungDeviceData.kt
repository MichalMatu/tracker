package io.blueeye.core.decoders.parser.samsung

/** Data model for parsed Samsung device data. */
data class SamsungDeviceData(
    val deviceModel: String? = null,
    val deviceType: io.blueeye.core.model.DeviceType =
        io.blueeye.core.model.DeviceType.UNKNOWN,
    val isOfflineFinding: Boolean = false,
    val privacyId: ByteArray? = null, // From SmartThings Find
    val batteryLeft: Int? = null,
    val batteryRight: Int? = null,
    val batteryCase: Int? = null,
    val isQuickShareVisible: Boolean = false,
    val isSmartTag: Boolean = false,
    val smartTagId: String? = null,
)
