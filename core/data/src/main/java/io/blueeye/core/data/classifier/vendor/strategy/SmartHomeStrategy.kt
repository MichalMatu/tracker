package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.ManufacturerIds
import io.blueeye.core.data.classifier.vendor.NameAnalyzer
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.data.classifier.vendor.VendorStrategy
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

class SmartHomeStrategy
@Inject
constructor() : VendorStrategy, NameAnalyzer {
    override fun canHandle(input: VendorScanInput): Boolean {
        return input.hasManufacturer(ManufacturerIds.PHILIPS_HUE) ||
            input.hasManufacturer(ManufacturerIds.TUYA) ||
            input.hasManufacturer(ManufacturerIds.IKEA)
    }

    override fun decode(input: VendorScanInput): VendorScanResult {
        return VendorScanResult(
            deviceType = DeviceType.SMART_HOME,
            modelName = "Philips Hue",
            extraInfo = "Smart Lighting",
        )
    }

    override fun analyzeName(name: String): VendorScanResult? {
        val n = name.lowercase()

        return when {
            isIot(n) -> VendorScanResult(deviceType = DeviceType.SMART_HOME, modelName = name, extraInfo = "IoT Device")
            isAudioVideo(
                n
            ) -> VendorScanResult(deviceType = DeviceType.SPEAKER, modelName = name, extraInfo = "Audio/Video")
            isMedical(
                n
            ) -> VendorScanResult(deviceType = DeviceType.MEDICAL, modelName = name, extraInfo = "Medical Device")
            else -> null
        }
    }

    private fun isIot(n: String) = n.contains("hue ") || n.contains("tuya") ||
        n.contains("yeelight") || n.contains("lifx") || n.contains("govee") ||
        n.contains("switchbot") || n.contains("aqara") || n.contains("nest") ||
        n.contains("ring") || n.contains("ikea")

    private fun isAudioVideo(n: String) = n.contains("bose") || n.contains("sonos") ||
        n.contains("jbl") || n.contains("sony") ||
        (n.contains("samsung") && (n.contains("tv") || n.contains("soundbar")))

    private fun isMedical(n: String) = n.contains("dexcom") || n.contains("abbott") ||
        n.contains("medtronic") || n.contains("omron") || n.contains("nonin")
}
