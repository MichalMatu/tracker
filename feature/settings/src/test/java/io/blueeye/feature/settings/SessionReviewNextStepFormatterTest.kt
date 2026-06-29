package io.blueeye.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionReviewNextStepFormatterTest {
    @Test
    fun `format prioritizes suspicious review`() {
        assertEquals(
            "Review next: 2 suspicious devices in Details, then mark a verdict.",
            SessionReviewNextStepFormatter.format(
                stats(
                    counts =
                        SessionReviewCategoryCounts(
                            watchlist = 1,
                            suspicious = 2,
                            publicSafety = 1,
                        ),
                ),
            ),
        )
    }

    @Test
    fun `format prioritizes follow me alert history`() {
        assertEquals(
            "Review next: inspect 1 follow-me alert in Details with score and RSSI history.",
            SessionReviewNextStepFormatter.format(
                stats(
                    counts = SessionReviewCategoryCounts(suspicious = 2),
                    alerts = SessionAlertHistorySummary(followMeAlertCount = 1),
                ),
            ),
        )
    }

    @Test
    fun `format routes identity carryover for review before suspicious category`() {
        assertEquals(
            "Review next: inspect 2 identity carryovers in Details before trusting merged history.",
            SessionReviewNextStepFormatter.format(
                stats(
                    counts = SessionReviewCategoryCounts(suspicious = 1),
                    identity = SessionIdentityCarryoverSummary(deviceCount = 2),
                ),
            ),
        )
    }

    @Test
    fun `format adds rssi trend guidance to suspicious review`() {
        assertEquals(
            "Review next: 1 suspicious device in Details; compare RSSI trend before marking a verdict.",
            SessionReviewNextStepFormatter.format(
                stats(
                    counts = SessionReviewCategoryCounts(suspicious = 1),
                    trends = SessionRssiTrendSummary(strengtheningCount = 1),
                ),
            ),
        )
    }

    @Test
    fun `format routes strengthening rssi trend for review`() {
        assertEquals(
            "Review next: compare 2 strengthening RSSI trends in Details before marking a verdict.",
            SessionReviewNextStepFormatter.format(
                stats(
                    trends = SessionRssiTrendSummary(strengtheningCount = 2),
                ),
            ),
        )
    }

    @Test
    fun `format routes public safety signals as evidence only`() {
        assertEquals(
            "Review next: inspect 1 public-safety-like signal as evidence, not proof.",
            SessionReviewNextStepFormatter.format(
                stats(counts = SessionReviewCategoryCounts(publicSafety = 1)),
            ),
        )
    }

    @Test
    fun `format routes public safety alert history as evidence only`() {
        assertEquals(
            "Review next: inspect 1 public-safety-like alert as evidence, not proof.",
            SessionReviewNextStepFormatter.format(
                stats(
                    alerts = SessionAlertHistorySummary(publicSafetySignalCount = 1),
                ),
            ),
        )
    }

    @Test
    fun `format returns blank when there is nothing actionable`() {
        assertEquals(
            "",
            SessionReviewNextStepFormatter.format(
                stats(counts = SessionReviewCategoryCounts(nearby = 4)),
            ),
        )
    }

    private fun stats(
        counts: SessionReviewCategoryCounts = SessionReviewCategoryCounts(),
        trends: SessionRssiTrendSummary = SessionRssiTrendSummary(),
        alerts: SessionAlertHistorySummary = SessionAlertHistorySummary(),
        identity: SessionIdentityCarryoverSummary = SessionIdentityCarryoverSummary(),
    ): SessionStats =
        SessionStats(
            reviewCategoryCounts = counts,
            rssiTrendSummary = trends,
            alertHistorySummary = alerts,
            identityCarryoverSummary = identity,
        )
}
