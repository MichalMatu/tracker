package io.blueeye.feature.settings

import kotlin.math.roundToInt

internal object SessionRssiQualityFormatter {
    fun format(quality: RssiQualityStats): SessionRssiQualityUiInfo? {
        if (quality.sampleCount == 0) return null
        val warningText = quality.warningText
        return if (warningText != null) {
            SessionRssiQualityUiInfo(
                text = warningText,
                tone = SessionRssiQualityTone.WARNING,
            )
        } else {
            SessionRssiQualityUiInfo(
                text = "RSSI quality: ${quality.sampleCount} samples / ${quality.uniqueRssiCount} unique values",
                tone = SessionRssiQualityTone.NORMAL,
            )
        }
    }
}

internal data class SessionRssiQualityUiInfo(
    val text: String,
    val tone: SessionRssiQualityTone,
)

internal enum class SessionRssiQualityTone {
    NORMAL,
    WARNING,
}

private val RssiQualityStats.warningText: String?
    get() =
        when {
            RssiQualityWarning.FLAT_RSSI_PATTERN in warnings ->
                "RSSI quality: flat pattern; ${dominantRssi ?: "one RSSI"} dBm dominates " +
                    "$dominantPercentText of samples"
            RssiQualityWarning.TOO_FEW_SAMPLES in warnings ->
                "RSSI quality: only $sampleCount $sampleWord; collect at least 3"
            else -> null
        }

private val RssiQualityStats.sampleWord: String
    get() = if (sampleCount == 1) "sample" else "samples"

private val RssiQualityStats.dominantPercentText: String
    get() = "${((dominantRssiShare ?: 0.0) * 100).roundToInt()}%"
