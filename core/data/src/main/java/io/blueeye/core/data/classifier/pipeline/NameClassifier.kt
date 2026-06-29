package io.blueeye.core.data.classifier.pipeline

import io.blueeye.core.model.DeviceType

/**
 * Strategy for classifying devices based on their name heuristics.
 *
 * Use with caution - names can be spoofed.
 */
object NameClassifier {
    /** Classify by device name heuristics (fallback when CoD is unavailable). */
    /** Classify by device name heuristics (fallback when CoD is unavailable). */
    fun classify(deviceName: String?): DeviceType {
        if (deviceName == null) return DeviceType.UNKNOWN
        val lowerName = deviceName.lowercase()

        return checkHeadphones(lowerName)
            ?: checkWearables(lowerName)
            ?: checkTrackers(lowerName)
            ?: checkPhones(lowerName)
            ?: checkComputers(lowerName)
            ?: checkTv(lowerName)
            ?: DeviceType.UNKNOWN
    }

    private fun checkHeadphones(name: String): DeviceType? {
        val keywords = listOf(
            "airpod", "headphone", "earphone", "earbud", "headset",
            "buds", "bose", "beats", "jabra", "sennheiser",
            "wh-1000", "wf-1000"
        )
        if (keywords.any { name.contains(it) }) {
            return DeviceType.HEADPHONES
        }
        return null
    }

    private fun checkWearables(name: String): DeviceType? {
        val keywords = listOf("watch", "band", "fitbit", "garmin", "amazfit", "mi band")
        if (keywords.any { name.contains(it) }) {
            return DeviceType.WEARABLE
        }
        return null
    }

    private fun checkPhones(name: String): DeviceType? {
        val isGalaxyPhone = name.contains("galaxy") && !name.contains("buds")
        val keywords = listOf("iphone", "pixel", "oneplus", "huawei", "xiaomi")

        return if (isGalaxyPhone || keywords.any { name.contains(it) }) {
            DeviceType.PHONE
        } else {
            null
        }
    }

    private fun checkComputers(name: String): DeviceType? {
        val keywords = listOf("macbook", "laptop", "thinkpad", "surface")
        if (keywords.any { name.contains(it) }) {
            return DeviceType.LAPTOP
        }
        return null
    }

    private fun checkTv(name: String): DeviceType? {
        val isLgTv = name.contains("[lg]") && name.contains("tv")
        val keywords = listOf("[tv]", "android tv", "webos", "roku", "fire tv", "chromecast")

        return if (isLgTv || keywords.any { name.contains(it) }) {
            DeviceType.TV
        } else {
            null
        }
    }

    private fun checkTrackers(name: String): DeviceType? {
        return when {
            name.contains("airtag") -> DeviceType.AIRTAG
            name.contains("tile") -> DeviceType.TILE
            name.contains("smarttag") -> DeviceType.SAMSUNG_TAG
            else -> null
        }
    }
}
