package io.blueeye.core.data.classifier.pipeline

import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.model.DeviceType

/**
 * Strategy for classifying devices based on their GATT Services UUIDs.
 * This is used for "Device Fingerprinting" after a connection has been established
 * or when service UUIDs are available in the advertisement data.
 */
object GattServicesStrategy {

    fun classify(services: List<String>, deviceName: String? = null): VendorScanResult? {
        val normalizedServices = services.map { it.lowercase() }

        return when {
            normalizedServices.any { it.contains("12345678-1234-5678-1234-56789abcdef0") } -> {
                 if (deviceName?.startsWith("PlantCare", ignoreCase = true) == true) {
                     VendorScanResult(
                         deviceType = DeviceType.SENSOR,
                         modelName = "PlantCare Soil Sensor",
                         extraInfo = "MichalMatu ESP32-C6 Project"
                     )
                 } else {
                     VendorScanResult(
                         deviceType = DeviceType.UNKNOWN,
                         modelName = "Generic ESP32",
                         extraInfo = "Espressif Device"
                     )
                 }
            }
            normalizedServices.any { it.contains("0000febe") || it.contains("febe") } -> VendorScanResult(
                deviceType = DeviceType.HEADPHONES,
                modelName = null,
                extraInfo = "Bose Wearable"
            )
            normalizedServices.any { it.contains("d0611e78-bbb4-4591-a5f8-487910ae4366") } -> VendorScanResult(
                deviceType = DeviceType.LAPTOP,
                modelName = "Apple Device (Continuity)",
                extraInfo = "Supports Handoff/Universal Clipboard"
            )
            normalizedServices.any { it.contains("9fa480e0-4967-4542-9390-d343dc5d04ae") } -> VendorScanResult(
                deviceType = DeviceType.PHONE,
                modelName = "Apple Device (Nearby)",
                extraInfo = "Supports AirDrop/Proximity"
            )
            normalizedServices.any { it.contains("0000fe2c") || it.contains("fe2c") } -> VendorScanResult(
                deviceType = DeviceType.HEADPHONES,
                modelName = "Fast Pair Device",
                extraInfo = "Google Fast Pair"
            )
            normalizedServices.any { it.contains("0000feed") || it.contains("feed") } -> VendorScanResult(
                deviceType = DeviceType.TAG,
                modelName = "Tile Tracker",
                extraInfo = "Lost & Found Tag"
            )
            else -> null
        }
    }
}
