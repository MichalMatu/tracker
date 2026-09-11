package io.blueeye.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.data.db.entity.WatchlistEntity
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.IdentityCarryoverVerdict

@Dao
@Suppress("TooManyFunctions")
interface DeviceActionDao {
    @Delete
    suspend fun delete(device: DeviceEntity)

    @Query(
        """
        DELETE FROM devices
        WHERE lastSeenAt < :beforeTimestamp
            AND isInWatchlist = 0
            AND isSafeBeacon = 0
            AND (userAlias IS NULL OR TRIM(userAlias) = '')
            AND (userNotes IS NULL OR TRIM(userNotes) = '')
            AND alertSound = 0
            AND alertVibration = 0
            AND isTrackingEnabled = 1
            AND isIgnoredForTracking = 0
            AND calibrationLabel = 'UNKNOWN'
            AND identityCarryoverVerdict = 'UNREVIEWED'
            AND fingerprint NOT IN (SELECT deviceFingerprint FROM watchlist)
            AND fingerprint NOT IN (SELECT deviceFingerprint FROM identity_continuity_candidates)
            AND fingerprint NOT IN (SELECT candidateFingerprint FROM identity_continuity_candidates)
        """,
    )
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

    @Query(
        "UPDATE identity_continuity_candidates SET deviceFingerprint = :targetFingerprint " +
            "WHERE deviceFingerprint = :sourceFingerprint",
    )
    suspend fun moveIdentityCandidates(
        targetFingerprint: String,
        sourceFingerprint: String,
    )

    @Query(
        "UPDATE identity_continuity_candidates SET candidateFingerprint = :targetFingerprint " +
            "WHERE candidateFingerprint = :sourceFingerprint",
    )
    suspend fun retargetIdentityCandidates(
        targetFingerprint: String,
        sourceFingerprint: String,
    )

    @Query("DELETE FROM devices WHERE fingerprint = :fingerprint")
    suspend fun deleteByFingerprint(fingerprint: String)

    @Query("SELECT * FROM devices WHERE fingerprint = :fingerprint")
    suspend fun getDeviceForMerge(fingerprint: String): DeviceEntity?

    @Update
    suspend fun updateDeviceForMerge(device: DeviceEntity)

    @Query("SELECT * FROM watchlist WHERE deviceFingerprint = :fingerprint")
    suspend fun getWatchlistForMerge(fingerprint: String): WatchlistEntity?

    @Update
    suspend fun updateWatchlistForMerge(entry: WatchlistEntity)

    @Transaction
    suspend fun mergeDevices(
        targetFingerprint: String,
        duplicateFingerprint: String,
    ) {
        val target = getDeviceForMerge(targetFingerprint)
        val duplicate = getDeviceForMerge(duplicateFingerprint)
        if (target != null && duplicate != null) {
            val mergedTarget = DeviceMergePolicy.mergeUserState(target, duplicate)
            val targetWatchlist = getWatchlistForMerge(targetFingerprint)
            val duplicateWatchlist = getWatchlistForMerge(duplicateFingerprint)
            val mergedWatchlist =
                DeviceMergePolicy.mergeWatchlist(
                    target = targetWatchlist,
                    duplicate = duplicateWatchlist,
                    targetFingerprint = targetFingerprint,
                )

            if (mergedTarget != target) {
                updateDeviceForMerge(mergedTarget)
            }
            if (mergedWatchlist != null && mergedWatchlist != targetWatchlist) {
                updateWatchlistForMerge(mergedWatchlist)
            }
        }
        moveSamples(targetFingerprint, duplicateFingerprint)
        moveFollowMeObservations(targetFingerprint, duplicateFingerprint)
        moveAlertEvidenceEvents(targetFingerprint, duplicateFingerprint)
        moveIdentityCandidates(targetFingerprint, duplicateFingerprint)
        retargetIdentityCandidates(targetFingerprint, duplicateFingerprint)
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
