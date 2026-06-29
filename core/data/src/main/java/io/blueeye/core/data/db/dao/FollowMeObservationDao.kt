package io.blueeye.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.blueeye.core.data.db.entity.FollowMeObservationEntity
import kotlinx.coroutines.flow.Flow

/** DAO for append-only Follow-Me score history. */
@Dao
interface FollowMeObservationDao {
    @Insert
    suspend fun insert(observation: FollowMeObservationEntity)

    @Query(
        """
        SELECT * FROM follow_me_observations
        WHERE deviceFingerprint = :fingerprint
        ORDER BY timestamp DESC
        LIMIT :limit
        """,
    )
    fun getRecentForDevice(
        fingerprint: String,
        limit: Int = DEFAULT_HISTORY_LIMIT,
    ): Flow<List<FollowMeObservationEntity>>

    @Query("DELETE FROM follow_me_observations WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldObservations(beforeTimestamp: Long): Int

    @Query(
        """
        DELETE FROM follow_me_observations
        WHERE deviceFingerprint = :fingerprint
            AND id NOT IN (
                SELECT id FROM follow_me_observations
                WHERE deviceFingerprint = :fingerprint
                ORDER BY timestamp DESC
                LIMIT :keepCount
            )
        """,
    )
    suspend fun trimDeviceHistory(
        fingerprint: String,
        keepCount: Int,
    ): Int

    @Query("DELETE FROM follow_me_observations")
    suspend fun deleteAll()

    @Query("DELETE FROM follow_me_observations WHERE deviceFingerprint NOT IN (SELECT fingerprint FROM devices)")
    suspend fun deleteOrphanedObservations()

    companion object {
        const val DEFAULT_HISTORY_LIMIT = 100
    }
}
