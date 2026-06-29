package io.blueeye.core.data.classifier

import io.blueeye.core.data.classifier.ble.BleAppearanceClassifier
import io.blueeye.core.data.classifier.ble.ServiceUuidClassifier
import io.blueeye.core.data.classifier.model.BleClassificationInput
import io.blueeye.core.data.classifier.pipeline.CoDClassifier
import io.blueeye.core.data.classifier.pipeline.GattServicesStrategy
import io.blueeye.core.data.classifier.pipeline.NameClassifier
import io.blueeye.core.data.classifier.pipeline.VendorClassifier
import io.blueeye.core.data.classifier.vendor.ManufacturerIds
import io.blueeye.core.data.tracker.fingerprint.KnownDeviceFingerprints
import io.blueeye.core.model.DeviceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device classifier facade that orchestrates various classification strategies.
 *
 * It delegates logic to specialized classifiers:
 * - [CoDClassifier] for Class of Device
 * - [NameClassifier] for name-based heuristics
 * - [VendorClassifier] for vendor-based heuristics
 * - [AppleContinuityParser], [ServiceUuidClassifier], [BleAppearanceClassifier] for BLE specifics
 *
 * Reference: Bluetooth SIG Assigned Numbers, Section 2.8
 */
@Singleton
class DeviceClassifier @Inject constructor(
    private val appleContinuityParser: io.blueeye.core.decoders.parser.apple.AppleContinuityParser
) {
    /**
     * Classify a Bluetooth device based on its Class of Device (CoD).
     *
     * @param classOfDevice The raw CoD value from BluetoothClass or discovery
     * @return The determined DeviceType
     */
    fun classifyByCoD(classOfDevice: Int?): DeviceType {
        return CoDClassifier.classify(classOfDevice)
    }

    /**
     * Classify by device name heuristics (fallback when CoD is unavailable). Use with caution -
     * names can be spoofed.
     */
    fun classifyByName(deviceName: String?): DeviceType {
        return NameClassifier.classify(deviceName)
    }

    /** Combined classification using CoD first, then name, then vendor as fallback. */
    fun classify(
        classOfDevice: Int?,
        deviceName: String?,
        vendorName: String? = null,
    ): DeviceType {
        val codResult = CoDClassifier.classify(classOfDevice)
        if (codResult != DeviceType.UNKNOWN) return codResult

        return NameClassifier.classify(deviceName).takeIf { it != DeviceType.UNKNOWN }
            ?: VendorClassifier.classify(vendorName)
    }

    /**
     * Comprehensive BLE classification using all available data sources.
     *
     * Priority order:
     * 1. Apple family names when Apple payload decoding conflicts with the visible identity
     * 2. Apple Continuity Protocol (AirTag, AirPods detection)
     * 3. Service UUIDs (Tile, beacons, fitness)
     * 4. Service/manufacturer-data fingerprints
     * 5. BLE Appearance
     * 6. Device Name heuristics
     * 7. Vendor Name fallback
     *
     * @param input Classification input data
     * @return Best-effort DeviceType classification
     */
    fun classifyBle(input: BleClassificationInput): DeviceType {
        var result = DeviceType.UNKNOWN
        val nameType = classifyByName(input.deviceName)

        // 1. Apple Continuity Protocol (highest priority for Apple devices)
        val appleManufacturerData = input.manufacturerRecords[ManufacturerIds.APPLE]
        if (appleManufacturerData != null) {
            val appleInfo = appleContinuityParser.parse(appleManufacturerData)
            result = mapAppleToDeviceType(appleInfo)
            AppleIdentityConflictGuard
                .preferredNameTypeForConflict(input.deviceName, result)
                ?.let { result = it }
        }

        // 2. Service UUIDs (trackers, fitness, beacons)
        if (result == DeviceType.UNKNOWN) {
            result = ServiceUuidClassifier.classify(input.serviceUuids).deviceType
        }

        // 3. Precise service/manufacturer-data fingerprints
        if (result == DeviceType.UNKNOWN) {
            result = classifyKnownFingerprint(input)
            AppleIdentityConflictGuard
                .preferredNameTypeForConflict(input.deviceName, result)
                ?.let { result = it }
        }

        // 4. BLE Appearance
        if (result == DeviceType.UNKNOWN) {
            result = BleAppearanceClassifier.classify(input.appearance)
        }

        // 5. Device Name heuristics
        if (result == DeviceType.UNKNOWN) {
            result = nameType
        }

        // 6. Vendor Name fallback
        if (result == DeviceType.UNKNOWN) {
            result = VendorClassifier.classify(input.vendorName)
        }

        return result
    }

    private fun classifyKnownFingerprint(input: BleClassificationInput): DeviceType {
        val model = KnownDeviceFingerprints.identify(input.serviceDataByUuid, input.manufacturerRecords)
            ?: return DeviceType.UNKNOWN
        val normalizedModel = model.lowercase()

        return fingerprintTypeRules
            .firstOrNull { rule -> rule.keywords.any(normalizedModel::contains) }
            ?.deviceType
            ?: DeviceType.UNKNOWN
    }

    private companion object {
        private val fingerprintTypeRules =
            listOf(
                FingerprintTypeRule(listOf("sony", "bose", "buds", "airpods"), DeviceType.HEADPHONES),
                FingerprintTypeRule(listOf("tile"), DeviceType.TILE),
                FingerprintTypeRule(listOf("chipolo"), DeviceType.TAG),
                FingerprintTypeRule(listOf("eddystone", "exposure notification", "altbeacon"), DeviceType.BEACON),
                FingerprintTypeRule(listOf("tesla"), DeviceType.CAR),
                FingerprintTypeRule(listOf("fitbit"), DeviceType.WEARABLE),
                FingerprintTypeRule(listOf("sensortag"), DeviceType.SENSOR),
                FingerprintTypeRule(listOf("alexa", "amazon sidewalk"), DeviceType.SMART_HOME),
            )
    }

    private data class FingerprintTypeRule(
        val keywords: List<String>,
        val deviceType: DeviceType,
    )

    private fun mapAppleToDeviceType(info: io.blueeye.core.decoders.parser.apple.AppleDeviceData?): DeviceType {
        if (info == null) return DeviceType.UNKNOWN
        return when (info.deviceModel) {
            "iPhone" -> DeviceType.PHONE
            "iPad", "iPad Pro" -> DeviceType.TABLET
            "Apple Watch" -> DeviceType.WEARABLE
            "MacBook", "Mac" -> DeviceType.LAPTOP
            "AirPods", "AirPods Pro", "AirPods Max" -> DeviceType.HEADPHONES
            "HomePod" -> DeviceType.AUDIO_VIDEO
            "Apple TV" -> DeviceType.AUDIO_VIDEO
            "AirTag", "Find My" -> DeviceType.TRACKER
            else -> DeviceType.UNKNOWN
        }
    }

    fun classifyByGattServices(servicesString: String?, deviceName: String? = null): io.blueeye.core.data.classifier.vendor.VendorScanResult? {
        if (servicesString.isNullOrBlank()) return null

        // Parse services string (format: uuid:[char];uuid:[char]...)
        // We just need the UUIDs for GattServicesStrategy
        val serviceEntries = servicesString.split(";")
        val serviceUuids = serviceEntries.mapNotNull {
            val parts = it.split(":")
            if (parts.isNotEmpty()) parts[0] else null
        }

        return GattServicesStrategy.classify(serviceUuids, deviceName)
    }
}
