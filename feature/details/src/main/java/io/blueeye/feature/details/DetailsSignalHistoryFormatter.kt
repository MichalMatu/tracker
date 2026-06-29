package io.blueeye.feature.details

import io.blueeye.core.model.SignalSample
import kotlin.math.roundToInt

object DetailsSignalHistoryFormatter {
    fun format(
        samples: List<SignalSample>,
        timestampFormatter: (Long) -> String = DetailsUiFormatter::formatFriendlyTimestamp,
    ): DetailsSignalHistoryUiInfo? {
        if (samples.isEmpty()) return null

        val sortedSamples = samples.sortedBy { it.timestamp }
        val rssiValues = sortedSamples.map { it.rssi }
        val firstSample = sortedSamples.first()
        val latestSample = sortedSamples.last()

        return DetailsSignalHistoryUiInfo(
            sampleCountText = sampleCountText(sortedSamples.size),
            windowText = windowText(firstSample, latestSample, timestampFormatter),
            latestRssiText = "Latest ${latestSample.rssi} dBm",
            averageRssiText = "Average ${rssiValues.average().roundToInt()} dBm",
            rangeText = "Range ${rssiValues.min()} to ${rssiValues.max()} dBm",
            trendText = trendText(firstSample, latestSample, sortedSamples.size),
            trendTone = trendTone(firstSample, latestSample, sortedSamples.size),
        )
    }

    private fun sampleCountText(sampleCount: Int): String =
        if (sampleCount == 1) {
            "1 RSSI sample"
        } else {
            "$sampleCount RSSI samples"
        }

    private fun windowText(
        firstSample: SignalSample,
        latestSample: SignalSample,
        timestampFormatter: (Long) -> String,
    ): String =
        if (firstSample.timestamp == latestSample.timestamp) {
            "Observed ${timestampFormatter(latestSample.timestamp)}"
        } else {
            "First ${timestampFormatter(firstSample.timestamp)} / latest ${timestampFormatter(latestSample.timestamp)}"
        }

    private fun trendText(
        firstSample: SignalSample,
        latestSample: SignalSample,
        sampleCount: Int,
    ): String {
        if (sampleCount < MIN_TREND_SAMPLES) return "Trend needs more samples"

        val delta = latestSample.rssi - firstSample.rssi
        val formattedDelta = delta.formatDelta()
        return when {
            delta >= TREND_THRESHOLD_DB -> "RSSI strengthening ($formattedDelta dB)"
            delta <= -TREND_THRESHOLD_DB -> "RSSI fading ($formattedDelta dB)"
            else -> "RSSI stable ($formattedDelta dB)"
        }
    }

    private fun trendTone(
        firstSample: SignalSample,
        latestSample: SignalSample,
        sampleCount: Int,
    ): DetailsSignalTrendTone {
        if (sampleCount < MIN_TREND_SAMPLES) return DetailsSignalTrendTone.INSUFFICIENT

        val delta = latestSample.rssi - firstSample.rssi
        return when {
            delta >= TREND_THRESHOLD_DB -> DetailsSignalTrendTone.STRENGTHENING
            delta <= -TREND_THRESHOLD_DB -> DetailsSignalTrendTone.FADING
            else -> DetailsSignalTrendTone.STABLE
        }
    }

    private fun Int.formatDelta(): String =
        if (this > 0) {
            "+$this"
        } else {
            toString()
        }

    private const val MIN_TREND_SAMPLES = 2
    private const val TREND_THRESHOLD_DB = 8
}

data class DetailsSignalHistoryUiInfo(
    val sampleCountText: String,
    val windowText: String,
    val latestRssiText: String,
    val averageRssiText: String,
    val rangeText: String,
    val trendText: String,
    val trendTone: DetailsSignalTrendTone,
)

enum class DetailsSignalTrendTone {
    STRENGTHENING,
    FADING,
    STABLE,
    INSUFFICIENT,
}
