package io.blueeye.core.data.tracker.alert

import io.blueeye.core.model.TrackingStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AlertDecisionEngine.
 * Verifies the decision matrix for Follow-Me alerts.
 */
class AlertDecisionEngineTest {

    private lateinit var engine: AlertDecisionEngine

    @Before
    fun setup() {
        engine = AlertDecisionEngine()
    }

    // ==================== RULE 1: Ignored devices never alert ====================

    @Test
    fun `ignored device - never alerts even if dangerous`() {
        val result = engine.shouldAlert(
            isIgnored = true,
            userHasMoved = true,
            isZastane = false,
            trackingStatus = TrackingStatus.DANGEROUS
        )
        assertFalse("Ignored device should never alert", result)
    }

    @Test
    fun `ignored known tracker - still no alert`() {
        val result = engine.shouldAlert(
            isIgnored = true, // Even AirTag!
            userHasMoved = true,
            isZastane = false,
            trackingStatus = TrackingStatus.DANGEROUS
        )
        assertFalse("Ignored known tracker should not alert", result)
    }

    // ==================== RULE 2: Movement context is required ====================

    @Test
    fun `known tracker - does not alert without movement pattern`() {
        val result = engine.shouldAlert(
            isIgnored = false,
            userHasMoved = false, // User at home
            isZastane = false,
            trackingStatus = TrackingStatus.SAFE // Even low score
        )
        assertFalse("Known tracker evidence alone should not alert", result)
    }

    @Test
    fun `known tracker - baseline safe device does not alert`() {
        val result = engine.shouldAlert(
            isIgnored = false,
            userHasMoved = true,
            isZastane = true, // Was there before
            trackingStatus = TrackingStatus.SAFE
        )
        assertFalse("Baseline known tracker should not alert without movement-pattern status", result)
    }

    @Test
    fun `known tracker with suspicious movement pattern alerts`() {
        val result = engine.shouldAlert(
            isIgnored = false,
            userHasMoved = true,
            isZastane = false,
            trackingStatus = TrackingStatus.SUSPICIOUS
        )
        assertTrue("Known tracker with suspicious movement evidence should alert", result)
    }

    // ==================== RULE 3: No movement = no alerts ====================

    @Test
    fun `user at home - no alerts for suspicious devices`() {
        val result = engine.shouldAlert(
            isIgnored = false,
            userHasMoved = false,
            isZastane = false,
            trackingStatus = TrackingStatus.SUSPICIOUS
        )
        assertFalse("No alerts when user hasn't moved", result)
    }

    @Test
    fun `user at home - no alerts for dangerous devices`() {
        val result = engine.shouldAlert(
            isIgnored = false,
            userHasMoved = false,
            isZastane = false,
            trackingStatus = TrackingStatus.DANGEROUS
        )
        assertFalse("No alerts when user hasn't moved, even for dangerous", result)
    }

    // ==================== RULE 4: Baseline (zastane) devices don't alert ====================

    @Test
    fun `zastane device - no alert after movement`() {
        val result = engine.shouldAlert(
            isIgnored = false,
            userHasMoved = true,
            isZastane = true, // Was there before user moved
            trackingStatus = TrackingStatus.DANGEROUS
        )
        assertFalse("Baseline device should not alert after movement", result)
    }

    // ==================== RULE 5: Normal alert logic ====================

    @Test
    fun `dangerous device after movement - alerts`() {
        val result = engine.shouldAlert(
            isIgnored = false,
            userHasMoved = true,
            isZastane = false,
            trackingStatus = TrackingStatus.DANGEROUS
        )
        assertTrue("Dangerous new device should alert after movement", result)
    }

    @Test
    fun `suspicious device after movement - alerts`() {
        val result = engine.shouldAlert(
            isIgnored = false,
            userHasMoved = true,
            isZastane = false,
            trackingStatus = TrackingStatus.SUSPICIOUS
        )
        assertTrue("Suspicious new device should alert after movement", result)
    }

    @Test
    fun `safe device after movement - no alert`() {
        val result = engine.shouldAlert(
            isIgnored = false,
            userHasMoved = true,
            isZastane = false,
            trackingStatus = TrackingStatus.SAFE
        )
        assertFalse("Safe device should not alert", result)
    }

    // ==================== Explanation tests ====================

    @Test
    fun `explanation for ignored device`() {
        val explanation = engine.getDecisionExplanation(
            isIgnored = true, isKnownTracker = false, userHasMoved = true,
            isZastane = false, trackingStatus = TrackingStatus.DANGEROUS
        )
        assertTrue(explanation.contains("ignored"))
    }

    @Test
    fun `explanation for known tracker`() {
        val explanation = engine.getDecisionExplanation(
            isIgnored = false, isKnownTracker = true, userHasMoved = false,
            isZastane = false, trackingStatus = TrackingStatus.SAFE
        )
        assertTrue(explanation.contains("moved"))
    }
}
