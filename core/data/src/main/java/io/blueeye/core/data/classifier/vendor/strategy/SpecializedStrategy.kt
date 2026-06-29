package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.NameAnalyzer
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.data.classifier.vendor.VendorStrategy
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

/**
 * Strategy for specialized equipment like Drones and Medical devices.
 */
class SpecializedStrategy @Inject constructor() : VendorStrategy, NameAnalyzer {

    private data class SpecializedRule(
        val keywords: List<String>,
        val type: DeviceType,
        val model: String,
        val info: String
    )

    private val rules = listOf(
        SpecializedRule(
            keywords = listOf("mavic", "phantom", "matrice", "mini 3", "mini 4", "air 3"),
            type = DeviceType.DRONE,
            model = "DJI Drone",
            info = "UAV System"
        ),
        SpecializedRule(
            keywords = listOf("autel", "evo"),
            type = DeviceType.DRONE,
            model = "Autel Robotics",
            info = "UAV System"
        ),
        SpecializedRule(
            keywords = listOf("parrot", "anafi"),
            type = DeviceType.DRONE,
            model = "Parrot Anafi",
            info = "UAV System"
        ),
        SpecializedRule(
            keywords = listOf("skydio"),
            type = DeviceType.DRONE,
            model = "Skydio",
            info = "UAV System"
        ),
        SpecializedRule(
            keywords = listOf("lifepak", "lucas"),
            type = DeviceType.MEDICAL,
            model = "Stryker",
            info = "Defibrillator"
        ),
        SpecializedRule(
            keywords = listOf("corpuls"),
            type = DeviceType.MEDICAL,
            model = "Corpuls",
            info = "Defibrillator"
        ),
        SpecializedRule(
            keywords = listOf("zoll"),
            type = DeviceType.MEDICAL,
            model = "Zoll Medical",
            info = "Defibrillator"
        )
    )

    override fun canHandle(input: VendorScanInput): Boolean = false

    override fun decode(input: VendorScanInput): VendorScanResult =
        VendorScanResult(DeviceType.UNKNOWN)

    override fun analyzeName(name: String): VendorScanResult? {
        if (name.isBlank()) return null
        val n = name.lowercase()
        return rules.firstNotNullOfOrNull { rule ->
            if (rule.keywords.any { n.contains(it) }) {
                VendorScanResult(rule.type, rule.model, extraInfo = rule.info)
            } else {
                null
            }
        }
    }
}
