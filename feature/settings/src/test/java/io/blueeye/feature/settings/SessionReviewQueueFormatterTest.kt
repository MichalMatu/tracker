package io.blueeye.feature.settings

import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.IdentityCarryoverVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReviewQueueFormatterTest {
    @Test
    fun `format uses device queue before aggregate counts`() {
        val queue =
            SessionReviewQueueFormatter.format(
                SessionStats(
                    reviewCategoryCounts = SessionReviewCategoryCounts(suspicious = 4),
                    reviewDeviceQueue =
                        listOf(
                            SessionReviewDeviceQueueItem(
                                fingerprint = "device-1",
                                displayName = "Known headphones",
                                reasonText = "Follow-Me alert",
                                actionText = SessionReviewQueueCopy.FOLLOW_ME_ALERT,
                                decisions =
                                    listOf(
                                        SessionReviewDeviceQueueDecision(
                                            text = "Mark suspicious",
                                            deviceCalibrationLabel = DeviceCalibrationLabel.SUSPICIOUS,
                                        ),
                                        SessionReviewDeviceQueueDecision(
                                            text = "False positive",
                                            deviceCalibrationLabel = DeviceCalibrationLabel.FALSE_POSITIVE,
                                        ),
                                    ),
                            ),
                        ),
                ),
            )

        assertEquals(1, queue.size)
        assertEquals("Known headphones - Follow-Me alert", queue[0].title)
        assertEquals("device-1", queue[0].deviceFingerprint)
        assertEquals("Mark suspicious", queue[0].actions[0].text)
        assertEquals(DeviceCalibrationLabel.SUSPICIOUS, queue[0].actions[0].deviceCalibrationLabel)
        assertEquals("False positive", queue[0].actions[1].text)
        assertEquals(DeviceCalibrationLabel.FALSE_POSITIVE, queue[0].actions[1].deviceCalibrationLabel)
    }

    @Test
    fun `format preserves identity carryover verdict actions`() {
        val queue =
            SessionReviewQueueFormatter.format(
                SessionStats(
                    reviewDeviceQueue =
                        listOf(
                            SessionReviewDeviceQueueItem(
                                fingerprint = "device-identity",
                                displayName = "Merged headphones",
                                reasonText = "Identity carryover",
                                actionText = SessionReviewQueueCopy.IDENTITY_CARRYOVER,
                                decisions =
                                    listOf(
                                        SessionReviewDeviceQueueDecision(
                                            text = "Same device",
                                            identityCarryoverVerdict =
                                                IdentityCarryoverVerdict.CONFIRMED_SAME_DEVICE,
                                        ),
                                        SessionReviewDeviceQueueDecision(
                                            text = "Inconclusive",
                                            identityCarryoverVerdict = IdentityCarryoverVerdict.INCONCLUSIVE,
                                        ),
                                    ),
                            ),
                        ),
                ),
            )

        assertEquals("Same device", queue[0].actions[0].text)
        assertEquals(
            IdentityCarryoverVerdict.CONFIRMED_SAME_DEVICE,
            queue[0].actions[0].identityCarryoverVerdict,
        )
        assertEquals("Inconclusive", queue[0].actions[1].text)
        assertEquals(
            IdentityCarryoverVerdict.INCONCLUSIVE,
            queue[0].actions[1].identityCarryoverVerdict,
        )
    }

    @Test
    fun `format preserves watchlist tracking actions`() {
        val queue =
            SessionReviewQueueFormatter.format(
                SessionStats(
                    reviewDeviceQueue =
                        listOf(
                            SessionReviewDeviceQueueItem(
                                fingerprint = "device-watch",
                                displayName = "Watched headphones",
                                reasonText = "Watchlist return",
                                actionText = SessionReviewQueueCopy.WATCHLIST_RETURN,
                                decisions =
                                    listOf(
                                        SessionReviewDeviceQueueDecision(
                                            text = "Pause alerts",
                                            watchlistTrackingEnabled = false,
                                        ),
                                    ),
                            ),
                        ),
                ),
            )

        assertEquals("Pause alerts", queue[0].actions.single().text)
        assertEquals(false, queue[0].actions.single().watchlistTrackingEnabled)
    }

    @Test
    fun `format prioritizes alert and identity review signals`() {
        val queue =
            SessionReviewQueueFormatter.format(
                SessionStats(
                    reviewCategoryCounts =
                        SessionReviewCategoryCounts(
                            suspicious = 4,
                            publicSafety = 2,
                        ),
                    alertHistorySummary = SessionAlertHistorySummary(followMeAlertCount = 1),
                    identityCarryoverSummary = SessionIdentityCarryoverSummary(deviceCount = 2),
                ),
            )

        assertEquals(3, queue.size)
        assertEquals("1 Follow-Me alert", queue[0].title)
        assertEquals("2 Identity carryovers", queue[1].title)
        assertEquals("4 Suspicious devices", queue[2].title)
    }

    @Test
    fun `format routes rssi trends before lower priority watchlist items`() {
        val queue =
            SessionReviewQueueFormatter.format(
                SessionStats(
                    reviewCategoryCounts = SessionReviewCategoryCounts(watchlist = 1),
                    rssiTrendSummary = SessionRssiTrendSummary(strengtheningCount = 2),
                ),
            )

        assertEquals("2 Strengthening RSSI trends", queue[0].title)
        assertEquals("1 Watchlist device", queue[1].title)
    }

    @Test
    fun `format returns empty queue when session has no review signals`() {
        assertTrue(SessionReviewQueueFormatter.format(SessionStats()).isEmpty())
    }
}
