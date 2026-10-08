package io.blueeye.core.decoders.parser.samsung

import io.blueeye.core.model.DeviceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conservative Samsung SmartTag parser for manufacturer data (0x0075).
 *
 * Frame byte 0x42 is shared by the wider Samsung Offline Finding ecosystem and is not a
 * unique SmartTag signature. A SmartTag classification requires corroborating model/name evidence.
 */
@Singleton
class SmartTagParser
@Inject
constructor() {
    fun parse(
        data: ByteArray,
        advertisedName: String? = null,
    ): SamsungDeviceData? {
        val normalizedName = advertisedName.orEmpty().lowercase()
        val isKnownSmartTagName =
            SMART_TAG_NAME_MARKERS.any(normalizedName::contains)
        val isSmartTagPacket =
            data.size >= MIN_SMART_TAG_PAYLOAD_SIZE &&
                data.firstOrNull()?.toInt()?.and(BYTE_MASK) == TYPE_SMART_THINGS_FIND &&
                isKnownSmartTagName

        return if (isSmartTagPacket) {
            SamsungDeviceData(
                deviceModel = "Samsung SmartTag",
                deviceType = DeviceType.TAG,
                isOfflineFinding = true,
                isSmartTag = true,
                smartTagId = data.copyOfRange(TAG_ID_START, TAG_ID_END).joinToString("") { "%02X".format(it) },
            )
        } else {
            null
        }
    }

    private companion object {
        const val TYPE_SMART_THINGS_FIND = 0x42
        const val BYTE_MASK = 0xFF
        const val MIN_SMART_TAG_PAYLOAD_SIZE = 12
        const val TAG_ID_START = 4
        const val TAG_ID_END = 12
        val SMART_TAG_NAME_MARKERS = listOf("smarttag", "ei-t5300", "ei-t7300", "ei-t5600")
    }
}
