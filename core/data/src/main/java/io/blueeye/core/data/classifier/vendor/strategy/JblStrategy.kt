package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.ManufacturerIds
import io.blueeye.core.data.classifier.vendor.NameAnalyzer
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.data.classifier.vendor.VendorStrategy
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

/** Strategy for JBL/Harman audio devices. */
class JblStrategy
@Inject
constructor() : VendorStrategy, NameAnalyzer {
    override fun canHandle(input: VendorScanInput): Boolean {
        return input.hasManufacturer(ManufacturerIds.JBL)
    }

    override fun decode(input: VendorScanInput): VendorScanResult {
        return VendorScanResult(
            deviceType = DeviceType.SPEAKER,
            modelName = "JBL Audio",
            extraInfo = "JBL/Harman",
        )
    }

    override fun analyzeName(name: String): VendorScanResult? {
        val n = name.lowercase()

        return when {
            n.contains("jbl") -> classifyJbl(name, n)
            n.contains("harman") || n.contains("kardon") -> classifyHarman(name)
            else -> null
        }
    }

    private fun classifyJbl(name: String, n: String): VendorScanResult {
        val type = when {
            n.contains("tune") || n.contains("live") || n.contains("endurance") ||
                n.contains("reflect") -> DeviceType.HEADPHONES
            n.contains("flip") || n.contains("charge") || n.contains("xtreme") ||
                n.contains("partybox") || n.contains("go ") ||
                n.contains("pulse") -> DeviceType.SPEAKER
            n.contains("bar") -> DeviceType.TV
            else -> DeviceType.SPEAKER
        }
        return VendorScanResult(deviceType = type, modelName = name, extraInfo = "JBL Audio")
    }

    private fun classifyHarman(name: String) = VendorScanResult(
        deviceType = DeviceType.SPEAKER,
        modelName = name,
        extraInfo = "Harman Kardon",
    )
}
