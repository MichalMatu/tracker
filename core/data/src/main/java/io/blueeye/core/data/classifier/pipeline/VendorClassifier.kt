package io.blueeye.core.data.classifier.pipeline

import io.blueeye.core.model.DeviceType

/**
 * Strategy for classifying devices based on vendor name.
 *
 * Fallback when CoD and device name are unavailable.
 */
object VendorClassifier {
    /** Classify by vendor name. */
    fun classify(vendorName: String?): DeviceType {
        if (vendorName == null) return DeviceType.UNKNOWN

        val lowerVendorName = vendorName.lowercase()

        return when {
            lowerVendorName.contains("garmin") -> DeviceType.WEARABLE
            lowerVendorName.contains("fitbit") -> DeviceType.WEARABLE
            else -> DeviceType.UNKNOWN
        }
    }
}
