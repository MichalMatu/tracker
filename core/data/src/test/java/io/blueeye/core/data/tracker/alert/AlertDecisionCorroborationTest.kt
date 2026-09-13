package io.blueeye.core.data.tracker.alert

import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertDecisionCorroborationTest {
    private val engine = AlertDecisionEngine()

    @Test
    fun `suspicious movement score without tracker identity can notify`() {
        assertTrue(engine.shouldAlert(false, true, false, TrackingStatus.SUSPICIOUS))
    }

    @Test
    fun `dangerous movement score without tracker identity can notify`() {
        assertTrue(engine.shouldAlert(false, true, false, TrackingStatus.DANGEROUS))
    }
}
