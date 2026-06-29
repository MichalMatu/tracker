package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.ManufacturerIds
import io.blueeye.core.data.classifier.vendor.NameAnalyzer
import io.blueeye.core.data.classifier.vendor.TacticalOuiRegistry
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.data.classifier.vendor.VendorStrategy
import io.blueeye.core.data.classifier.vendor.tactical.TacticalCategory
import io.blueeye.core.data.classifier.vendor.tactical.TacticalNameMatcher
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

/** Strategy for detecting professional/public-safety Bluetooth signals. */
class TacticalStrategy @Inject constructor() : VendorStrategy, NameAnalyzer {

    override fun canHandle(input: VendorScanInput): Boolean =
        input.hasManufacturer(ManufacturerIds.SIG_SAUER) ||
            input.hasManufacturer(ManufacturerIds.L3HARRIS) ||
            input.hasManufacturer(ManufacturerIds.SEPURA) ||
            TacticalOuiRegistry.matchByServiceUuid(input.serviceUuids) != null

    override fun decode(input: VendorScanInput): VendorScanResult {
        return TacticalOuiRegistry.matchByServiceUuid(input.serviceUuids)?.let { (cat, desc) ->
            VendorScanResult(categoryToDeviceType(cat), desc, extraInfo = HIGH_CONFIDENCE_LABEL)
        } ?: VendorScanResult(DeviceType.TACTICAL, "Professional signal", extraInfo = MEDIUM_CONFIDENCE_LABEL)
    }

    override fun analyzeName(name: String): VendorScanResult? {
        val match = TacticalNameMatcher.match(name) ?: return null
        return VendorScanResult(match.deviceType, match.modelName, extraInfo = NAME_CONFIDENCE_LABEL)
    }

    private fun categoryToDeviceType(cat: TacticalCategory): DeviceType {
        return when (cat) {
            TacticalCategory.BODY_CAMERA -> DeviceType.BODY_CAMERA
            TacticalCategory.HOLSTER_SENSOR -> DeviceType.HOLSTER_SENSOR
            TacticalCategory.TACTICAL_AUDIO -> DeviceType.TACTICAL_AUDIO
            TacticalCategory.TACTICAL_RADIO -> DeviceType.TACTICAL_RADIO
            TacticalCategory.SMART_WEAPON -> DeviceType.SMART_WEAPON
            TacticalCategory.TACTICAL_EUD -> DeviceType.TACTICAL_EUD
            TacticalCategory.FIRE_EMS -> DeviceType.MEDICAL
            TacticalCategory.POLICE_EQUIPMENT -> DeviceType.POLICE
            TacticalCategory.VEHICLE_ROUTER -> DeviceType.VEHICLE_ROUTER
            TacticalCategory.DOCUMENT_READER -> DeviceType.DOCUMENT_READER
            TacticalCategory.FIREFIGHTER -> DeviceType.FIREFIGHTER
        }
    }

    private companion object {
        private const val NAME_CONFIDENCE_LABEL = "Name match - medium confidence"
        private const val MEDIUM_CONFIDENCE_LABEL = "Medium confidence"
        private const val HIGH_CONFIDENCE_LABEL = "Service UUID match - high confidence"
    }
}
