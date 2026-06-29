package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.ManufacturerIds
import io.blueeye.core.data.classifier.vendor.NameAnalyzer
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.data.classifier.vendor.VendorStrategy
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

/**
 * Strategy for Xiaomi ecosystem: Mi Band, Mi Watch, scooters, IoT, etc. Also covers Huami (Amazfit)
 * and Zepp.
 */
class XiaomiStrategy
@Inject
constructor() : VendorStrategy, NameAnalyzer {
    override fun canHandle(input: VendorScanInput): Boolean {
        return input.hasManufacturer(ManufacturerIds.XIAOMI)
    }

    override fun decode(input: VendorScanInput): VendorScanResult {
        return VendorScanResult(
            deviceType = DeviceType.FITNESS,
            modelName = "Xiaomi Device",
            extraInfo = "Xiaomi Ecosystem",
        )
    }

    @Suppress("CyclomaticComplexMethod")
    override fun analyzeName(name: String): VendorScanResult? {
        val n = name.lowercase()

        return when {
            n.contains("mi band") || n.contains("smart band") || n.contains("miband") ->
                VendorScanResult(
                    deviceType = DeviceType.FITNESS,
                    modelName = name,
                    extraInfo = "Xiaomi Fitness",
                )
            n.contains("amazfit") || n.contains("zepp") || n.contains("pace") ||
                n.contains("gtr") || n.contains("gts") || n.contains("bip") ->
                VendorScanResult(
                    deviceType = DeviceType.WATCH,
                    modelName = name,
                    extraInfo = "Amazfit/Huami",
                )
            n.contains("m365") || n.contains("scooter") || n.contains("ninebot") ->
                VendorScanResult(
                    deviceType = DeviceType.CAR,
                    modelName = name,
                    extraInfo = "Electric Scooter",
                )
            n.contains("xiaomi") || n.contains("mi ") || n.startsWith("mi-") ->
                VendorScanResult(
                    deviceType = DeviceType.SMART_HOME,
                    modelName = name,
                    extraInfo = "Xiaomi",
                )
            n.contains("huawei") || n.contains("honor") -> {
                val type = when {
                    n.contains("band") -> DeviceType.FITNESS
                    n.contains("watch") || n.contains("gt ") -> DeviceType.WATCH
                    n.contains("freebuds") -> DeviceType.HEADPHONES
                    else -> DeviceType.PHONE
                }
                VendorScanResult(deviceType = type, modelName = name, extraInfo = "Huawei")
            }
            else -> null
        }
    }
}
