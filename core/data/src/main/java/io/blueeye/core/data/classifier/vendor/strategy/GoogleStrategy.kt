package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.ManufacturerIds
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.data.classifier.vendor.VendorStrategy
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

class GoogleStrategy
@Inject
constructor() : VendorStrategy {
    override fun canHandle(input: VendorScanInput): Boolean {
        return input.hasManufacturer(ManufacturerIds.GOOGLE)
    }

    override fun decode(input: VendorScanInput): VendorScanResult {
        // FAST PAIR Logic
        // Usually starts with Model ID (3 bytes) or Account Key filter

        // Simple heuristic: Google ID usually implies Fast Pair capable device
        return VendorScanResult(
            modelName = "Google Fast Pair Device",
            extraInfo = "Fast Pair Protocol",
            // Google manufacturer data alone does not identify hardware form factor.
            deviceType = DeviceType.UNKNOWN,
        )
    }
}
