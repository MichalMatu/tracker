package io.blueeye.feature.radar.presentation

/**
 * Stable ordering for Radar cards.
 *
 * RSSI still does not participate in ordering because it changes several times per second. Recency
 * uses coarse buckets instead of raw lastSeenAt so a device that is actively in range stays above
 * stale rows without continuously reshuffling the list.
 */
internal object RadarUiCardOrder {
    val comparator: Comparator<RadarUiItem>
        get() = comparator(System.currentTimeMillis())

    fun comparator(nowMs: Long): Comparator<RadarUiItem> =
        compareByDescending<RadarUiItem> { it.isInWatchlist }
            .thenByDescending { recencyPriority(it.lastSeenAt, nowMs) }
            .thenByDescending { it.isNew }
            .thenByDescending { it.firstSeenAt }
            .thenBy { it.displayName.lowercase() }
            .thenBy { it.fingerprint }

    private fun recencyPriority(
        lastSeenAt: Long,
        nowMs: Long
    ): Int {
        val ageMs = (nowMs - lastSeenAt).coerceAtLeast(0L)
        return when {
            ageMs <= ACTIVE_WINDOW_MS -> 2
            ageMs <= RECENT_WINDOW_MS -> 1
            else -> 0
        }
    }

    private const val ACTIVE_WINDOW_MS = 15_000L
    private const val RECENT_WINDOW_MS = 60_000L
}
