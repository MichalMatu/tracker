package io.blueeye.core.decoders.parser.apple

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AirPlayParser @Inject constructor() {
    fun parse(data: ByteArray): AppleDeviceData? {
        // Type 0x09
        val flags = if (data.isNotEmpty()) data[0].toInt() and 0xFF else 0
        return AppleDeviceData(
            deviceModel = "Apple AirPlay Target",
            airPlayFlags = flags
        )
    }
}
