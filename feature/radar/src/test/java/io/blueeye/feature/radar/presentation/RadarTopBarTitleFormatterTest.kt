package io.blueeye.feature.radar.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RadarTopBarTitleFormatterTest {
    @Test
    fun `count text separates seen devices from attention devices`() {
        val text =
            RadarTopBarTitleFormatter.countText(
                state(
                    deviceCount = 21,
                    summary = summary(attentionCount = 3),
                ),
            )

        assertEquals("3 need review / 21 seen", text)
        assertFalse(text.contains("devices nearby"))
    }

    @Test
    fun `count text shows no attention when everything is ordinary noise or nearby`() {
        val text =
            RadarTopBarTitleFormatter.countText(
                state(
                    deviceCount = 21,
                    summary =
                        summary(
                            headline = "No attention signals",
                            attentionCount = 0,
                        ),
                ),
            )

        assertEquals("21 seen / none need review", text)
    }

    @Test
    fun `count text uses singular review wording`() {
        val text =
            RadarTopBarTitleFormatter.countText(
                state(
                    deviceCount = 8,
                    summary = summary(attentionCount = 1),
                ),
            )

        assertEquals("1 needs review / 8 seen", text)
    }

    @Test
    fun `filtered count keeps total context while preserving review count`() {
        val text =
            RadarTopBarTitleFormatter.countText(
                state(
                    deviceCount = 5,
                    totalCount = 21,
                    isFilterActive = true,
                    summary = summary(attentionCount = 2),
                ),
            )

        assertEquals("2 need review / 5 of 21 shown", text)
    }

    @Test
    fun `summary text keeps evidence review language`() {
        val text =
            RadarTopBarTitleFormatter.summaryText(
                summary(
                    headline = "Public-safety-like signals need evidence review",
                    detail = "1 public safety / 20 noise",
                    attentionCount = 1,
                ),
            )

        assertEquals("Public-safety-like signals need evidence review - 1 public safety / 20 noise", text)
    }

    private fun state(
        deviceCount: Int,
        totalCount: Int = deviceCount,
        isFilterActive: Boolean = false,
        summary: RadarDecisionSummary?,
    ): RadarTopBarState =
        RadarTopBarState(
            deviceCount = deviceCount,
            totalCount = totalCount,
            isFilterActive = isFilterActive,
            isScanning = true,
            isBaselineActive = false,
            autoActiveProbeEnabled = false,
            decisionSummary = summary,
            filterCount = if (isFilterActive) 1 else 0,
        )

    private fun summary(
        headline: String = "Review tracker-like evidence",
        detail: String = "1 suspicious",
        attentionCount: Int,
    ): RadarDecisionSummary =
        RadarDecisionSummary(
            headline = headline,
            detail = detail,
            tone = RadarUiColorToken.SUSPICIOUS,
            attentionCount = attentionCount,
        )
}
