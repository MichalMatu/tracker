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
        """,
    )
    suspend fun failAutoProbeIfStillProbing(
        fingerprint: String,
        timestamp: Long,
        error: String,
    ): Int
}
