package io.blueeye.feature.settings

internal object SessionReviewNextStepFormatter {
    fun format(stats: SessionStats): String {
        val counts = stats.reviewCategoryCounts
        val trendSummary = stats.rssiTrendSummary
        val alertHistorySummary = stats.alertHistorySummary
        val identityCarryoverSummary = stats.identityCarryoverSummary
        return format(counts, trendSummary, alertHistorySummary, identityCarryoverSummary)
    }

    private fun format(
        counts: SessionReviewCategoryCounts,
        trendSummary: SessionRssiTrendSummary,
        alertHistorySummary: SessionAlertHistorySummary,
        identityCarryoverSummary: SessionIdentityCarryoverSummary,
    ): String =
        when {
            alertHistorySummary.followMeAlertCount > 0 ->
                "Review next: inspect ${alertHistorySummary.followMeAlertCount.alertText("follow-me")} " +
                    "in Details with score and RSSI history."
            identityCarryoverSummary.deviceCount > 0 ->
                "Review next: inspect ${identityCarryoverSummary.deviceCount.carryoverText()} " +
                    "in Details before trusting merged history."
            counts.suspicious > 0 ->
                suspiciousReviewText(counts.suspicious, trendSummary)
            trendSummary.strengtheningCount > 0 ->
                "Review next: compare ${trendSummary.strengtheningCount.trendText()} " +
                    "in Details before marking a verdict."
            alertHistorySummary.watchlistReturnCount > 0 ->
                "Review next: confirm ${alertHistorySummary.watchlistReturnCount.alertText("watchlist return")} " +
                    "was expected."
            counts.watchlist > 0 ->
                "Review next: confirm ${counts.watchlist.deviceText("watchlist")} still need return alerts."
            alertHistorySummary.publicSafetySignalCount > 0 ->
                "Review next: inspect " +
                    alertHistorySummary.publicSafetySignalCount.alertText("public-safety-like") +
                    " as evidence, not proof."
            counts.publicSafety > 0 ->
                "Review next: inspect ${counts.publicSafety.signalText("public-safety-like")} as evidence, not proof."
            counts.unknownNoise > 0 ->
                "Review next: mark repeated noise as False Positive or Known Safe."
            else -> ""
        }
}

private fun suspiciousReviewText(
    count: Int,
    trendSummary: SessionRssiTrendSummary,
): String =
    if (trendSummary.strengtheningCount > 0) {
        "Review next: ${count.deviceText("suspicious")} in Details; " +
            "compare RSSI trend before marking a verdict."
    } else {
        "Review next: ${count.deviceText("suspicious")} in Details, then mark a verdict."
    }

private fun Int.trendText(): String =
    if (this == 1) {
        "1 strengthening RSSI trend"
    } else {
        "$this strengthening RSSI trends"
    }

private fun Int.alertText(label: String): String =
    if (this == 1) {
        "1 $label alert"
    } else {
        "$this $label alerts"
    }

private fun Int.carryoverText(): String =
    if (this == 1) {
        "1 identity carryover"
    } else {
        "$this identity carryovers"
    }

private fun Int.deviceText(label: String): String =
    if (this == 1) {
        "1 $label device"
    } else {
        "$this $label devices"
    }

private fun Int.signalText(label: String): String =
    if (this == 1) {
        "1 $label signal"
    } else {
        "$this $label signals"
    }
