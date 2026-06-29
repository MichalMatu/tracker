package io.blueeye.core.data.tracker.formatter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskAssessmentFormatterTest {
    private val formatter = RiskAssessmentFormatter()

    @Test
    fun `high score label and action stay evidence based`() {
        val label = formatter.getRiskLabel(HIGH_SCORE)
        val action = formatter.getRecommendedAction(HIGH_SCORE)

        assertTrue(label.contains("High follow-me score"))
        assertTrue(action.contains("Review the evidence"))
        assertNoFactClaim("$label $action")
    }

    @Test
    fun `suspicious score action does not claim confirmed following`() {
        val label = formatter.getRiskLabel(SUSPICIOUS_SCORE)
        val action = formatter.getRecommendedAction(SUSPICIOUS_SCORE)

        assertTrue(label.contains("Possible follow-me pattern"))
        assertTrue(action.contains("possible movement pattern"))
        assertFalse(action.contains("correlated with your movement"))
        assertNoFactClaim("$label $action")
    }

    private fun assertNoFactClaim(text: String) {
        val normalized = text.lowercase()
        assertFalse(normalized.contains("has been following you"))
        assertFalse(normalized.contains("following you"))
        assertFalse(normalized.contains("danger"))
    }

    private companion object {
        private const val HIGH_SCORE = 82
        private const val SUSPICIOUS_SCORE = 60
    }
}
