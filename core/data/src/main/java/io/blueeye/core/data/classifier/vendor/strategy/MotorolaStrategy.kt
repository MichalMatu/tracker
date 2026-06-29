package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.ManufacturerIds
import io.blueeye.core.data.classifier.vendor.NameAnalyzer
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.data.classifier.vendor.VendorStrategy
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

/**
 * Strategy for Motorola Solutions and Zebra Technologies equipment.
 */
class MotorolaStrategy @Inject constructor() : VendorStrategy, NameAnalyzer {

    private data class MotorolaRule(
        val keywords: List<String>,
        val type: DeviceType,
        val model: (String) -> String,
        val info: String
    )

    private val rules = listOf(
        MotorolaRule(
            listOf("apx", "xts", "mxp", "mtp", "st7000"),
            DeviceType.TACTICAL_RADIO,
            { "Motorola Radio" },
            "Public Safety Comms"
        ),
        MotorolaRule(
            listOf("zebra", "symbol", "ds2278", "ds8178", "li4278"),
            DeviceType.PRINTER,
            { "Zebra Scanner" },
            "Logistics/POS"
        )
    )

    override fun canHandle(input: VendorScanInput): Boolean =
        input.hasManufacturer(ManufacturerIds.MOTOROLA) ||
            input.hasManufacturer(ManufacturerIds.ZEBRA) ||
            input.hasManufacturer(ManufacturerIds.ZEBRA_SYMBOL)

    override fun decode(input: VendorScanInput): VendorScanResult =
        VendorScanResult(DeviceType.UNKNOWN, "Motorola/Zebra", extraInfo = "Vendor match")

    override fun analyzeName(name: String): VendorScanResult? {
        if (name.isBlank()) return null
        val n = name.lowercase()
        return rules.firstNotNullOfOrNull { rule ->
            if (rule.keywords.any { n.contains(it) }) {
                VendorScanResult(rule.type, rule.model(name), extraInfo = rule.info)
            } else {
                null
            }
        }
    }
}
