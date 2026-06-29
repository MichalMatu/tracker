package io.blueeye.core.decoders.parser.apple

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeKitParser @Inject constructor() {
    fun parse(data: ByteArray): AppleDeviceData? {
        // Type 0x06
        val status = if (data.isNotEmpty()) data[0].toInt() and 0xFF else 0
        return AppleDeviceData(
            deviceModel = "Apple HomeKit Accessory",
            homeKitStatus = status
        )
    }
}
