package io.blueeye.core.alert

import io.blueeye.core.data.classifier.vendor.tactical.ConfidenceLevel
import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackerAlertSignalPolicyTest {
    @Test
    fun `high follow me score is capped below critical vibration`() {
        val level =
            TrackerAlertSignalPolicy.vibrationLevel(
                status = TrackingStatus.DANGEROUS,
                isKnownTracker = false,
            )

        assertEquals(ConfidenceLevel.HIGH, level)
    }

    @Test
    fun `suspicious follow me score uses medium vibration`() {
        val level =
            TrackerAlertSignalPolicy.vibrationLevel(
                status = TrackingStatus.SUSPICIOUS,
                isKnownTracker = false,
            )

        assertEquals(ConfidenceLevel.MEDIUM, level)
    }

    @Test
    fun `safe non tracker does not vibrate`() {
        val level =
            TrackerAlertSignalPolicy.vibrationLevel(
                status = TrackingStatus.SAFE,
                isKnownTracker = false,
            )

        assertNull(level)
    }

    @Test
    fun `known tracker evidence uses high vibration without follow me claim`() {
        val level =
            TrackerAlertSignalPolicy.vibrationLevel(
                status = TrackingStatus.SAFE,
                isKnownTracker = true,
            )

        assertEquals(ConfidenceLevel.HIGH, level)
    }
}
