package io.blueeye.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.model.TrackingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceSearchDao {
    @Query("SELECT * FROM devices WHERE lastSeenAt > :sinceTimestamp ORDER BY lastRssi DESC")
    fun getRecentDevicesFlow(sinceTimestamp: Long): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE lastSeenAt > :sinceTimestamp ORDER BY lastRssi DESC")
    suspend fun getRecentDevicesSnapshot(sinceTimestamp: Long): List<DeviceEntity>

    @Query("SELECT * FROM devices WHERE isInWatchlist = 1 ORDER BY lastSeenAt DESC")
    fun getWatchlistDevicesFlow(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE isSafeBeacon = 1")
    suspend fun getSafeBeacons(): List<DeviceEntity>

    @Query("SELECT * FROM devices WHERE trackingStatus = :status ORDER BY followingScore DESC")
    fun getByTrackingStatus(status: TrackingStatus): Flow<List<DeviceEntity>>

    @Query(
        """
        SELECT * FROM devices 
        WHERE lastDeviceName LIKE '%' || :query || '%' 
           OR userAlias LIKE '%' || :query || '%'
           OR vendorName LIKE '%' || :query || '%'
        ORDER BY lastSeenAt DESC
    """,
    )
    fun searchDevices(query: String): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE lastRawData = :rawDataHex ORDER BY firstSeenAt ASC LIMIT 1")
    suspend fun findDeviceByRawData(rawDataHex: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE lastRawData = :rawDataHex ORDER BY firstSeenAt ASC")
    suspend fun findAllDevicesByRawData(rawDataHex: String): List<DeviceEntity>

    @Query(
        "SELECT * FROM devices WHERE gattServices = :gattServices AND fingerprint != :excludeFingerprint LIMIT 1",
    )
    suspend fun findDeviceByGattServices(
        gattServices: String,
        excludeFingerprint: String,
    ): DeviceEntity?


}
