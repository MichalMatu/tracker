package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.ManufacturerIds
import io.blueeye.core.data.classifier.vendor.NameAnalyzer
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.data.classifier.vendor.VendorStrategy
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

/**
 * Strategy for DJI drones and related accessories. Detects Remote ID broadcasts and controller
 * connections.
 */
class DjiStrategy
@Inject
constructor() : VendorStrategy, NameAnalyzer {
    override fun canHandle(input: VendorScanInput): Boolean {
        return input.hasManufacturer(ManufacturerIds.DJI)
    }

    override fun decode(input: VendorScanInput): VendorScanResult {
        return VendorScanResult(
            deviceType = DeviceType.DRONE,
            modelName = "DJI Drone",
            extraInfo = "UAV Remote ID",
        )
    }

    override fun analyzeName(name: String): VendorScanResult? {
        val n = name.lowercase()

        val dronePrefixes = listOf("dji", "mavic", "phantom", "mini ", "air 2", "spark", "inspire", "matrice")
        val accessoryPrefixes = listOf("rc-", "goggles", "osmo", "ronin")
        val autelPrefixes = listOf("autel", "evo ")
        val parrotPrefixes = listOf("parrot", "anafi")

        return when {
            dronePrefixes.any { n.contains(it) } -> VendorScanResult(
                deviceType = DeviceType.DRONE,
                modelName = name,
                extraInfo = "DJI Drone",
            )
            accessoryPrefixes.any { n.contains(it) } -> VendorScanResult(
                deviceType = DeviceType.CAMERA,
                modelName = name,
                extraInfo = "DJI Accessory",
            )
            autelPrefixes.any { n.contains(it) } -> VendorScanResult(
                deviceType = DeviceType.DRONE,
                modelName = name,
                extraInfo = "Autel Drone",
            )
            parrotPrefixes.any { n.contains(it) } -> VendorScanResult(
                deviceType = DeviceType.DRONE,
                modelName = name,
                extraInfo = "Parrot Drone",
            )
            else -> null
        }
    }
}
