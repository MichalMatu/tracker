package io.blueeye.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.blueeye.core.data.db.entity.IdentityContinuityCandidateEntity
import io.blueeye.core.model.IdentityCarryoverVerdict

@Dao
interface IdentityContinuityCandidateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(candidate: IdentityContinuityCandidateEntity): Long

    @Query(
        """
        SELECT * FROM identity_continuity_candidates
        WHERE timestamp >= :sinceTimestamp
        ORDER BY timestamp ASC
        """,
    )
    suspend fun getSince(sinceTimestamp: Long): List<IdentityContinuityCandidateEntity>

    @Query(
        "UPDATE identity_continuity_candidates SET verdict = :verdict WHERE id = :id",
    )
    suspend fun setVerdict(id: Long, verdict: IdentityCarryoverVerdict)

    @Query("DELETE FROM identity_continuity_candidates WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldCandidates(beforeTimestamp: Long): Int

    @Query("DELETE FROM identity_continuity_candidates")
    suspend fun deleteAll()
}
