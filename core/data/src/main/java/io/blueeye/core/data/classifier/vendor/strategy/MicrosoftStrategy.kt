package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.ManufacturerIds
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.data.classifier.vendor.VendorStrategy
import javax.inject.Inject

class MicrosoftStrategy
@Inject
constructor(
    private val swiftPairParser: io.blueeye.core.decoders.parser.microsoft.SwiftPairParser,
    private val cdpParser: io.blueeye.core.decoders.parser.microsoft.CdpParser,
) : VendorStrategy {
    override fun canHandle(input: VendorScanInput): Boolean {
        return input.hasManufacturer(ManufacturerIds.MICROSOFT)
    }

    override fun decode(input: VendorScanInput): VendorScanResult {
        val data = input.manufacturerData(ManufacturerIds.MICROSOFT) ?: return VendorScanResult()
        var result: VendorScanResult? = null

        // 1. Try Swift Pair
        val swiftPair = swiftPairParser.parse(data)
        if (swiftPair != null) {
            result = VendorScanResult(
                deviceType = swiftPair.deviceType,
                modelName = swiftPair.deviceModel ?: "Microsoft Accessory",
                extraInfo = "Swift Pair",
            )
        }

        // 2. Try CDP
        if (result == null) {
            val cdp = cdpParser.parse(data)
            if (cdp != null) {
                result = VendorScanResult(
                    deviceType = cdp.deviceType,
                    modelName = cdp.deviceModel ?: "Windows Device",
                    extraInfo = "Connected Devices Platform",
                )
            }
        }

        return result ?: VendorScanResult(modelName = "Microsoft Device")
    }
}
