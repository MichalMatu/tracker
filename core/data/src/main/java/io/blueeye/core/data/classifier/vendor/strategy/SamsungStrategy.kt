package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.ManufacturerIds
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.data.classifier.vendor.VendorStrategy
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

class SamsungStrategy
@Inject
constructor(
    private val manufacturerParser: io.blueeye.core.decoders.parser.samsung.SamsungManufacturerParser,
) : VendorStrategy {
    override fun canHandle(input: VendorScanInput): Boolean {
        return input.hasManufacturer(ManufacturerIds.SAMSUNG) ||
            input.hasServiceUuid(SMARTTHINGS_FIND_UUID)
    }

    override fun decode(input: VendorScanInput): VendorScanResult {
        val mfgResult = input.manufacturerData(ManufacturerIds.SAMSUNG)?.let { manufacturerParser.parse(it, input.deviceName) }

        val deviceType = mfgResult?.deviceType ?: DeviceType.UNKNOWN
        val modelName = mfgResult?.deviceModel ?: "Samsung Device"
        var extraInfo = ""

        if (input.hasServiceUuid(SMARTTHINGS_FIND_UUID)) {
            extraInfo += "SmartThings Find. "
        }

        if (input.hasManufacturer(ManufacturerIds.SAMSUNG) && input.hasServiceUuid(QUICK_SHARE_UUID)) {
            extraInfo += "Fast Pair / Ecosystem (device type unknown). "
        }

        // Fallback or Merge
        return VendorScanResult(
            deviceType = deviceType,
            modelName = modelName,
            extraInfo = extraInfo.trim().ifEmpty { "Samsung Ecosystem" },
        )
    }

    private companion object {
        private const val SMARTTHINGS_FIND_UUID = "fd5a"
        private const val QUICK_SHARE_UUID = "fe2c"
    }
}
