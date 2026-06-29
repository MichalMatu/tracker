package io.blueeye.core.data.classifier.ble

import io.blueeye.core.model.DeviceType

/** Classifies device based on advertised Service UUIDs. Constants are defined in [ServiceUuids]. */
object ServiceUuidClassifier {
    /** Result of UUID-based classification. */
    data class UuidClassification(
        val deviceType: DeviceType,
        val isTracker: Boolean = false,
        val isBeacon: Boolean = false,
        val isExposureNotification: Boolean = false,
        val serviceName: String? = null,
        val confidence: Float = 0.5f,
    )

    fun classify(serviceUuids: List<String>?): UuidClassification {
        val normalizedUuids = serviceUuids?.map { normalizeUuid(it) }
            ?: return UuidClassification(DeviceType.UNKNOWN)

        return checkTrackers(normalizedUuids)
            ?: checkExposure(normalizedUuids)
            ?: checkSpecialServices(normalizedUuids)
            ?: checkVendorPatterns(normalizedUuids)
            ?: UuidClassification(DeviceType.UNKNOWN)
    }

    private fun checkExposure(normalizedUuids: List<String>): UuidClassification? {
        return if (normalizedUuids.contains(ServiceUuids.UUID_EXPOSURE_NOTIFICATION)) {
            UuidClassification(
                deviceType = DeviceType.BEACON,
                isExposureNotification = true,
                serviceName = "Exposure Notification",
                confidence = 1.0f,
            )
        } else {
            null
        }
    }

    private fun checkTrackers(normalizedUuids: List<String>): UuidClassification? {
        return when {
            normalizedUuids.contains(ServiceUuids.UUID_TILE) -> UuidClassification(
                deviceType = DeviceType.TILE,
                isTracker = true,
                serviceName = "Tile Tracker",
                confidence = 1.0f,
            )
            normalizedUuids.contains(ServiceUuids.UUID_SAMSUNG_SMARTTAG) -> UuidClassification(
                deviceType = DeviceType.SAMSUNG_TAG,
                isTracker = true,
                serviceName = "Samsung SmartTag",
                confidence = 0.8f,
            )
            normalizedUuids.contains(ServiceUuids.UUID_TESLA_PHONE_KEY) -> UuidClassification(
                deviceType = DeviceType.CAR,
                serviceName = "Tesla Phone Key",
                confidence = 1.0f,
            )
            normalizedUuids.any { it in setOf(ServiceUuids.UUID_CHIPOLO, ServiceUuids.UUID_CHIPOLO_CLASSIC) } ->
                UuidClassification(
                    deviceType = DeviceType.TAG,
                    isTracker = true,
                    serviceName = "Chipolo Tracker",
                    confidence = 1.0f,
                )
            else -> null
        }
    }

    private fun checkSpecialServices(normalizedUuids: List<String>): UuidClassification? {
        return when {
            normalizedUuids.contains(ServiceUuids.UUID_EDDYSTONE) -> UuidClassification(
                deviceType = DeviceType.BEACON,
                isBeacon = true,
                serviceName = "Google Eddystone",
                confidence = 1.0f,
            )
            normalizedUuids.any { it in ServiceUuids.FITNESS_UUIDS } -> UuidClassification(
                deviceType = DeviceType.WEARABLE,
                serviceName = "Fitness Device",
                confidence = 0.9f,
            )
            normalizedUuids.any { it in ServiceUuids.HEALTH_UUIDS } -> UuidClassification(
                deviceType = DeviceType.UNKNOWN,
                serviceName = "Health Device",
                confidence = 0.8f,
            )
            normalizedUuids.any { it in ServiceUuids.AUDIO_UUIDS } -> UuidClassification(
                deviceType = DeviceType.HEADPHONES,
                serviceName = "LE Audio Device",
                confidence = 0.9f,
            )
            normalizedUuids.contains(ServiceUuids.UUID_HUMAN_INTERFACE_DEVICE) -> UuidClassification(
                deviceType = DeviceType.UNKNOWN,
                serviceName = "HID Device",
                confidence = 0.7f,
            )
            else -> null
        }
    }

