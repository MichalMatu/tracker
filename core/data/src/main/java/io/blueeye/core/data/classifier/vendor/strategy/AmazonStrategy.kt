package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.ManufacturerIds
import io.blueeye.core.data.classifier.vendor.NameAnalyzer
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.data.classifier.vendor.VendorStrategy
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

/** Strategy for Amazon devices: Echo, Ring, Fire TV, Kindle, etc. */
class AmazonStrategy
@Inject
constructor() : VendorStrategy, NameAnalyzer {
    override fun canHandle(input: VendorScanInput): Boolean {
        return input.hasManufacturer(ManufacturerIds.AMAZON)
    }

    override fun decode(input: VendorScanInput): VendorScanResult {
        return VendorScanResult(
            deviceType = DeviceType.SMART_HOME,
            modelName = "Amazon Device",
            extraInfo = "Amazon Alexa",
        )
    }

    override fun analyzeName(name: String): VendorScanResult? {
        val n = name.lowercase()

        return when {
            n.contains("echo") || n.contains("alexa") -> VendorScanResult(
                deviceType = DeviceType.SPEAKER,
                modelName = name,
                extraInfo = "Amazon Alexa",
            )
            n.contains("ring") -> VendorScanResult(
                deviceType = DeviceType.SMART_HOME,
                modelName = name,
                extraInfo = "Ring Security",
            )
            n.contains("fire tv") || n.contains("firestick") -> VendorScanResult(
                deviceType = DeviceType.TV,
                modelName = name,
                extraInfo = "Amazon Fire TV",
            )
            n.contains("kindle") -> VendorScanResult(
                deviceType = DeviceType.TABLET,
                modelName = name,
                extraInfo = "Amazon Kindle",
            )
            n.contains("amazon") -> VendorScanResult(
                deviceType = DeviceType.SMART_HOME,
                modelName = name,
                extraInfo = "Amazon",
            )
            else -> null
        }
    }
}
