package io.blueeye.feature.settings

import io.blueeye.core.model.DeviceCalibrationLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionReviewReadinessUiFormatterTest {
    @Test
    fun `ready session uses ready tone without detail noise`() {
        val readiness =
            SessionReviewReadinessCalculator.calculate(
                SessionReviewReadinessInput(
                    label = DeviceCalibrationLabel.SUSPICIOUS,
                    notes = "Home baseline near known headphones.",
                    deviceCount = 2,
                    sampleCount = 8,
                    attentionEvidenceCount = 1,
                ),
            )

        val info = SessionReviewReadinessUiFormatter.format(readiness)

        assertEquals("Ready for heuristic review", info.statusText)
        assertEquals("", info.detailText)
        assertEquals(SessionReviewReadinessTone.READY, info.tone)
    }

    @Test
    fun `incomplete session explains blockers and useful additions`() {
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

        val info = SessionReviewReadinessUiFormatter.format(readiness)

        assertEquals("Needs more session context", info.statusText)
        assertEquals(
            "Missing: session label, session devices, RSSI samples / Useful: session notes, review signals",
            info.detailText,
        )
        assertEquals(SessionReviewReadinessTone.NEEDS_CONTEXT, info.tone)
    }

    @Test
    fun `ready session with warnings keeps ready tone and useful detail`() {
        val readiness =
            SessionReviewReadinessCalculator.calculate(
                SessionReviewReadinessInput(
                    label = DeviceCalibrationLabel.KNOWN_SAFE,
                    notes = "",
                    deviceCount = 3,
                    sampleCount = 9,
                    attentionEvidenceCount = 0,
                ),
            )

        val info = SessionReviewReadinessUiFormatter.format(readiness)

        assertEquals("Ready for heuristic review", info.statusText)
        assertEquals("Useful: session notes", info.detailText)
        assertEquals(SessionReviewReadinessTone.READY, info.tone)
    }

    @Test
    fun `ready active collection session without probe data explains useful detail`() {
        val readiness =
            SessionReviewReadinessCalculator.calculate(
                SessionReviewReadinessInput(
                    label = DeviceCalibrationLabel.SUSPICIOUS,
                    notes = "Active field check.",
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

        val info = SessionReviewReadinessUiFormatter.format(readiness)

        assertEquals("Ready for heuristic review", info.statusText)
        assertEquals("Useful: active probe data", info.detailText)
        assertEquals(SessionReviewReadinessTone.READY, info.tone)
    }
}
