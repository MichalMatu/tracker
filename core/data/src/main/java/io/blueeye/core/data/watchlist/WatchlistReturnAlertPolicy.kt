package io.blueeye.core.data.watchlist

object WatchlistReturnAlertPolicy {
    fun evaluate(
        isWatchlisted: Boolean,
        isTrackingEnabled: Boolean,
        previousLastSeenAt: Long?,
        currentSeenAt: Long,
        lastAlertAt: Long?,
    ): WatchlistReturnAlertDecision {
        val offlineDurationMs = previousLastSeenAt?.let { currentSeenAt - it }
        return when {
            !isWatchlisted ->
                WatchlistReturnAlertDecision.Suppress(WatchlistReturnAlertSuppressReason.NOT_WATCHLISTED)
            !isTrackingEnabled ->
                WatchlistReturnAlertDecision.Suppress(WatchlistReturnAlertSuppressReason.TRACKING_DISABLED)
            offlineDurationMs == null ->
                WatchlistReturnAlertDecision.Suppress(WatchlistReturnAlertSuppressReason.FIRST_SIGHTING)
            offlineDurationMs <= OFFLINE_THRESHOLD_MS ->
                WatchlistReturnAlertDecision.Suppress(WatchlistReturnAlertSuppressReason.STILL_PRESENT)
            lastAlertAt != null && currentSeenAt - lastAlertAt < RETURN_ALERT_COOLDOWN_MS ->
                WatchlistReturnAlertDecision.Suppress(WatchlistReturnAlertSuppressReason.COOLDOWN_ACTIVE)
            else ->
                WatchlistReturnAlertDecision.Alert(offlineDurationMs = offlineDurationMs)
        }
    }

    const val OFFLINE_THRESHOLD_MS = 60_000L
    const val RETURN_ALERT_COOLDOWN_MS = 5 * 60_000L
}

sealed interface WatchlistReturnAlertDecision {
    data class Alert(val offlineDurationMs: Long) : WatchlistReturnAlertDecision

    data class Suppress(
        val reason: WatchlistReturnAlertSuppressReason,
    ) : WatchlistReturnAlertDecision
}

enum class WatchlistReturnAlertSuppressReason {
    NOT_WATCHLISTED,
    TRACKING_DISABLED,
    FIRST_SIGHTING,
    STILL_PRESENT,
    COOLDOWN_ACTIVE,
}
