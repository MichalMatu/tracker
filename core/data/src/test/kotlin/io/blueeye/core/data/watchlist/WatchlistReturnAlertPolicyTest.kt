package io.blueeye.core.data.watchlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchlistReturnAlertPolicyTest {
    @Test
    fun `non-watchlisted device never alerts`() {
        val decision = WatchlistReturnAlertPolicy.evaluate(
            isWatchlisted = false,
            isTrackingEnabled = true,
            previousLastSeenAt = NOW - OFFLINE_GAP_MS,
            currentSeenAt = NOW,
            lastAlertAt = null,
        )

        assertSuppressReason(WatchlistReturnAlertSuppressReason.NOT_WATCHLISTED, decision)
    }

    @Test
    fun `disabled tracking never alerts`() {
        val decision = WatchlistReturnAlertPolicy.evaluate(
            isWatchlisted = true,
            isTrackingEnabled = false,
            previousLastSeenAt = NOW - OFFLINE_GAP_MS,
            currentSeenAt = NOW,
            lastAlertAt = null,
        )

        assertSuppressReason(WatchlistReturnAlertSuppressReason.TRACKING_DISABLED, decision)
    }

    @Test
    fun `continuous presence does not alert`() {
        val decision = WatchlistReturnAlertPolicy.evaluate(
            isWatchlisted = true,
            isTrackingEnabled = true,
            previousLastSeenAt = NOW - PRESENT_GAP_MS,
            currentSeenAt = NOW,
            lastAlertAt = null,
        )

        assertSuppressReason(WatchlistReturnAlertSuppressReason.STILL_PRESENT, decision)
    }

    @Test
    fun `return after offline threshold alerts`() {
        val decision = WatchlistReturnAlertPolicy.evaluate(
            isWatchlisted = true,
            isTrackingEnabled = true,
            previousLastSeenAt = NOW - OFFLINE_GAP_MS,
            currentSeenAt = NOW,
            lastAlertAt = null,
        )

        assertTrue(decision is WatchlistReturnAlertDecision.Alert)
        assertEquals(OFFLINE_GAP_MS, (decision as WatchlistReturnAlertDecision.Alert).offlineDurationMs)
    }

    @Test
    fun `cooldown blocks repeated return alert`() {
        val decision = WatchlistReturnAlertPolicy.evaluate(
            isWatchlisted = true,
            isTrackingEnabled = true,
            previousLastSeenAt = NOW - OFFLINE_GAP_MS,
            currentSeenAt = NOW,
            lastAlertAt = NOW - COOLDOWN_ACTIVE_GAP_MS,
        )

        assertSuppressReason(WatchlistReturnAlertSuppressReason.COOLDOWN_ACTIVE, decision)
    }

    private fun assertSuppressReason(
        expected: WatchlistReturnAlertSuppressReason,
        decision: WatchlistReturnAlertDecision,
    ) {
        assertTrue(decision is WatchlistReturnAlertDecision.Suppress)
        assertEquals(expected, (decision as WatchlistReturnAlertDecision.Suppress).reason)
    }

    private companion object {
        private const val NOW = 1_789_000_000_000L
        private const val PRESENT_GAP_MS = 30_000L
        private const val OFFLINE_GAP_MS = WatchlistReturnAlertPolicy.OFFLINE_THRESHOLD_MS + 1L
        private const val COOLDOWN_ACTIVE_GAP_MS = WatchlistReturnAlertPolicy.RETURN_ALERT_COOLDOWN_MS - 1L
    }
}
