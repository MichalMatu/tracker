package io.blueeye.core.decoders.parser.samsung

import io.blueeye.core.model.DeviceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for Samsung Manufacturer Data (ID: 0x0075). Defines logic to delegate to specific subtype
 * parsers.
 */
@Singleton
class SamsungManufacturerParser
@Inject
constructor(
    private val smartTagParser: SmartTagParser,
) {
    fun parse(data: ByteArray?, advertisedName: String? = null): SamsungDeviceData? {
        if (data == null || data.isEmpty()) return null

        // Try SmartTag (Offline Finding) first (Type 0x42)
        val smartTagData = smartTagParser.parse(data, advertisedName)
        if (smartTagData != null) return smartTagData

        // Quick Share / Fast Pair and SmartThings Find use their own service frames.
        // Treating arbitrary 0x0075 bytes as one of those protocols mislabels TVs.

        // Unknown Samsung Device
        return SamsungDeviceData(
            deviceModel = "Samsung Device",
            deviceType = DeviceType.UNKNOWN // Could be generic Samsung phone/accessory
        )
    }
}
