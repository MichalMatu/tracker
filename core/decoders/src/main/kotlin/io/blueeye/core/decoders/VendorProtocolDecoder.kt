package io.blueeye.core.decoders

import io.blueeye.core.model.DeviceType

/** Decoder for other vendor specific protocols (Microsoft, Samsung, Tile). */
object VendorProtocolDecoder {
    const val MAN_ID_MICROSOFT = 0x0006
    const val MAN_ID_SAMSUNG = 0x0075
    const val MAN_ID_TILE =
        0x00FEED // Tile mostly uses Service UUID, but sometimes Man data? Not really.

    data class VendorInfo(val deviceType: DeviceType, val name: String? = null)

    fun decode(
        manufacturerId: Int?,
        data: ByteArray?,
    ): VendorInfo? {
        return when (manufacturerId) {
            MAN_ID_MICROSOFT -> decodeMicrosoft(data)
            MAN_ID_SAMSUNG -> decodeSamsung(data)
            else -> null
        }
    }

    private fun decodeMicrosoft(@Suppress("UNUSED_PARAMETER") data: ByteArray?): VendorInfo {
        // Microsoft Swift Pair often starts with 0x01
        return VendorInfo(DeviceType.LAPTOP, "Microsoft Device")
    }

    private fun decodeSamsung(@Suppress("UNUSED_PARAMETER") data: ByteArray?): VendorInfo {
        // Check for SmartTag ... complex proprietary format
        return VendorInfo(DeviceType.PHONE, "Samsung Device")
    }
}
