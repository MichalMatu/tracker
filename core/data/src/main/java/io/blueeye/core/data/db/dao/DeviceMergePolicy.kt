package io.blueeye.core.data.db.dao

import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.data.db.entity.WatchlistEntity
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.IdentityCarryoverVerdict

internal object DeviceMergePolicy {
    fun mergeUserState(
        target: DeviceEntity,
        duplicate: DeviceEntity,
    ): DeviceEntity {
        val watchlistMetadataSource =
            if (duplicate.lastWatchlistReturnAlertAt > target.lastWatchlistReturnAlertAt) duplicate else target

        return target.copy(
            isInWatchlist = mergeDefaultable(target.isInWatchlist, duplicate.isInWatchlist, false),
            isSafeBeacon = mergeDefaultable(target.isSafeBeacon, duplicate.isSafeBeacon, false),
            userAlias = mergeOptionalText("userAlias", target.userAlias, duplicate.userAlias),
            userNotes = mergeOptionalText("userNotes", target.userNotes, duplicate.userNotes),
            alertSound = mergeDefaultable(target.alertSound, duplicate.alertSound, false),
            alertVibration = mergeDefaultable(target.alertVibration, duplicate.alertVibration, false),
            isTrackingEnabled = mergeDefaultable(
                target.isTrackingEnabled,
                duplicate.isTrackingEnabled,
                true,
            ),
            isIgnoredForTracking = mergeDefaultable(
                target.isIgnoredForTracking,
                duplicate.isIgnoredForTracking,
                false,
            ),
            calibrationLabel = mergeDefaultableWithConflict(
                field = "calibrationLabel",
                target = target.calibrationLabel,
                duplicate = duplicate.calibrationLabel,
                default = DeviceCalibrationLabel.UNKNOWN,
            ),
            identityCarryoverVerdict = mergeDefaultableWithConflict(
                field = "identityCarryoverVerdict",
                target = target.identityCarryoverVerdict,
                duplicate = duplicate.identityCarryoverVerdict,
                default = IdentityCarryoverVerdict.UNREVIEWED,
            ),
            lastWatchlistReturnAlertAt = watchlistMetadataSource.lastWatchlistReturnAlertAt,
            lastWatchlistReturnOfflineDurationMs = watchlistMetadataSource.lastWatchlistReturnOfflineDurationMs,
        )
    }

    fun mergeWatchlist(
        target: WatchlistEntity?,
        duplicate: WatchlistEntity?,
        targetFingerprint: String,
    ): WatchlistEntity? =
        when {
            target == null -> duplicate?.copy(deviceFingerprint = targetFingerprint)
            duplicate == null -> target
            target.hasSameUserConfig(duplicate) ->
                target.copy(addedAt = minOf(target.addedAt, duplicate.addedAt))
            else -> throw DeviceMergeConflictException("watchlist")
        }

    private fun WatchlistEntity.hasSameUserConfig(other: WatchlistEntity): Boolean =
        alertType == other.alertType &&
            priorityLevel == other.priorityLevel &&
            triggerSmartHome == other.triggerSmartHome &&
            smartHomeUrl == other.smartHomeUrl

    private fun mergeOptionalText(
        field: String,
        target: String?,
        duplicate: String?,
    ): String? {
        val targetMeaningful = target?.takeIf { it.isNotBlank() }
        val duplicateMeaningful = duplicate?.takeIf { it.isNotBlank() }
        if (targetMeaningful != null && duplicateMeaningful != null && targetMeaningful != duplicateMeaningful) {
            throw DeviceMergeConflictException(field)
        }
        return targetMeaningful ?: duplicateMeaningful ?: target ?: duplicate
    }

    private fun <T> mergeDefaultable(
        target: T,
        duplicate: T,
        default: T,
    ): T =
        when {
            target != default -> target
            duplicate != default -> duplicate
            else -> target
        }

    private fun <T> mergeDefaultableWithConflict(
        field: String,
        target: T,
        duplicate: T,
        default: T,
    ): T {
        val targetMeaningful = target != default
        val duplicateMeaningful = duplicate != default
        if (targetMeaningful && duplicateMeaningful && target != duplicate) {
            throw DeviceMergeConflictException(field)
        }
        return mergeDefaultable(target, duplicate, default)
    }
}

internal class DeviceMergeConflictException(
    val fieldName: String,
) : IllegalStateException("Conflicting user-owned device state for $fieldName")
