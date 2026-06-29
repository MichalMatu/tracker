package io.blueeye.core.data.classifier.vendor.strategy

import io.blueeye.core.data.classifier.vendor.ManufacturerIds
import io.blueeye.core.data.classifier.vendor.VendorScanInput
import io.blueeye.core.data.classifier.vendor.VendorScanResult
import io.blueeye.core.data.classifier.vendor.VendorStrategy
import io.blueeye.core.decoders.parser.apple.AppleDeviceData
import io.blueeye.core.model.DeviceType
import javax.inject.Inject

/**
 * Consolidated Apple strategy. Delegates to AppleContinuityParser for Apple device classification.
 */
class AppleStrategy
@Inject
constructor(
    private val appleContinuityParser: io.blueeye.core.decoders.parser.apple.AppleContinuityParser,
) : VendorStrategy {
    override fun canHandle(input: VendorScanInput): Boolean {
        return input.hasManufacturer(ManufacturerIds.APPLE)
    }

    override fun decode(input: VendorScanInput): VendorScanResult {
        val data = input.manufacturerData(ManufacturerIds.APPLE)
        return data
            ?.let(appleContinuityParser::parse)
            .toVendorScanResult(hasAppleRecord = data != null)
    }

    private fun AppleDeviceData?.toVendorScanResult(hasAppleRecord: Boolean): VendorScanResult =
        when {
            !hasAppleRecord -> VendorScanResult()
            this == null -> VendorScanResult(modelName = "Apple Device", extraInfo = "Apple (Unknown Protocol)")
            else ->
                VendorScanResult(
                    deviceType = toDeviceType(),
                    modelName = deviceModel ?: "Apple Device",
                    extraInfo = buildExtraInfo().ifEmpty { "Apple Protocol" },
                    batteryLevel = batteryLevelLeft,
                )
        }

    private fun AppleDeviceData.toDeviceType(): DeviceType {
        return when {
            deviceModel?.contains("iPhone") == true -> DeviceType.PHONE
            deviceModel?.contains("iPad") == true -> DeviceType.TABLET
            deviceModel?.contains("Mac") == true -> DeviceType.LAPTOP
            deviceModel?.contains("Watch") == true -> DeviceType.WEARABLE
            deviceModel?.contains("AirPods") == true -> DeviceType.HEADPHONES
            findMyKey != null -> DeviceType.TAG
            else -> DeviceType.UNKNOWN
        }
    }

    private fun AppleDeviceData.buildExtraInfo(): String {
        return buildString {
            if (findMyKey != null) {
                append("Find My (Offline Finding). ")
            }
            if (airDropHash != null) {
                append("AirDrop (Hash Available). ")
            }
            statusFlags?.let { flags ->
                append("Status: 0x${flags.toString(RADIX_HEX)}. ")
            }
        }.trim()
    }

    private companion object {
        private const val RADIX_HEX = 16
    }
}
