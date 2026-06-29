package io.blueeye.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.IdentityCarryoverVerdict

@Dao
@Suppress("TooManyFunctions")
interface DeviceActionDao {
    @Delete
    suspend fun delete(device: DeviceEntity)

    @Query("DELETE FROM devices WHERE lastSeenAt < :beforeTimestamp AND isInWatchlist = 0")
    suspend fun deleteOldDevices(beforeTimestamp: Long): Int

    @Query("DELETE FROM devices")
    suspend fun deleteAll()

    @Query("DELETE FROM devices WHERE isInWatchlist = 0")
    suspend fun deleteNonWatchlistDevices()

    @Query(
        "UPDATE signal_samples SET deviceFingerprint = :targetFingerprint WHERE deviceFingerprint = :sourceFingerprint",
    )
    suspend fun moveSamples(
        targetFingerprint: String,
        sourceFingerprint: String,
    )

    @Query(
        """
        UPDATE follow_me_observations
        SET deviceFingerprint = :targetFingerprint
        WHERE deviceFingerprint = :sourceFingerprint
        """,
    )
    suspend fun moveFollowMeObservations(
        targetFingerprint: String,
        sourceFingerprint: String,
    )

    @Query(
        """
        UPDATE alert_evidence_events
        SET deviceFingerprint = :targetFingerprint
        WHERE deviceFingerprint = :sourceFingerprint
        """,
    )
    suspend fun moveAlertEvidenceEvents(
        targetFingerprint: String,
        sourceFingerprint: String,
    )

    @Query("DELETE FROM devices WHERE fingerprint = :fingerprint")
    suspend fun deleteByFingerprint(fingerprint: String)

    @Transaction
    suspend fun mergeDevices(
        targetFingerprint: String,
        duplicateFingerprint: String,
    ) {
        moveSamples(targetFingerprint, duplicateFingerprint)
        moveFollowMeObservations(targetFingerprint, duplicateFingerprint)
        moveAlertEvidenceEvents(targetFingerprint, duplicateFingerprint)
        deleteByFingerprint(duplicateFingerprint)
    }

    @Query("UPDATE devices SET isIgnoredForTracking = :ignored WHERE fingerprint = :fingerprint")
    suspend fun setIgnoredForTracking(fingerprint: String, ignored: Boolean)

    @Query("UPDATE devices SET calibrationLabel = :label WHERE fingerprint = :fingerprint")
    suspend fun setCalibrationLabel(fingerprint: String, label: DeviceCalibrationLabel)

    @Query("UPDATE devices SET identityCarryoverVerdict = :verdict WHERE fingerprint = :fingerprint")
    suspend fun setIdentityCarryoverVerdict(
        fingerprint: String,
        verdict: IdentityCarryoverVerdict,
    )
}
