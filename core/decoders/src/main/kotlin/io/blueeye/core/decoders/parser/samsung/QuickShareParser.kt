package io.blueeye.core.decoders.parser.samsung

import io.blueeye.core.model.DeviceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for Samsung Quick Share / Google Nearby Share. Service UUID: 0xFE2C. Based on NearDrop
 * research.
 */
@Singleton
class QuickShareParser
@Inject
constructor() {
    fun parse(serviceData: ByteArray?): SamsungDeviceData? {
        if (serviceData == null) return null

        // Quick Share / Nearby Share frame.
        // Usually: [Version/Flags] [Salt/Hash...]

        // This presence strongly suggests a Phone, Tablet, or Laptop.
        // It heavily implies a modern Android/Samsung device.

        return SamsungDeviceData(
            deviceModel = "Samsung/Android (Quick Share)",
            deviceType = DeviceType.PHONE, // Most likely a phone or tablet
            isQuickShareVisible = true,
        )
    }
}
