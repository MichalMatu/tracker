package io.blueeye.core.decoders.parser.apple

import javax.inject.Inject
import javax.inject.Singleton

/** Parser for Apple Wi‑Fi Settings / Tethering Target (Type 0x0D). Based on FuriousMAC. */
@Singleton
class TetheringTargetParser
@Inject
constructor() {
    fun parse(data: ByteArray): AppleDeviceData? {
        if (data.isEmpty()) return null

        // Exact field layout is device/version dependent. Keep raw bytes for fingerprinting.
        return AppleDeviceData(
            deviceModel = "Apple Wi‑Fi Settings",
            tetheringType = 0x0D,
            tetheringPayload = data,
        )
    }
}
