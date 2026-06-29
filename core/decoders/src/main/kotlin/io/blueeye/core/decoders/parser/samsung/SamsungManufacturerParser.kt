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
    private val quickShareParser: QuickShareParser,
    private val smartThingsParser: SmartThingsParser,
) {
    fun parse(data: ByteArray?): SamsungDeviceData? {
        if (data == null || data.isEmpty()) return null

        // Try SmartTag (Offline Finding) first (Type 0x42)
        val smartTagData = smartTagParser.parse(data)
        if (smartTagData != null) return smartTagData

        // Try Quick Share (Type 0x12) - Placeholder ID check inside parser if implemented
        val quickShareData = quickShareParser.parse(data) // Check implementation of this type
        if (quickShareData != null) return quickShareData

        // Try SmartThings (generic)
        val smartThingsData = smartThingsParser.parse(data)
        if (smartThingsData != null) return smartThingsData

        // Unknown Samsung Device
        return SamsungDeviceData(
            deviceModel = "Samsung Device",
            deviceType = DeviceType.UNKNOWN // Could be generic Samsung phone/accessory
        )
    }
}
