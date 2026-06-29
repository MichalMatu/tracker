package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.NameAnalyzer
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.data.classifier.vendor.VendorStrategy
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

class CommercialStrategy @Inject constructor() : VendorStrategy, NameAnalyzer {

    private data class CommercialRule(
        val keywords: List<String>,
        val type: DeviceType,
        val info: String
    )

    private val posKeywords = listOf("sumup", "ingenico", "verifone", "mypos", "izettle", "square")
    private val accessKeywords = listOf("danalock", "nuki", "yale", "hid ", "assa abloy", "salto")
    private val industrialKeywords = listOf("honeywell", "cognex", "datalogic", "zebra", "symbol")

    private val rules = listOf(
        CommercialRule(posKeywords, DeviceType.POS, "Payment Terminal"),
        CommercialRule(accessKeywords, DeviceType.ACCESS_CONTROL, "Access Control"),
        CommercialRule(industrialKeywords, DeviceType.PRINTER, "Industrial Scanner")
    )

    override fun canHandle(input: VendorScanInput): Boolean = false

    override fun decode(input: VendorScanInput): VendorScanResult =
        VendorScanResult(deviceType = DeviceType.UNKNOWN)

    override fun analyzeName(name: String): VendorScanResult? {
        if (name.isBlank()) return null
        val n = name.lowercase()
        return rules.firstNotNullOfOrNull { rule ->
            if (rule.keywords.any { n.contains(it) }) {
                VendorScanResult(rule.type, name, extraInfo = rule.info)
            } else {
                null
            }
        }
    }
}
