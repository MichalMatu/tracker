package io.blueeye.feature.radar.presentation

enum class LiveNearbyFreshness {
    ACTIVE,
    RECENT,
    STALE,
}

enum class LiveSignalTrend {
    STRONGER,
    STEADY,
    WEAKER,
    UNKNOWN,
}

data class LiveNearbyDevice(
    val item: RadarUiItem,
    val smoothedRssi: Int,
    val trend: LiveSignalTrend,
    val freshness: LiveNearbyFreshness,
    val ageMs: Long,
)

sealed interface LiveNearbyEntry {
    val key: String
    val sortRssi: Int
    val latestSeenAt: Long

    data class Device(val device: LiveNearbyDevice) : LiveNearbyEntry {
        override val key: String = "device:${device.item.fingerprint}"
        override val sortRssi: Int = device.smoothedRssi
        override val latestSeenAt: Long = device.item.lastSeenAt
    }

    data class ProtocolGroup(
        val family: ProtocolNoiseFamily,
        val members: List<LiveNearbyDevice>,
        val activeCount: Int,
        val recentCount: Int,
        val totalIdentities: Int,
        override val sortRssi: Int,
        override val latestSeenAt: Long,
    ) : LiveNearbyEntry {
        override val key: String = "protocol:${family.name}"
    }
}

data class LiveNearbySnapshot(
    val pinned: LiveNearbyDevice?,
    val active: List<LiveNearbyEntry>,
    val recent: List<LiveNearbyEntry>,
    val activeIdentityCount: Int,
    val recentIdentityCount: Int,
)

sealed interface LiveNearbyUiState {
    data object Loading : LiveNearbyUiState

    data class Error(val message: String) : LiveNearbyUiState

    data class Success(val snapshot: LiveNearbySnapshot) : LiveNearbyUiState
}
