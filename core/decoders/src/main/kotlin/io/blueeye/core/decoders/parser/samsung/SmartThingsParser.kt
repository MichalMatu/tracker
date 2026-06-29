package io.blueeye.core.decoders.parser.samsung

import io.blueeye.core.model.DeviceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for Samsung SmartThings Find / Offline Finding. Based on uTag / FindMy research.
 * SERVICE/DATA UUID: 0xFD5A (SmartThings)
 */
@Singleton
class SmartThingsParser
@Inject
constructor() {
    fun parse(serviceData: ByteArray?): SamsungDeviceData? {
        if (serviceData == null || serviceData.isEmpty()) return null

        // SmartThings Offline Finding usually broadcasts under Service UUID 0xFD5A.
        // Payload structure varies by type (Tag, Phone, etc).
        // Common element: Privacy ID (derived from seed).

        // Minimal length check (heuristic)
        if (serviceData.size < 4) return null

        // This is a simplified heuristic.
        // Real implementation would require complex unwrapping of the payload structure described
        // in uTag.
        // For now, we flag presence of FD5A with sufficient length as a SmartThings device.

        return SamsungDeviceData(
            deviceModel = "Samsung Device (SmartThings)",
            deviceType = DeviceType.SAMSUNG_TAG, // Assume Tag or Tracking capable device
            isOfflineFinding = true,
            privacyId = serviceData.take(4).toByteArray(), // Just taking a prefix as ID for now
        )
    }
}
