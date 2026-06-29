package io.blueeye.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.blueeye.core.data.db.entity.AlertEvidenceEventEntity
import io.blueeye.core.model.AlertEvidenceEventType
import kotlinx.coroutines.flow.Flow

/** DAO for durable alert/evidence event history. */
@Dao
interface AlertEvidenceEventDao {
    @Insert
    suspend fun insert(event: AlertEvidenceEventEntity)

    @Query(
        """
        SELECT * FROM alert_evidence_events
        WHERE deviceFingerprint = :fingerprint
        ORDER BY timestamp DESC
        LIMIT :limit
        """,
    )
    fun getRecentForDevice(
        fingerprint: String,
        limit: Int = DEFAULT_HISTORY_LIMIT,
    ): Flow<List<AlertEvidenceEventEntity>>

    @Query(
        """
        SELECT * FROM alert_evidence_events
        ORDER BY timestamp DESC
        LIMIT :limit
        """,
    )
    fun getRecent(
        limit: Int = DEFAULT_HISTORY_LIMIT,
    ): Flow<List<AlertEvidenceEventEntity>>

    @Query(
        """
        SELECT * FROM alert_evidence_events
        WHERE deviceFingerprint = :fingerprint
            AND eventType = :eventType
        ORDER BY timestamp DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestForDeviceEvent(
        fingerprint: String,
        eventType: AlertEvidenceEventType,
    ): AlertEvidenceEventEntity?

    @Query("DELETE FROM alert_evidence_events WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldEvents(beforeTimestamp: Long): Int

    @Query(
        """
        DELETE FROM alert_evidence_events
        WHERE deviceFingerprint = :fingerprint
            AND id NOT IN (
                SELECT id FROM alert_evidence_events
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

    @Query("DELETE FROM alert_evidence_events")
    suspend fun deleteAll()

    @Query("DELETE FROM alert_evidence_events WHERE deviceFingerprint NOT IN (SELECT fingerprint FROM devices)")
    suspend fun deleteOrphanedEvents()

    companion object {
        const val DEFAULT_HISTORY_LIMIT = 100
    }
}
