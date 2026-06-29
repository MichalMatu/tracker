package io.blueeye.core.decoders.parser.apple

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeySiriParser @Inject constructor() {
    fun parse(@Suppress("UNUSED_PARAMETER") data: ByteArray): AppleDeviceData? {
        // Type 0x08
        return AppleDeviceData(deviceModel = "Apple Hey Siri")
    }
}
