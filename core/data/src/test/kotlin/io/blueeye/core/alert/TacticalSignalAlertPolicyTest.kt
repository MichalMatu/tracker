package io.blueeye.core.alert

import io.blueeye.core.data.classifier.vendor.tactical.ConfidenceLevel
import io.blueeye.core.model.EvidenceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TacticalSignalAlertPolicyTest {
    @Test
    fun `critical tactical confidence is capped below critical vibration`() {
        val level = TacticalSignalAlertPolicy.vibrationLevel(ConfidenceLevel.CRITICAL)

        assertEquals(ConfidenceLevel.HIGH, level)
    }

    @Test
    fun `high and medium tactical confidence use medium review vibration`() {
        assertEquals(ConfidenceLevel.MEDIUM, TacticalSignalAlertPolicy.vibrationLevel(ConfidenceLevel.HIGH))
        assertEquals(ConfidenceLevel.MEDIUM, TacticalSignalAlertPolicy.vibrationLevel(ConfidenceLevel.MEDIUM))
    }

    @Test
    fun `new tactical signal can vibrate once for review`() {
        assertTrue(TacticalSignalAlertPolicy.shouldVibrate(isNewDevice = true, evidenceSource = EvidenceSource.OUI))
    }

    @Test
    fun `repeated tactical signal does not vibrate again by confidence alone`() {
        assertFalse(TacticalSignalAlertPolicy.shouldVibrate(isNewDevice = false, evidenceSource = EvidenceSource.OUI))
    }

    @Test
    fun `name-only signal does not vibrate`() {
        assertFalse(TacticalSignalAlertPolicy.shouldVibrate(isNewDevice = true, evidenceSource = EvidenceSource.NAME))
    }
}
