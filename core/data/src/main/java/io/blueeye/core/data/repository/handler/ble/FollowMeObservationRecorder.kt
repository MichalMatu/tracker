package io.blueeye.core.data.repository.handler.ble

import io.blueeye.core.data.db.dao.FollowMeObservationDao
import io.blueeye.core.data.db.entity.FollowMeObservationEntity
import io.blueeye.core.model.TrackingStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FollowMeObservationRecorder @Inject constructor(
    private val followMeObservationDao: FollowMeObservationDao,
) {
    fun hasObservationValue(ctx: ScanDataContext): Boolean =
        ctx.followMeExplanation != null ||
            ctx.followingScore > 0f ||
            ctx.trackingStatus != TrackingStatus.SAFE ||
            ctx.followMeDurationScore > 0 ||
            ctx.followMeRssiStabilityScore > 0 ||
            ctx.followMeDeviceTypeScore > 0 ||
            ctx.followMeMacBehaviorScore > 0 ||
            ctx.followMeEncounterScore > 0 ||
            ctx.followMeUserMoved != null ||
            ctx.followMeBaselineDevice != null

    suspend fun record(ctx: ScanDataContext) {
        if (!hasObservationValue(ctx)) return

        val observation = FollowMeObservationEntity(
            deviceFingerprint = ctx.fingerprint,
            observedMac = ctx.mac,
            timestamp = ctx.timestamp,
            trackingStatus = ctx.trackingStatus,
            score = ctx.followingScore,
            explanation = ctx.followMeExplanation,
            rssi = ctx.validRssi,
            encounterCount = (ctx.existingDevice?.encounterCount ?: 0) + 1,
            durationScore = ctx.followMeDurationScore,
            rssiStabilityScore = ctx.followMeRssiStabilityScore,
            deviceTypeScore = ctx.followMeDeviceTypeScore,
            macBehaviorScore = ctx.followMeMacBehaviorScore,
            encounterScore = ctx.followMeEncounterScore,
            userMoved = ctx.followMeUserMoved,
            baselineDevice = ctx.followMeBaselineDevice,
        )

        try {
            followMeObservationDao.insert(observation)
            followMeObservationDao.trimDeviceHistory(ctx.fingerprint, FOLLOW_ME_HISTORY_LIMIT)
            followMeObservationDao.deleteOldObservations(ctx.timestamp - FOLLOW_ME_HISTORY_RETENTION_MS)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            android.util.Log.w(TAG, "Follow-Me observation insert failed: ${e.message}")
        }
    }

    private companion object {
        private const val TAG = "FollowMeObservationRecorder"
        private const val FOLLOW_ME_HISTORY_LIMIT = 100
        private const val FOLLOW_ME_HISTORY_RETENTION_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
