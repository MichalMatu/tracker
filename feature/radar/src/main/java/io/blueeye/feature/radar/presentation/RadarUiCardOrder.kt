package io.blueeye.feature.radar.presentation

internal object RadarUiCardOrder {
    val comparator: Comparator<RadarUiItem> =
        compareByDescending<RadarUiItem> { it.isInWatchlist }
            .thenByDescending { it.isNew }
            .thenByDescending { signalBucket(it.signalInfo.rssi) }
            .thenByDescending { normalizedRssi(it.signalInfo.rssi) }
            .thenByDescending { it.device.lastSeenAt }
            .thenByDescending { it.device.firstSeenAt }
            .thenBy { it.displayName.lowercase() }
            .thenBy { it.fingerprint }

    private fun signalBucket(rssi: Int): Int =
        when {
            rssi == UNKNOWN_RSSI -> NO_SIGNAL_BUCKET
            rssi >= STRONG_RSSI -> STRONG_SIGNAL_BUCKET
            rssi >= MEDIUM_RSSI -> MEDIUM_SIGNAL_BUCKET
            rssi >= WEAK_RSSI -> WEAK_SIGNAL_BUCKET
            else -> NO_SIGNAL_BUCKET
        }

    private fun normalizedRssi(rssi: Int): Int = if (rssi == UNKNOWN_RSSI) Int.MIN_VALUE else rssi

    private const val STRONG_RSSI = -60
    private const val MEDIUM_RSSI = -80
    private const val WEAK_RSSI = -90
    private const val UNKNOWN_RSSI = 0
    private const val STRONG_SIGNAL_BUCKET = 3
    private const val MEDIUM_SIGNAL_BUCKET = 2
    private const val WEAK_SIGNAL_BUCKET = 1
    private const val NO_SIGNAL_BUCKET = 0
}
