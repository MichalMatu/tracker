package io.blueeye.core.decoders.parser.apple

import javax.inject.Inject
import javax.inject.Singleton

/** Parser for Apple Nearby Action / Wi‑Fi Join (Type 0x0F). Based on FuriousMAC. */
@Singleton
class NearbyActionParser
@Inject
constructor() {
    fun parse(data: ByteArray): AppleDeviceData? {
        if (data.isEmpty()) return null

        // FuriousMAC: Wi‑Fi Join includes first 3 bytes of SHA256(SSID).
        val ssidHashPrefix =
            if (data.size >= 3) {
                data.copyOfRange(0, 3)
            } else {
                data.copyOfRange(0, data.size)
            }

        return AppleDeviceData(
            deviceModel = "Apple Device (Wi‑Fi Join)",
            wifiSsidHashPrefix = ssidHashPrefix,
        )
    }
}
