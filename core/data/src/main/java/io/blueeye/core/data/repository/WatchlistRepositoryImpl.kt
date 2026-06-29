package io.blueeye.core.data.repository

import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.db.dao.WatchlistDao
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.data.db.entity.WatchlistEntity
import io.blueeye.core.data.mapper.toDomain
import io.blueeye.core.data.utils.asResult
import io.blueeye.core.domain.repository.WatchlistDeviceItem
import io.blueeye.core.domain.repository.WatchlistRepository
import io.blueeye.core.domain.watchlist.WatchlistRangePolicy
import io.blueeye.core.model.AlertType
import io.blueeye.core.model.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistRepositoryImpl @Inject constructor(
    private val watchlistDao: WatchlistDao,
    private val deviceDao: DeviceDao,
) : WatchlistRepository {
    override val watchlistDevicesFlow: Flow<Result<List<WatchlistDeviceItem>>> = combine(
        watchlistDao.getAllFlow(),
        deviceDao.getAllDevicesFlow()
    ) { watchlist: List<WatchlistEntity>, entities: List<DeviceEntity> ->
        val devices = entities.toDomain()
        val deviceMap = devices.associateBy { device: Device -> device.fingerprint }
        watchlist.mapNotNull { entry: WatchlistEntity ->
            deviceMap[entry.deviceFingerprint]?.let { matchedDevice: Device ->
                WatchlistDeviceItem(
                    device = matchedDevice,
                    isInRange = WatchlistRangePolicy.isInRange(
                        lastSeenAt = matchedDevice.lastSeenAt,
                        now = System.currentTimeMillis(),
                    ),
                    alertType = entry.alertType,
                    priorityLevel = entry.priorityLevel
                )
            }
        }
    }.asResult()

    override suspend fun isOnWatchlist(fingerprint: String): Result<Boolean> = runCatching {
        watchlistDao.isOnWatchlist(fingerprint)
    }

    override suspend fun addToWatchlist(
        fingerprint: String,
        alertType: AlertType,
        priorityLevel: Int
    ): Result<Unit> = runCatching {
        val entry = WatchlistEntity(
            deviceFingerprint = fingerprint,
            alertType = alertType,
            priorityLevel = priorityLevel
        )
        watchlistDao.insert(entry)
        deviceDao.setWatchlistStatus(fingerprint, isWatchlisted = true)
    }

    override suspend fun removeFromWatchlist(fingerprint: String): Result<Unit> = runCatching {
        watchlistDao.deleteByFingerprint(fingerprint)
        deviceDao.setWatchlistStatus(fingerprint, isWatchlisted = false)
    }

    override suspend fun toggleWatchlist(fingerprint: String): Result<Boolean> = runCatching {
        if (isOnWatchlist(fingerprint).getOrThrow()) {
            removeFromWatchlist(fingerprint).getOrThrow()
            false
        } else {
            addToWatchlist(fingerprint).getOrThrow()
            true
        }
    }

    override suspend fun updateAlertSettings(
        fingerprint: String,
        alertType: AlertType?,
        priorityLevel: Int?,
        triggerSmartHome: Boolean?,
        smartHomeUrl: String?
    ): Result<Unit> = runCatching {
        val existing = watchlistDao.getByFingerprint(fingerprint) ?: return@runCatching
        val updated = existing.copy(
            alertType = alertType ?: existing.alertType,
            priorityLevel = priorityLevel ?: existing.priorityLevel,
            triggerSmartHome = triggerSmartHome ?: existing.triggerSmartHome,
            smartHomeUrl = smartHomeUrl ?: existing.smartHomeUrl
        )
        watchlistDao.update(updated)
    }

    override suspend fun setTrackingEnabled(fingerprint: String, enabled: Boolean): Result<Unit> = runCatching {
        deviceDao.updateTrackingEnabled(fingerprint, enabled)
    }
}
