package io.blueeye.feature.settings

import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.IdentityCarryoverVerdict

internal data class SessionReviewQueueAction(
    val text: String,
    val deviceCalibrationLabel: DeviceCalibrationLabel? = null,
    val identityCarryoverVerdict: IdentityCarryoverVerdict? = null,
    val watchlistTrackingEnabled: Boolean? = null,
)

internal data class SessionReviewQueueItem(
    val title: String,
    val actionText: String,
    val deviceFingerprint: String? = null,
    val actions: List<SessionReviewQueueAction> = emptyList(),
)

internal object SessionReviewQueueFormatter {
    fun format(stats: SessionStats): List<SessionReviewQueueItem> =
        if (stats.reviewDeviceQueue.isNotEmpty()) {
            stats.reviewDeviceQueue
                .map { item -> item.toQueueItem() }
                .take(SessionReviewQueueLimits.MAX_ITEMS)
        } else {
            buildCandidates(stats)
                .mapNotNull { candidate -> candidate.toItem() }
                .take(SessionReviewQueueLimits.MAX_ITEMS)
        }

    private fun SessionReviewDeviceQueueItem.toQueueItem(): SessionReviewQueueItem =
        SessionReviewQueueItem(
            title = "$displayName - $reasonText",
            actionText = actionText,
            deviceFingerprint = fingerprint,
            actions =
                decisions.map { decision ->
                    SessionReviewQueueAction(
                        text = decision.text,
                        deviceCalibrationLabel = decision.deviceCalibrationLabel,
                        identityCarryoverVerdict = decision.identityCarryoverVerdict,
                        watchlistTrackingEnabled = decision.watchlistTrackingEnabled,
                    )
                },
        )

    private fun buildCandidates(stats: SessionStats): List<SessionReviewQueueCandidate> =
        listOf(
            SessionReviewQueueCandidate(
                count = stats.alertHistorySummary.followMeAlertCount,
                singularTitle = "Follow-Me alert",
                pluralTitle = "Follow-Me alerts",
                actionText = SessionReviewQueueCopy.FOLLOW_ME_ALERT,
            ),
            SessionReviewQueueCandidate(
                count = stats.identityCarryoverSummary.deviceCount,
                singularTitle = "Identity carryover",
                pluralTitle = "Identity carryovers",
                actionText = SessionReviewQueueCopy.IDENTITY_CARRYOVER,
            ),
            SessionReviewQueueCandidate(
                count = stats.reviewCategoryCounts.suspicious,
                singularTitle = "Suspicious device",
                pluralTitle = "Suspicious devices",
                actionText = SessionReviewQueueCopy.SUSPICIOUS,
            ),
            SessionReviewQueueCandidate(
                count = stats.rssiTrendSummary.strengtheningCount,
                singularTitle = "Strengthening RSSI trend",
                pluralTitle = "Strengthening RSSI trends",
                actionText = SessionReviewQueueCopy.STRENGTHENING_RSSI,
            ),
            SessionReviewQueueCandidate(
                count = stats.alertHistorySummary.watchlistReturnCount,
                singularTitle = "Watchlist return",
                pluralTitle = "Watchlist returns",
                actionText = SessionReviewQueueCopy.WATCHLIST_RETURN,
            ),
            SessionReviewQueueCandidate(
                count = stats.alertHistorySummary.publicSafetySignalCount,
                singularTitle = "Public-safety-like signal",
                pluralTitle = "Public-safety-like signals",
                actionText = SessionReviewQueueCopy.PUBLIC_SAFETY_ALERT,
            ),
            SessionReviewQueueCandidate(
                count = stats.reviewCategoryCounts.watchlist,
                singularTitle = "Watchlist device",
                pluralTitle = "Watchlist devices",
                actionText = SessionReviewQueueCopy.WATCHLIST,
            ),
            SessionReviewQueueCandidate(
                count = stats.reviewCategoryCounts.publicSafety,
                singularTitle = "Public-safety-like signal",
                pluralTitle = "Public-safety-like signals",
                actionText = SessionReviewQueueCopy.PUBLIC_SAFETY,
            ),
            SessionReviewQueueCandidate(
                count = stats.reviewCategoryCounts.unknownNoise,
                singularTitle = "Unknown/noise device",
                pluralTitle = "Unknown/noise devices",
                actionText = SessionReviewQueueCopy.UNKNOWN_NOISE,
            ),
        )

    private fun SessionReviewQueueCandidate.toItem(): SessionReviewQueueItem? =
        count.takeIf { itemCount -> itemCount > 0 }?.let { itemCount ->
            SessionReviewQueueItem(
                title = "$itemCount ${if (itemCount == 1) singularTitle else pluralTitle}",
                actionText = actionText,
            )
        }
}

private data class SessionReviewQueueCandidate(
    val count: Int,
    val singularTitle: String,
    val pluralTitle: String,
    val actionText: String,
)

private object SessionReviewQueueLimits {
    const val MAX_ITEMS = 3
}
