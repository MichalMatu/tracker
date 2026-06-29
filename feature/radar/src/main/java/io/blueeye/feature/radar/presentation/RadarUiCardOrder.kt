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
            rssi == UNKNOWN_RSSI -> 0
            rssi >= STRONG_RSSI -> 3
            rssi >= MEDIUM_RSSI -> 2
            rssi >= WEAK_RSSI -> 1
            else -> 0
        }

    private fun normalizedRssi(rssi: Int): Int =
        if (rssi == UNKNOWN_RSSI) Int.MIN_VALUE else rssi

    private const val STRONG_RSSI = -60
    private const val MEDIUM_RSSI = -80
    private const val WEAK_RSSI = -90
    private const val UNKNOWN_RSSI = 0
}
