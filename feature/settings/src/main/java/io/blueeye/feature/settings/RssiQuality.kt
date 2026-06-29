package io.blueeye.feature.settings

import io.blueeye.core.model.SignalSample
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class RssiQualityStats(
    val sampleCount: Int = 0,
    val uniqueRssiCount: Int = 0,
    val dominantRssi: Int? = null,
    val dominantRssiCount: Int? = null,
    val dominantRssiShare: Double? = null,
    val warnings: List<RssiQualityWarning> = emptyList(),
)

data class RssiTrendStats(
    val direction: RssiTrendDirection = RssiTrendDirection.INSUFFICIENT,
    val firstWindowAverageRssi: Double? = null,
    val lastWindowAverageRssi: Double? = null,
    val deltaRssi: Double? = null,
    val firstWindowSampleCount: Int = 0,
    val lastWindowSampleCount: Int = 0,
)

data class SessionRssiTrendSummary(
    val deviceCount: Int = 0,
    val strengtheningCount: Int = 0,
    val weakeningCount: Int = 0,
    val stableCount: Int = 0,
    val insufficientCount: Int = 0,
    val strongestStrengtheningDeltaRssi: Double? = null,
)

enum class RssiQualityWarning {
    TOO_FEW_SAMPLES,
    FLAT_RSSI_PATTERN,
}

enum class RssiTrendDirection {
    INSUFFICIENT,
    STABLE,
    STRENGTHENING,
    WEAKENING,
}

internal object RssiQualityAnalyzer {
    fun analyze(samples: List<SignalSample>): RssiQualityStats {
        val dominant =
            samples
                .groupingBy { sample -> sample.rssi }
                .eachCount()
                .maxWithOrNull(
                    compareBy<Map.Entry<Int, Int>> { entry -> entry.value }
                        .thenBy { entry -> entry.key },
                )
                ?.let { entry -> entry.key to entry.value }
        val dominantShare = dominant?.let { (_, count) -> count.toDouble() / samples.size }
        val warnings =
            listOfNotNull(
                RssiQualityWarning.TOO_FEW_SAMPLES.takeIf {
                    samples.size in 1 until RssiQualityThresholds.MIN_REVIEW_SAMPLES
                },
                RssiQualityWarning.FLAT_RSSI_PATTERN.takeIf {
                    samples.size >= RssiQualityThresholds.MIN_FLAT_PATTERN_SAMPLES &&
                        dominantShare != null &&
                        dominantShare >= RssiQualityThresholds.FLAT_PATTERN_SHARE
                },
            )

        return RssiQualityStats(
            sampleCount = samples.size,
            uniqueRssiCount = samples.map { sample -> sample.rssi }.distinct().size,
            dominantRssi = dominant?.first,
            dominantRssiCount = dominant?.second,
            dominantRssiShare = dominantShare,
            warnings = warnings,
        )
    }
}

internal object RssiTrendAnalyzer {
    fun analyze(samples: List<SignalSample>): RssiTrendStats {
        if (samples.size < RssiTrendThresholds.MIN_TREND_SAMPLES) return RssiTrendStats()

        val orderedSamples = samples.sortedBy { sample -> sample.timestamp }
        val windowSize = orderedSamples.size / 2
        val firstWindow = orderedSamples.take(windowSize)
        val lastWindow = orderedSamples.takeLast(windowSize)
        val firstAverage = firstWindow.averageRssi()
        val lastAverage = lastWindow.averageRssi()
        val delta = lastAverage - firstAverage

        return RssiTrendStats(
            direction = delta.direction,
            firstWindowAverageRssi = firstAverage,
            lastWindowAverageRssi = lastAverage,
            deltaRssi = delta,
            firstWindowSampleCount = firstWindow.size,
            lastWindowSampleCount = lastWindow.size,
        )
    }
}

internal object SessionRssiTrendSummaryAnalyzer {
    fun analyze(samples: List<SignalSample>): SessionRssiTrendSummary {
        val trends =
            samples
                .groupBy { sample -> sample.deviceFingerprint }
                .values
                .map { deviceSamples -> RssiTrendAnalyzer.analyze(deviceSamples) }
        if (trends.isEmpty()) return SessionRssiTrendSummary()

        return SessionRssiTrendSummary(
            deviceCount = trends.size,
            strengtheningCount = trends.countDirection(RssiTrendDirection.STRENGTHENING),
            weakeningCount = trends.countDirection(RssiTrendDirection.WEAKENING),
            stableCount = trends.countDirection(RssiTrendDirection.STABLE),
            insufficientCount = trends.countDirection(RssiTrendDirection.INSUFFICIENT),
            strongestStrengtheningDeltaRssi =
                trends
                    .filter { trend -> trend.direction == RssiTrendDirection.STRENGTHENING }
                    .mapNotNull { trend -> trend.deltaRssi }
                    .maxOrNull(),
        )
    }
}

internal fun RssiQualityStats.toJson(): JsonObject =
    buildJsonObject {
        put("uniqueRssiCount", uniqueRssiCount)
        putNullableNumber("dominantRssi", dominantRssi)
        putNullableNumber("dominantRssiCount", dominantRssiCount)
        putNullableNumber("dominantRssiShare", dominantRssiShare)
        put(
            "warnings",
            JsonArray(warnings.map { warning -> JsonPrimitive(warning.name) }),
        )
    }

internal fun RssiTrendStats.toJson(): JsonObject =
    buildJsonObject {
        put("direction", direction.name)
        putNullableNumber("firstWindowAverageRssi", firstWindowAverageRssi)
        putNullableNumber("lastWindowAverageRssi", lastWindowAverageRssi)
        putNullableNumber("deltaRssi", deltaRssi)
        put("firstWindowSampleCount", firstWindowSampleCount)
        put("lastWindowSampleCount", lastWindowSampleCount)
    }

private fun List<RssiTrendStats>.countDirection(direction: RssiTrendDirection): Int {
    return count { trend -> trend.direction == direction }
}

private fun List<SignalSample>.averageRssi(): Double = sumOf { sample -> sample.rssi }.toDouble() / size

private val Double.direction: RssiTrendDirection
    get() =
        when {
            this >= RssiTrendThresholds.MIN_DIRECTION_DELTA -> RssiTrendDirection.STRENGTHENING
            this <= -RssiTrendThresholds.MIN_DIRECTION_DELTA -> RssiTrendDirection.WEAKENING
            else -> RssiTrendDirection.STABLE
        }

private fun JsonObjectBuilder.putNullableNumber(
    key: String,
    value: Number?,
) {
    if (value == null) {
        put(key, JsonNull)
    } else {
        put(key, value)
    }
}

private object RssiQualityThresholds {
    const val MIN_REVIEW_SAMPLES = 3
    const val MIN_FLAT_PATTERN_SAMPLES = 6
    const val FLAT_PATTERN_SHARE = 0.85
}

private object RssiTrendThresholds {
    const val MIN_TREND_SAMPLES = 4
    const val MIN_DIRECTION_DELTA = 3.0
}
