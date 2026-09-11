package io.blueeye.core.data.db.dao

import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.data.db.entity.WatchlistEntity
import io.blueeye.core.model.AlertType
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.IdentityCarryoverVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DeviceMergePolicyTest {
    @Test
    fun `merge transfers meaningful duplicate user state into neutral target`() {
        val target = device("target")
        val duplicate =
            device("duplicate").copy(
                isInWatchlist = true,
                isSafeBeacon = true,
                userAlias = "Headset",
                userNotes = "Field note",
                alertSound = true,
                alertVibration = true,
                isTrackingEnabled = false,
                isIgnoredForTracking = true,
                calibrationLabel = DeviceCalibrationLabel.SUSPICIOUS,
                identityCarryoverVerdict = IdentityCarryoverVerdict.CONFIRMED_SAME_DEVICE,
                lastWatchlistReturnAlertAt = 200L,
                lastWatchlistReturnOfflineDurationMs = 42L,
            )

        val merged = DeviceMergePolicy.mergeUserState(target, duplicate)

        assertTrue(merged.isInWatchlist)
        assertTrue(merged.isSafeBeacon)
        assertEquals("Headset", merged.userAlias)
        assertEquals("Field note", merged.userNotes)
        assertTrue(merged.alertSound)
        assertTrue(merged.alertVibration)
        assertFalse(merged.isTrackingEnabled)
        assertTrue(merged.isIgnoredForTracking)
        assertEquals(DeviceCalibrationLabel.SUSPICIOUS, merged.calibrationLabel)
        assertEquals(IdentityCarryoverVerdict.CONFIRMED_SAME_DEVICE, merged.identityCarryoverVerdict)
        assertEquals(200L, merged.lastWatchlistReturnAlertAt)
        assertEquals(42L, merged.lastWatchlistReturnOfflineDurationMs)
    }

    @Test
    fun `merge keeps meaningful target user state when duplicate is neutral`() {
        val target =
            device("target").copy(
                isInWatchlist = true,
                userAlias = "Known device",
                alertSound = true,
                isTrackingEnabled = false,
                calibrationLabel = DeviceCalibrationLabel.KNOWN_SAFE,
                identityCarryoverVerdict = IdentityCarryoverVerdict.INCONCLUSIVE,
                lastWatchlistReturnAlertAt = 300L,
                lastWatchlistReturnOfflineDurationMs = 99L,
            )

        val merged = DeviceMergePolicy.mergeUserState(target, device("duplicate"))

        assertEquals(target, merged)
    }

    @Test
    fun `merge rejects conflicting aliases before destructive merge`() {
        assertConflict("userAlias") {
            DeviceMergePolicy.mergeUserState(
                device("target").copy(userAlias = "Keys"),
                device("duplicate").copy(userAlias = "Backpack"),
            )
        }
    }

    @Test
    fun `merge rejects conflicting calibration labels`() {
        assertConflict("calibrationLabel") {
            DeviceMergePolicy.mergeUserState(
                device("target").copy(calibrationLabel = DeviceCalibrationLabel.TRUE_POSITIVE),
                device("duplicate").copy(calibrationLabel = DeviceCalibrationLabel.FALSE_POSITIVE),
            )
        }
    }

    @Test
    fun `merge rejects conflicting identity verdicts`() {
        assertConflict("identityCarryoverVerdict") {
            DeviceMergePolicy.mergeUserState(
                device("target").copy(identityCarryoverVerdict = IdentityCarryoverVerdict.CONFIRMED_SAME_DEVICE),
                device("duplicate").copy(identityCarryoverVerdict = IdentityCarryoverVerdict.FALSE_MATCH),
            )
        }
    }

    @Test
    fun `merge moves sole duplicate watchlist entry to target fingerprint`() {
        val duplicate =
            watchlist("duplicate").copy(
                alertType = AlertType.ALWAYS,
                priorityLevel = 5,
                triggerSmartHome = true,
                smartHomeUrl = "https://example.invalid/hook",
                addedAt = 123L,
            )

        val merged = DeviceMergePolicy.mergeWatchlist(null, duplicate, "target")

        assertEquals("target", merged?.deviceFingerprint)
        assertEquals(duplicate.id, merged?.id)
        assertEquals(duplicate.alertType, merged?.alertType)
        assertEquals(duplicate.priorityLevel, merged?.priorityLevel)
        assertEquals(duplicate.triggerSmartHome, merged?.triggerSmartHome)
        assertEquals(duplicate.smartHomeUrl, merged?.smartHomeUrl)
        assertEquals(123L, merged?.addedAt)
    }

    @Test
    fun `merge keeps earliest timestamp when watchlist configs match`() {
        val target = watchlist("target").copy(id = 1L, addedAt = 200L)
        val duplicate = watchlist("duplicate").copy(id = 2L, addedAt = 100L)

        val merged = DeviceMergePolicy.mergeWatchlist(target, duplicate, "target")

        assertEquals(1L, merged?.id)
        assertEquals("target", merged?.deviceFingerprint)
        assertEquals(100L, merged?.addedAt)
    }

    @Test
    fun `merge rejects differing watchlist configs`() {
        assertConflict("watchlist") {
            DeviceMergePolicy.mergeWatchlist(
                watchlist("target").copy(priorityLevel = 3),
                watchlist("duplicate").copy(priorityLevel = 5),
                "target",
            )
        }
    }

    private fun assertConflict(
        expectedField: String,
        block: () -> Unit,
    ) {
        try {
            block()
            fail("Expected DeviceMergeConflictException")
        } catch (exception: DeviceMergeConflictException) {
            assertEquals(expectedField, exception.fieldName)
        }
    }

    private fun device(fingerprint: String): DeviceEntity =
        DeviceEntity(
            fingerprint = fingerprint,
            lastMacAddress = null,
            lastDeviceName = null,
            firstSeenAt = 100L,
            lastSeenAt = 100L,
        )

    private fun watchlist(fingerprint: String): WatchlistEntity =
        WatchlistEntity(
            id = 10L,
            deviceFingerprint = fingerprint,
            addedAt = 150L,
        )
}
