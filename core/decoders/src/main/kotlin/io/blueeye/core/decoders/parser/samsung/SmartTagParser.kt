package io.blueeye.core.decoders.parser.samsung

import io.blueeye.core.model.DeviceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for Samsung SmartTag (Manufacturer ID 0x0075).
 * Based on research into Samsung SmartThings Find / Offline Finding protocol.
 *
 * Typical Structure:
 * Type: 0x42 (often used for SmartThings / Find)
 * Payload contains obscure/encrypted data, but presence indicates a SmartTag.
 */
@Singleton
class SmartTagParser
@Inject
constructor() {
    companion object {
        // Byte following 0x0075 that often indicates SmartThings/Find network
        const val TYPE_SMART_THINGS_FIND = 0x42

        // Known signatures for SmartTag models (heuristics from sniffing)
        // SmartTag (EI-T5300)
        // SmartTag+ (EI-T7300) with UWB
    }

    fun parse(data: ByteArray): SamsungDeviceData? {
        if (data.isEmpty()) return null

        // Check for SmartThings Find packet type
        // Note: data here is the payload AFTER 0x0075
        // The first byte often indicates the subtype/version logic

        // Heuristic detection based on packet length and some structure
        val isSmartTagPacket = data.size >= 8 && data[0].toInt() and 0xFF == TYPE_SMART_THINGS_FIND

        if (isSmartTagPacket) {
            // Encrypted identification data usually follows
            // We can extract a snippet as a "Tag ID" for tracking
            val tagId = if (data.size >= 12) {
                data.copyOfRange(4, 12).joinToString("") { "%02X".format(it) }
            } else {
                data.joinToString("") { "%02X".format(it) }
            }

            return SamsungDeviceData(
                deviceModel = "Samsung SmartTag",
                deviceType = DeviceType.TAG,
                isOfflineFinding = true,
                isSmartTag = true,
                smartTagId = tagId
            )
        }

        return null
    }
}
