package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.ManufacturerIds
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.data.classifier.vendor.VendorStrategy
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

class TileStrategy
@Inject
constructor() : VendorStrategy {
    override fun canHandle(input: VendorScanInput): Boolean {
        return input.hasManufacturer(ManufacturerIds.TILE)
    }

    override fun decode(input: VendorScanInput): VendorScanResult {
        // Tile devices are strictly trackeres
        return VendorScanResult(
            deviceType = DeviceType.TILE,
            modelName = "Tile Tracker",
            extraInfo = "Tile Network",
        )
    }
}
