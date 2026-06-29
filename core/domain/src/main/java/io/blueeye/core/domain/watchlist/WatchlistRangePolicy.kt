package io.blueeye.core.domain.watchlist

object WatchlistRangePolicy {
    const val IN_RANGE_WINDOW_MS = 60_000L

    fun isInRange(
        lastSeenAt: Long,
        now: Long,
    ): Boolean = now - lastSeenAt <= IN_RANGE_WINDOW_MS
}
