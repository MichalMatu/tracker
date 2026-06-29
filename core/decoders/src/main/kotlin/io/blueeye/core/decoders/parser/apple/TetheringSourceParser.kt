package io.blueeye.core.decoders.parser.apple

import javax.inject.Inject
import javax.inject.Singleton

/** Parser for Apple Instant Hotspot / Tethering Source (Type 0x0E). Based on FuriousMAC. */
@Singleton
class TetheringSourceParser
@Inject
constructor() {
    fun parse(data: ByteArray): AppleDeviceData? {
        if (data.isEmpty()) return null

        // FuriousMAC: can leak cellular service type/signal/battery. Without stable offsets we keep raw.
        return AppleDeviceData(
            deviceModel = "Apple Instant Hotspot",
            tetheringType = 0x0E,
            tetheringPayload = data,
        )
    }
}
