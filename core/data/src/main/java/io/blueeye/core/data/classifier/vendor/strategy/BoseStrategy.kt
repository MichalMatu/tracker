package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.ManufacturerIds
import io.blueeye.core.data.classifier.vendor.NameAnalyzer
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.data.classifier.vendor.VendorStrategy
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

/** Strategy for Bose premium audio devices. */
class BoseStrategy
@Inject
constructor() : VendorStrategy, NameAnalyzer {
    override fun canHandle(input: VendorScanInput): Boolean {
        return input.hasManufacturer(ManufacturerIds.BOSE) || input.hasServiceUuid("febe")
    }

    override fun decode(input: VendorScanInput): VendorScanResult {
        var extra = "Bose"

        // Detect Proprietary Protocol (0xFEBE)
        if (input.hasServiceUuid("febe")) {
            extra = "Bose Proprietary Audio (QC/NC Series)"
        }

        // Manufacturer ID 158 (0x009E) usually carries product ID in first byte?
        // Based on research, packet structure is complex, often just minimal presence.
        // We rely heavily on Service UUID 0xFEBE for identification of advanced models.

        return VendorScanResult(
            deviceType = DeviceType.HEADPHONES,
            modelName = "Bose Audio Device",
            extraInfo = extra,
        )
    }

    override fun analyzeName(name: String): VendorScanResult? {
        val n = name.lowercase()

        if (n.contains("bose")) {
            // QuietComfort, SoundLink, SoundSport, etc.
            val type =
                when {
                    n.contains("soundlink") &&
                        (n.contains("speaker") || n.contains("revolve")) ->
                        DeviceType.SPEAKER
                    n.contains("soundbar") || n.contains("home speaker") -> DeviceType.SPEAKER
                    else -> DeviceType.HEADPHONES
                }

            return VendorScanResult(
                deviceType = type,
                modelName = name,
                extraInfo = "Bose Premium Audio",
            )
        }

        return null
    }
}
