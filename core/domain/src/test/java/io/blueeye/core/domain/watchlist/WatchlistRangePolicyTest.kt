package io.blueeye.core.domain.watchlist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchlistRangePolicyTest {
    @Test
    fun `device is in range at threshold boundary`() {
        assertTrue(
            WatchlistRangePolicy.isInRange(
                lastSeenAt = NOW - WatchlistRangePolicy.IN_RANGE_WINDOW_MS,
                now = NOW,
            ),
        )
    }

    @Test
    fun `device is offline after threshold`() {
        assertFalse(
            WatchlistRangePolicy.isInRange(
                lastSeenAt = NOW - WatchlistRangePolicy.IN_RANGE_WINDOW_MS - 1L,
                now = NOW,
            ),
        )
    }

    private companion object {
        private const val NOW = 1_789_000_000_000L
    }
}