    private fun checkVendorPatterns(normalizedUuids: List<String>): UuidClassification? {
        return when {
            normalizedUuids.any { it.contains("7905f431") || it.contains("89d3502b") } -> UuidClassification(
                deviceType = DeviceType.PHONE,
                serviceName = "Apple Device",
                confidence = 0.7f,
            )
            normalizedUuids.any { it.startsWith("0000fe9f") || it.contains("fe9f") } -> UuidClassification(
                deviceType = DeviceType.LAPTOP,
                serviceName = "Google Device",
                confidence = 0.6f,
            )
            normalizedUuids.any { it.contains("0000fd6f") || it.contains("fd6f") } -> UuidClassification(
                deviceType = DeviceType.BEACON,
                isExposureNotification = true,
                serviceName = "Exposure Notification",
                confidence = 1.0f,
            )
            normalizedUuids.any { it.contains("adabfb00") } -> UuidClassification(
                deviceType = DeviceType.WEARABLE,
                serviceName = "Fitbit",
                confidence = 0.95f,
            )
            normalizedUuids.contains(ServiceUuids.UUID_GOOGLE_FAST_PAIR) -> UuidClassification(
                deviceType = DeviceType.UNKNOWN,
                serviceName = "Google Fast Pair Device",
                confidence = 0.9f,
            )
            normalizedUuids.contains(ServiceUuids.UUID_AMAZON_AMA) -> UuidClassification(
                deviceType = DeviceType.SMART_HOME,
                serviceName = "Amazon AMA / Sidewalk",
                confidence = 0.9f,
            )
            normalizedUuids.contains(ServiceUuids.UUID_BOSE) -> UuidClassification(
                deviceType = DeviceType.HEADPHONES,
                serviceName = "Bose Device (Service)",
                confidence = 0.9f,
            )
            normalizedUuids.contains(ServiceUuids.UUID_MEATER_PLUS) -> UuidClassification(
                deviceType = DeviceType.SMART_HOME,
                serviceName = "Meater+",
                confidence = 0.9f,
            )
            else -> null
        }
    }

    /** Normalize UUID - extract 16-bit portion from 128-bit or lowercase 16-bit. */
    private fun normalizeUuid(uuid: String): String {
        val lower = uuid.lowercase().replace("-", "")

        return when {
            // If it's a standard Bluetooth UUID (xxxxxxxx-0000-1000-8000-00805f9b34fb)
            // extract the first 8 characters and then the 4-character 16-bit UUID
            lower.length == FULL_UUID_LEN && lower.endsWith(BT_BASE_UUID_SUFFIX) -> {
                lower.substring(UUID_16_START, UUID_16_END)
            }
            // If already 4 characters, it's a 16-bit UUID
            lower.length == SHORT_UUID_LEN -> lower
            else -> lower
        }
    }

    private const val FULL_UUID_LEN = 32
    private const val SHORT_UUID_LEN = 4
    private const val UUID_16_START = 4
    private const val UUID_16_END = 8
    private const val BT_BASE_UUID_SUFFIX = "1000800000805f9b34fb"

    /** Quick check: Is this likely a known tracker? */
    fun isKnownTracker(serviceUuids: List<String>?): Boolean {
        if (serviceUuids.isNullOrEmpty()) return false
        val normalized = serviceUuids.map { normalizeUuid(it) }
        return normalized.contains(ServiceUuids.UUID_TILE) ||
            normalized.contains(ServiceUuids.UUID_SAMSUNG_SMARTTAG)
    }

    /** Quick check: Is this Exposure Notification (should potentially filter)? */
    fun isExposureNotification(serviceUuids: List<String>?): Boolean {
        if (serviceUuids.isNullOrEmpty()) return false
        return serviceUuids.any { normalizeUuid(it) == ServiceUuids.UUID_EXPOSURE_NOTIFICATION }
    }

    /** Get simple DeviceType without full classification. */
    fun getDeviceType(serviceUuids: List<String>?): DeviceType {
        return classify(serviceUuids).deviceType
    }
}
