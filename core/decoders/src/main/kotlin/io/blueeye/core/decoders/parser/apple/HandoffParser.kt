package io.blueeye.core.decoders.parser.apple

import javax.inject.Inject
import javax.inject.Singleton

/** Parser for Apple Handoff (Type 0x0C). Based on FuriousMAC. */
@Singleton
class HandoffParser
@Inject
constructor() {
    fun parse(data: ByteArray): AppleDeviceData? {
        if (data.isEmpty()) return null

        // FuriousMAC notes a monotonically increasing IV/counter. Exact endianness varies by payload.
        // We expose a best-effort unsigned 16-bit value from the first 2 bytes.
        val iv =
            if (data.size >= 2) {
                ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
            } else {
                data[0].toInt() and 0xFF
            }

        return AppleDeviceData(
            deviceModel = "Apple Handoff",
            handoffIv = iv,
        )
    }
}
