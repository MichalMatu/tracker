package io.blueeye.feature.radar.presentation

/**
 * Stable ordering for Radar cards.
 *
 * Live scan fields such as RSSI and lastSeenAt intentionally do not participate in ordering.
 * They can change several times per second and caused visible card reshuffling while the user
 * was reading or trying to tap a row. Only explicit priority changes (watchlist/baseline-new)
 * and immutable discovery identity determine position.
 */
internal object RadarUiCardOrder {
    val comparator: Comparator<RadarUiItem> =
        compareByDescending<RadarUiItem> { it.isInWatchlist }
            .thenByDescending { it.isNew }
            .thenByDescending { it.firstSeenAt }
            .thenBy { it.displayName.lowercase() }
            .thenBy { it.fingerprint }
}
