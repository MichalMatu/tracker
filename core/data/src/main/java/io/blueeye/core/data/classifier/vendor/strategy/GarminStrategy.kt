package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.ManufacturerIds
import io.blueeye.core.data.classifier.vendor.NameAnalyzer
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.data.classifier.vendor.VendorStrategy
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

/** Strategy for Garmin sports devices: watches, bike computers, HR straps. */
class GarminStrategy
@Inject
constructor() : VendorStrategy, NameAnalyzer {
    override fun canHandle(input: VendorScanInput): Boolean {
        return input.hasManufacturer(ManufacturerIds.GARMIN)
    }

    override fun decode(input: VendorScanInput): VendorScanResult {
        return VendorScanResult(
            deviceType = DeviceType.WATCH,
            modelName = "Garmin Device",
            extraInfo = "Garmin Sports",
        )
    }

    override fun analyzeName(name: String): VendorScanResult? {
        val n = name.lowercase()

        return when {
            n.contains("garmin") -> classifyGarmin(name, n)
            n.contains("polar") -> classifyPolar(name)
            n.contains("wahoo") || n.contains("elemnt") || n.contains("tickr") -> classifyWahoo(name)
            else -> null
        }
    }

    private fun classifyGarmin(name: String, n: String): VendorScanResult {
        val type = when {
            n.contains("fenix") || n.contains("forerunner") || n.contains("venu") ||
                n.contains("vivoactive") || n.contains("instinct") ||
                n.contains("enduro") -> DeviceType.WATCH
            n.contains("vivosmart") || n.contains("vivofit") -> DeviceType.FITNESS
            n.contains("edge") || n.contains("rally") || n.contains("hrm") ||
                n.contains("heart") -> DeviceType.FITNESS
            else -> DeviceType.WATCH
        }
        return VendorScanResult(deviceType = type, modelName = name, extraInfo = "Garmin Sports")
    }

    private fun classifyPolar(name: String) = VendorScanResult(
        deviceType = DeviceType.WATCH,
        modelName = name,
        extraInfo = "Polar Sports",
    )

    private fun classifyWahoo(name: String) = VendorScanResult(
        deviceType = DeviceType.FITNESS,
        modelName = name,
        extraInfo = "Wahoo Cycling",
    )
}
