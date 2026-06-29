package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.NameAnalyzer
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.data.classifier.vendor.VendorStrategy
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

class AutomotiveStrategy
@Inject
constructor() : VendorStrategy, NameAnalyzer {
    override fun canHandle(input: VendorScanInput): Boolean = false

    override fun decode(input: VendorScanInput): VendorScanResult {
        return VendorScanResult(deviceType = DeviceType.CAR)
    }

    override fun analyzeName(name: String): VendorScanResult? {
        val n = name.lowercase()

        val brandPrefixes = listOf(
            "bmw", "audi", "vw ", "ford", "tesla", "toyota", "nissan", "honda", "mazda", "volvo",
            "mercedes", "skoda", "peugeot", "renault", "citroen", "fiat", "kia", "hyundai", "opel"
        )

        val systemPatterns = listOf(
            "sync ", "carplay", "android auto", "uconnect", "blue&me", "mbux", "mmi ", "r-link",
            "intellilink", "mylink", "entune", "starlink", "vw radio", "car audio", "car kit",
            "pioneer", "kenwood", "alpine", "jvc", "blaupunkt"
        )

        return when {
            brandPrefixes.any { n.contains(it) } -> VendorScanResult(
                deviceType = DeviceType.CAR,
                modelName = name,
                extraInfo = "Car Multimedia",
            )
            n.startsWith("sync") || systemPatterns.any { n.contains(it) } -> VendorScanResult(
                deviceType = DeviceType.CAR,
                modelName = name,
                extraInfo = "Infotainment System",
            )
            n.contains("dtco") || n.contains("vdo") || n.contains("stoneridge") -> VendorScanResult(
                deviceType = DeviceType.CAR,
                modelName = "Tachograph / Truck Unit",
                extraInfo = "Transport Equipment",
            )
            else -> null
        }
    }
}
