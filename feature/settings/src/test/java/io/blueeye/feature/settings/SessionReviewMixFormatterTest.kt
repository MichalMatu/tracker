package io.blueeye.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionReviewMixFormatterTest {
    @Test
    fun `format omits empty categories`() {
        val text =
            SessionReviewMixFormatter.format(
                SessionReviewCategoryCounts(
                    watchlist = 1,
                    suspicious = 2,
                    publicSafety = 0,
                    nearby = 3,
                    unknownNoise = 0,
                ),
            )

        assertEquals("Review mix: 1 watchlist / 2 suspicious / 3 nearby", text)
    }

    @Test
    fun `format returns blank text when session has no reviewed devices`() {
        assertEquals("", SessionReviewMixFormatter.format(SessionReviewCategoryCounts()))
    }
}
