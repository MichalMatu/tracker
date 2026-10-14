package io.blueeye.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query

/** Conditional completion of automatic GATT probes without overwriting successful results. */
@Dao
interface AutoProbeStatusDao {
    /**
     * A probe completion may race a successful GATT persistence callback.
     * Finalize only the still-pending state, preserving CONNECTED evidence.
     */
    @Query(
        """
        UPDATE devices
        SET connectionStatus = 'FAILED',
            probeError = :error,
            lastProbeTimestamp = :timestamp
        WHERE fingerprint = :fingerprint
          AND connectionStatus = 'PROBING'
          AND lastProbeTimestamp <= :timestamp
        """,
    )
    suspend fun failAutoProbeIfStillProbing(
        fingerprint: String,
        timestamp: Long,
        error: String,
    ): Int

    /**
     * Handles stale records after process death. Preserve original probe time
     * so an old interrupted request is not put on a new 15-minute cooldown.
     */
    @Query(
        """
        UPDATE devices
        SET connectionStatus = 'FAILED',
            probeError = :error
        WHERE connectionStatus = 'PROBING'
          AND lastProbeTimestamp > 0
          AND lastProbeTimestamp < :cutoff
        """,
    )
    suspend fun failStaleProbesBefore(cutoff: Long, error: String): Int
}
