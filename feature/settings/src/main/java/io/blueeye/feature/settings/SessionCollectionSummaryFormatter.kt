package io.blueeye.feature.settings

internal object SessionCollectionSummaryFormatter {
    fun format(stats: SessionStats): String =
        if (stats.hasStarted) {
            "Collected: ${stats.deviceCount} devices / ${stats.sampleCount} RSSI samples / " +
                "${stats.evidenceCount} evidence / ${stats.attentionEvidenceCount} review signals" +
                stats.durationText()
        } else {
            "Collected: no active session yet"
        }

    private fun SessionStats.durationText(): String {
        if (durationMs <= 0L) return ""

        val minutes = durationMs / MILLIS_PER_MINUTE
        val hours = durationMs / MILLIS_PER_HOUR
        val value =
            when {
                hours > 0L -> "${hours}h"
                minutes > 0L -> "${minutes}m"
                else -> "${durationMs / MILLIS_PER_SECOND}s"
            }
        return " / over $value"
    }

    private const val MILLIS_PER_SECOND = 1_000L
    private const val MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND
    private const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
}
