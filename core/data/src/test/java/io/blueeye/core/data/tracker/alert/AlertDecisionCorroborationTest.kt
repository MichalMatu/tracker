package io.blueeye.core.data.tracker.alert

import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertDecisionCorroborationTest {
    private val engine = AlertDecisionEngine()

    @Test
    fun `suspicious movement score without tracker identity does not notify`() {
        assertFalse(engine.shouldAlert(false, true, false, TrackingStatus.SUSPICIOUS, false))
    }

    @Test
    fun `suspicious movement score with tracker identity can notify`() {
        assertTrue(engine.shouldAlert(false, true, false, TrackingStatus.SUSPICIOUS, true))
    }
}
