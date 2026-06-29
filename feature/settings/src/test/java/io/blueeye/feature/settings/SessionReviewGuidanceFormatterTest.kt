package io.blueeye.feature.settings

import io.blueeye.core.model.DeviceCalibrationLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionReviewGuidanceFormatterTest {
    @Test
    fun `format explains blockers before export`() {
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

        assertEquals(
            "Before export: choose a session verdict; collect at least one device; " +
                "keep scanning until RSSI samples are saved",
            SessionReviewGuidanceFormatter.format(readiness),
        )
    }

    @Test
    fun `format explains optional calibration improvements`() {
        val readiness =
            SessionReviewReadinessCalculator.calculate(
                SessionReviewReadinessInput(
                    label = DeviceCalibrationLabel.SUSPICIOUS,
                    notes = "",
                    deviceCount = 2,
                    sampleCount = 8,
                    attentionEvidenceCount = 0,
                ),
            )

        assertEquals(
            "Exportable now. Better calibration if you add context notes and capture at least one review signal",
            SessionReviewGuidanceFormatter.format(readiness),
        )
    }

    @Test
    fun `format explains optional active probe completion`() {
        val readiness =
            SessionReviewReadinessCalculator.calculate(
                SessionReviewReadinessInput(
                    label = DeviceCalibrationLabel.SUSPICIOUS,
                    notes = "Active collection field check.",
                    deviceCount = 2,
                    sampleCount = 8,
                    attentionEvidenceCount = 1,
                    activeCollection =
                        SessionReviewActiveCollection(
                            enabled = true,
                            dataDeviceCount = 0,
                        ),
                ),
            )

        assertEquals(
            "Exportable now. Better calibration if you let active collection finish or turn it off",
            SessionReviewGuidanceFormatter.format(readiness),
        )
    }

    @Test
    fun `format confirms complete session`() {
        val readiness =
            SessionReviewReadinessCalculator.calculate(
                SessionReviewReadinessInput(
                    label = DeviceCalibrationLabel.KNOWN_SAFE,
                    notes = "Quiet home baseline.",
                    deviceCount = 2,
                    sampleCount = 8,
                    attentionEvidenceCount = 0,
                ),
            )

        assertEquals(
            "Export is complete enough for heuristic review.",
            SessionReviewGuidanceFormatter.format(readiness),
        )
    }
}
