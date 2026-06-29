package io.blueeye.core.decoders.parser.apple

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MagicSwitchParser @Inject constructor() {
    fun parse(@Suppress("UNUSED_PARAMETER") data: ByteArray): AppleDeviceData? {
        // Type 0x0B
        return AppleDeviceData(deviceModel = "Apple Magic Switch")
    }
}
