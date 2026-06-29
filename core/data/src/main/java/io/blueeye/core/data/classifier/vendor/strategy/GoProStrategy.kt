package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.ManufacturerIds
import io.blueeye.core.data.classifier.vendor.NameAnalyzer
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.data.classifier.vendor.VendorStrategy
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

/** Strategy for GoPro action cameras. */
class GoProStrategy
@Inject
constructor() : VendorStrategy, NameAnalyzer {
    override fun canHandle(input: VendorScanInput): Boolean {
        return input.hasManufacturer(ManufacturerIds.GOPRO)
    }

    override fun decode(input: VendorScanInput): VendorScanResult {
        return VendorScanResult(
            deviceType = DeviceType.CAMERA,
            modelName = "GoPro Camera",
            extraInfo = "Action Camera",
        )
    }

    override fun analyzeName(name: String): VendorScanResult? {
        val n = name.lowercase()

        return when {
            n.contains("gopro") || n.contains("hero") || n.contains("max ") || n.startsWith("gp") ->
                VendorScanResult(
                    deviceType = DeviceType.CAMERA,
                    modelName = name,
                    extraInfo = "GoPro Action Camera",
                )
            n.contains("insta360") || n.contains("one x") || n.contains("go 2") ->
                VendorScanResult(
                    deviceType = DeviceType.CAMERA,
                    modelName = name,
                    extraInfo = "Insta360 Camera",
                )
            else -> null
        }
    }
}
