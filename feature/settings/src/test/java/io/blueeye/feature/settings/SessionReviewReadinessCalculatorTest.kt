package io.blueeye.feature.settings

import io.blueeye.core.model.DeviceCalibrationLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReviewReadinessCalculatorTest {
    @Test
    fun `ready session has no blockers or warnings`() {
        val readiness =
            SessionReviewReadinessCalculator.calculate(
                SessionReviewReadinessInput(
                    label = DeviceCalibrationLabel.SUSPICIOUS,
                    notes = "Home baseline near known headphones.",
                    deviceCount = 3,
                    sampleCount = 18,
                    attentionEvidenceCount = 1,
                ),
            )

        assertTrue(readiness.readyForHeuristicReview)
        assertTrue(readiness.hasUserVerdict)
        assertTrue(readiness.hasNotes)
        assertTrue(readiness.hasDevices)
        assertTrue(readiness.hasSamples)
        assertTrue(readiness.hasAttentionEvidence)
        assertEquals(emptyList<SessionReviewReadinessItem>(), readiness.blockers)
        assertEquals(emptyList<SessionReviewReadinessItem>(), readiness.warnings)
    }

    @Test
    fun `unlabeled empty session exposes blockers and warnings`() {
        val readiness =
            SessionReviewReadinessCalculator.calculate(
                SessionReviewReadinessInput(
                    label = DeviceCalibrationLabel.UNKNOWN,
                    notes = "",
                    deviceCount = 0,
                    sampleCount = 0,
                    attentionEvidenceCount = 0,
                ),
            )

        assertFalse(readiness.readyForHeuristicReview)
        assertEquals(
            listOf(
                SessionReviewReadinessItem.SESSION_LABEL,
                SessionReviewReadinessItem.SESSION_DEVICES,
                SessionReviewReadinessItem.RSSI_SAMPLES,
            ),
            readiness.blockers,
        )
        assertEquals(
            listOf(
                SessionReviewReadinessItem.SESSION_NOTES,
                SessionReviewReadinessItem.ATTENTION_EVIDENCE,
            ),
            readiness.warnings,
        )
    }

    @Test
    fun `session without notes remains ready but exposes useful warning`() {
        val readiness =
            SessionReviewReadinessCalculator.calculate(
                SessionReviewReadinessInput(
                    label = DeviceCalibrationLabel.TRUE_POSITIVE,
                    notes = " ",
                    deviceCount = 1,
                    sampleCount = 4,
                    attentionEvidenceCount = 1,
                ),
            )

        assertTrue(readiness.readyForHeuristicReview)
        assertEquals(emptyList<SessionReviewReadinessItem>(), readiness.blockers)
        assertEquals(listOf(SessionReviewReadinessItem.SESSION_NOTES), readiness.warnings)
    }

    @Test
    fun `known safe baseline does not require attention evidence`() {
        val readiness =
            SessionReviewReadinessCalculator.calculate(
                SessionReviewReadinessInput(
                    label = DeviceCalibrationLabel.KNOWN_SAFE,
                    notes = "Quiet home baseline.",
                    deviceCount = 3,
                    sampleCount = 9,
                    attentionEvidenceCount = 0,
                ),
            )

        assertTrue(readiness.readyForHeuristicReview)
        assertEquals(emptyList<SessionReviewReadinessItem>(), readiness.blockers)
        assertEquals(emptyList<SessionReviewReadinessItem>(), readiness.warnings)
    }

    @Test
    fun `active collection without probe data remains ready but warns`() {
        val readiness =
            SessionReviewReadinessCalculator.calculate(
                SessionReviewReadinessInput(
                    label = DeviceCalibrationLabel.SUSPICIOUS,
                    notes = "Active collection field check.",
                    deviceCount = 3,
                    sampleCount = 9,
                    attentionEvidenceCount = 1,
                    activeCollection =
                        SessionReviewActiveCollection(
                            enabled = true,
                            dataDeviceCount = 0,
                        ),
                ),
            )

        assertTrue(readiness.readyForHeuristicReview)
        assertFalse(readiness.hasActiveProbeData)
        assertEquals(emptyList<SessionReviewReadinessItem>(), readiness.blockers)
        assertEquals(listOf(SessionReviewReadinessItem.ACTIVE_PROBE_DATA), readiness.warnings)
    }

    @Test
    fun `active collection with probe data has no active probe warning`() {
        val readiness =
            SessionReviewReadinessCalculator.calculate(
                SessionReviewReadinessInput(
                    label = DeviceCalibrationLabel.SUSPICIOUS,
                    notes = "Active collection field check.",
                    deviceCount = 3,
                    sampleCount = 9,
                    attentionEvidenceCount = 1,
                    activeCollection =
                        SessionReviewActiveCollection(
                            enabled = true,
                            dataDeviceCount = 1,
                        ),
                ),
            )

        assertTrue(readiness.readyForHeuristicReview)
        assertTrue(readiness.hasActiveProbeData)
        assertEquals(emptyList<SessionReviewReadinessItem>(), readiness.blockers)
        assertEquals(emptyList<SessionReviewReadinessItem>(), readiness.warnings)
    }
}
