package io.blueeye.core.data.classifier.pipeline

import io.blueeye.core.model.DeviceType
import java.util.Locale

object ModelClassifier {
    fun classify(modelName: String?): DeviceType {
        if (modelName.isNullOrBlank()) return DeviceType.UNKNOWN

        val lower = modelName.lowercase(Locale.ROOT)

        return when {
            lower.contains("macbook") -> DeviceType.LAPTOP
            lower.contains("iphone") -> DeviceType.PHONE
            lower.contains("ipad") -> DeviceType.TABLET
            lower.contains("watch") -> DeviceType.WATCH
            lower.contains("airpods") -> DeviceType.HEADPHONES
            lower.contains("tv") && !lower.contains("tv stick") -> DeviceType.TV
            lower.contains("flip") || lower.contains("charge") || lower.contains("boom") ->
                DeviceType.SPEAKER
            else -> DeviceType.UNKNOWN
        }
    }
}
