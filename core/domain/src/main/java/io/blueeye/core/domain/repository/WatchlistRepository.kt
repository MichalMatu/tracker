package io.blueeye.core.domain.repository

import io.blueeye.core.model.AlertType
import io.blueeye.core.model.Device
import kotlinx.coroutines.flow.Flow

interface WatchlistRepository {
    val watchlistDevicesFlow: Flow<Result<List<WatchlistDeviceItem>>>

    suspend fun isOnWatchlist(fingerprint: String): Result<Boolean>

    suspend fun addToWatchlist(
        fingerprint: String,
        alertType: AlertType = AlertType.ON_APPEAR,
        priorityLevel: Int = 3
    ): Result<Unit>

    suspend fun removeFromWatchlist(fingerprint: String): Result<Unit>

    suspend fun toggleWatchlist(fingerprint: String): Result<Boolean>

    suspend fun updateAlertSettings(
        fingerprint: String,
        alertType: AlertType? = null,
        priorityLevel: Int? = null,
        triggerSmartHome: Boolean? = null,
        smartHomeUrl: String? = null
    ): Result<Unit>

    suspend fun setTrackingEnabled(
        fingerprint: String,
        enabled: Boolean
    ): Result<Unit>
}

data class WatchlistDeviceItem(
    val device: Device,
    val isInRange: Boolean,
    val alertType: AlertType,
    val priorityLevel: Int
)
