package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.ManufacturerIds
import io.blueeye.core.data.classifier.vendor.NameAnalyzer
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.data.classifier.vendor.VendorStrategy
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

/** Strategy for Sony devices: PlayStation, WH-1000XM headphones, cameras, etc. */
class SonyStrategy
@Inject
constructor() : VendorStrategy, NameAnalyzer {
    override fun canHandle(input: VendorScanInput): Boolean {
        return input.hasManufacturer(ManufacturerIds.SONY)
    }

    override fun decode(input: VendorScanInput): VendorScanResult {
        return VendorScanResult(
            deviceType = DeviceType.HEADPHONES,
            modelName = "Sony Audio Device",
            extraInfo = "Sony",
        )
    }

    override fun analyzeName(name: String): VendorScanResult? {
        val n = name.lowercase()

        return when {
            isGaming(n) -> VendorScanResult(deviceType = DeviceType.GAMING, modelName = name, extraInfo = "PlayStation")
            isAudio(
                n
            ) -> VendorScanResult(deviceType = DeviceType.HEADPHONES, modelName = name, extraInfo = "Sony Audio")
            isCamera(n) -> VendorScanResult(deviceType = DeviceType.CAMERA, modelName = name, extraInfo = "Sony Camera")
            n.contains("sony") -> VendorScanResult(modelName = name, extraInfo = "Sony Device")
            else -> null
        }
    }

    private fun isGaming(n: String) = n.contains("playstation") || n.contains("ps5") ||
        n.contains("ps4") || n.contains("dualshock") || n.contains("dualsense")

    private fun isAudio(n: String) = n.contains("wh-1000") || n.contains("wf-1000") ||
        n.contains("wh-") || n.contains("wf-") || (n.contains("sony") && n.contains("headphone"))

    private fun isCamera(n: String) = n.contains("ilce") || n.contains("a7") || n.contains("zv-")
}
