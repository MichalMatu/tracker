package io.blueeye.core.alert

import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerAlertContentFormatterTest {
    @Test
    fun `dangerous status uses score and evidence wording`() {
        val content =
            TrackerAlertContentFormatter.format(
                mac = MAC,
                score = HIGH_SCORE,
                status = TrackingStatus.DANGEROUS,
            )

        requireNotNull(content)
        assertTrue(content.title.contains("High follow-me evidence score"))
        assertTrue(content.body.contains("$HIGH_SCORE/100"))
        assertTrue(content.body.contains("Follow-Me evidence score"))
        assertTrue(content.body.contains("review evidence", ignoreCase = true))
        assertNoFactClaim(content)
    }

    @Test
    fun `suspicious status describes a possible movement pattern`() {
        val content =
            TrackerAlertContentFormatter.format(
                mac = MAC,
                score = SUSPICIOUS_SCORE,
                status = TrackingStatus.SUSPICIOUS,
            )

        requireNotNull(content)
        assertTrue(content.title.contains("Possible follow-me pattern"))
        assertTrue(content.body.contains("$SUSPICIOUS_SCORE/100"))
        assertTrue(content.body.contains("possible movement pattern"))
        assertTrue(content.body.contains("Review evidence"))
        assertFalse(content.body.contains("stayed correlated with your movement"))
        assertNoFactClaim(content)
    }

    @Test
    fun `alert body includes evidence reason when provided`() {
        val content =
            TrackerAlertContentFormatter.format(
                mac = MAC,
                score = SUSPICIOUS_SCORE,
                status = TrackingStatus.SUSPICIOUS,
                evidenceReason = "Seen for 12min while moving, RSSI stayed stable during movement window",
            )

        requireNotNull(content)
        assertTrue(content.body.contains("Evidence: Seen for 12min while moving"))
        assertTrue(content.body.contains("RSSI stayed stable during movement window"))
        assertNoFactClaim(content)
    }

    @Test
    fun `safe status has no alert content`() {
        assertNull(
            TrackerAlertContentFormatter.format(
                mac = MAC,
                score = 0,
                status = TrackingStatus.SAFE,
            )
        )
    }

    @Test
    fun `known tracker safe status uses tracker evidence without movement claim`() {
        val content =
            TrackerAlertContentFormatter.format(
                mac = MAC,
                score = 20,
                status = TrackingStatus.SAFE,
                evidenceReason = "Known tracker type, Movement not detected - follow-me score suppressed",
                isKnownTracker = true,
            )

        requireNotNull(content)
        assertTrue(content.title.contains("Tracker-like signal needs review"))
        assertTrue(content.body.contains("known Bluetooth tracker type"))
        assertTrue(content.body.contains("Signal from $MAC"))
        assertTrue(content.body.contains("Evidence: Known tracker type"))
        assertFalse(content.body.contains("Movement not detected"))
        assertFalse(content.body.contains("score suppressed"))
        assertFalse(content.body.contains("stayed correlated with your movement"))
        assertFalse(content.body.contains("moving together"))
        assertNoFactClaim(content)
    }

    @Test
    fun `known tracker safe status falls back to tracker evidence when reason is missing`() {
        val content =
            TrackerAlertContentFormatter.format(
                mac = MAC,
                score = 20,
                status = TrackingStatus.SAFE,
                evidenceReason = null,
                isKnownTracker = true,
            )

        requireNotNull(content)
        assertTrue(content.body.contains("Evidence: Known tracker type"))
        assertFalse(content.body.contains("score suppressed"))
        assertNoFactClaim(content)
    }

    @Test
    fun `known tracker safe status removes baseline suppression from evidence`() {
        val content =
            TrackerAlertContentFormatter.format(
                mac = MAC,
                score = 20,
                status = TrackingStatus.SAFE,
                evidenceReason =
                    "Known tracker type, " +
                        "Baseline device seen before movement - follow-me score suppressed",
                isKnownTracker = true,
            )

        requireNotNull(content)
        assertTrue(content.body.contains("Evidence: Known tracker type"))
        assertFalse(content.body.contains("Baseline device"))
        assertFalse(content.body.contains("score suppressed"))
        assertNoFactClaim(content)
    }

    @Test
    fun `known tracker safe status removes unavailable movement source from evidence`() {
        val content =
            TrackerAlertContentFormatter.format(
                mac = MAC,
                score = 20,
                status = TrackingStatus.SAFE,
                evidenceReason =
                    "Known tracker type, " +
                        "Movement source unavailable - follow-me score suppressed",
                isKnownTracker = true,
            )

        requireNotNull(content)
        assertTrue(content.body.contains("Evidence: Known tracker type"))
        assertFalse(content.body.contains("Movement source unavailable"))
        assertFalse(content.body.contains("score suppressed"))
        assertNoFactClaim(content)
    }

    private fun assertNoFactClaim(content: TrackerAlertContent) {
        val combined = "${content.title} ${content.body}".lowercase()
        assertFalse(combined.contains("dangerous device"))
        assertFalse(combined.contains("tracker-like device detected"))
        assertFalse(combined.contains("following you"))
        assertFalse(combined.contains("tracked you"))
    }

    private companion object {
        private const val MAC = "AA:BB:CC:11:22:33"
        private const val HIGH_SCORE = 88
        private const val SUSPICIOUS_SCORE = 61
    }
}
