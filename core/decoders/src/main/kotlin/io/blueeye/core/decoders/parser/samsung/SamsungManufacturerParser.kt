package io.blueeye.core.decoders.parser.samsung

import io.blueeye.core.model.DeviceType
import javax.inject.Inject
import javax.inject.Singleton

/** Parser for Samsung manufacturer data (0x0075). */
@Singleton
class SamsungManufacturerParser
@Inject
constructor(
    private val smartTagParser: SmartTagParser,
) {
    fun parse(
        data: ByteArray?,
        advertisedName: String? = null,
    ): SamsungDeviceData? =
        data
            ?.takeIf { it.isNotEmpty() }
            ?.let { manufacturerData ->
                smartTagParser.parse(manufacturerData, advertisedName)
                    ?: SamsungDeviceData(
                        deviceModel = "Samsung Device",
                        deviceType = DeviceType.UNKNOWN,
                    )
            }
}
