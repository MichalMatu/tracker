package io.blueeye.core.decoders.parser.samsung

import io.blueeye.core.model.DeviceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for Samsung SmartThings Find / Offline Finding manufacturer data (0x0075).
 *
 * Type 0x42 is used by the wider Offline Finding ecosystem and is not sufficient on its own to
 * identify a SmartTag. TVs and other Samsung devices can emit the same family of payloads.
 */
@Singleton
class SmartTagParser
@Inject
constructor() {
    companion object {
        // Byte following 0x0075 that often indicates SmartThings/Find network
        const val TYPE_SMART_THINGS_FIND = 0x42
    }

    fun parse(data: ByteArray): SamsungDeviceData? {
        if (data.isEmpty()) return null

        val isOfflineFindingPacket =
            data.size >= 8 && data[0].toInt() and 0xFF == TYPE_SMART_THINGS_FIND

        if (isOfflineFindingPacket) {
            return SamsungDeviceData(
                deviceModel = "Samsung Offline Finding",
                deviceType = DeviceType.UNKNOWN,
                isOfflineFinding = true,
                isSmartTag = false,
                smartTagId = null,
            )
        }

        return null
    }
}
