package io.blueeye.feature.details

import io.blueeye.core.model.FollowMeHistorySample
import io.blueeye.core.model.TrackingStatus
import kotlin.math.roundToInt

object DetailsFollowMeHistoryFormatter {
    fun format(
        samples: List<FollowMeHistorySample>,
        timestampFormatter: (Long) -> String = DetailsUiFormatter::formatFriendlyTimestamp,
    ): DetailsFollowMeHistoryUiInfo? {
        if (samples.isEmpty()) return null

        val sortedSamples = samples.sortedBy { it.timestamp }
        val firstSample = sortedSamples.first()
        val latestSample = sortedSamples.last()
        val peakSample = sortedSamples.maxBy { it.score }

        return DetailsFollowMeHistoryUiInfo(
            sampleCountText = sampleCountText(sortedSamples.size),
            windowText = windowText(firstSample, latestSample, timestampFormatter),
            latestScoreText = "Latest ${latestSample.score.roundToInt()}/100",
            peakScoreText = "Peak ${peakSample.score.roundToInt()}/100",
            latestStatus = latestSample.trackingStatus,
            latestStatusText = latestSample.trackingStatus.displayText(),
            movementText = movementText(latestSample),
            componentText = componentText(latestSample),
            latestExplanation = latestSample.explanation ?: "No score explanation recorded.",
            recentItems = sortedSamples.takeLast(RECENT_ITEM_COUNT).reversed(),
        )
    }

    private fun sampleCountText(sampleCount: Int): String =
        if (sampleCount == 1) {
            "1 score snapshot"
        } else {
            "$sampleCount score snapshots"
        }

    private fun windowText(
        firstSample: FollowMeHistorySample,
        latestSample: FollowMeHistorySample,
        timestampFormatter: (Long) -> String,
    ): String =
        if (firstSample.timestamp == latestSample.timestamp) {
            "Observed ${timestampFormatter(latestSample.timestamp)}"
        } else {
            "First ${timestampFormatter(firstSample.timestamp)} / latest ${timestampFormatter(latestSample.timestamp)}"
        }

    private fun movementText(sample: FollowMeHistorySample): String {
        val movement =
            when (sample.userMoved) {
                true -> "movement observed"
                false -> "movement not observed"
                null -> "movement unknown"
            }
        val baseline =
            when (sample.baselineDevice) {
                true -> "baseline device"
                false -> "not baseline"
                null -> "baseline unknown"
            }
        return "$movement / $baseline"
    }

    private fun componentText(sample: FollowMeHistorySample): String {
        val components =
            listOf(
                "duration" to sample.durationScore,
                "RSSI" to sample.rssiStabilityScore,
                "type" to sample.deviceTypeScore,
                "MAC" to sample.macBehaviorScore,
                "encounters" to sample.encounterScore,
            )
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(COMPONENT_LIMIT)

        if (components.isEmpty()) return "No positive score components"

        return components.joinToString { (name, score) -> "$name +$score" }
    }

    private fun TrackingStatus.displayText(): String =
        when (this) {
            TrackingStatus.SAFE -> "Safe"
            TrackingStatus.SUSPICIOUS -> "Suspicious"
            TrackingStatus.DANGEROUS -> "High attention"
        }

    private const val RECENT_ITEM_COUNT = 3
    private const val COMPONENT_LIMIT = 3
}

data class DetailsFollowMeHistoryUiInfo(
    val sampleCountText: String,
    val windowText: String,
    val latestScoreText: String,
    val peakScoreText: String,
    val latestStatus: TrackingStatus,
    val latestStatusText: String,
    val movementText: String,
    val componentText: String,
    val latestExplanation: String,
    val recentItems: List<FollowMeHistorySample>,
)
