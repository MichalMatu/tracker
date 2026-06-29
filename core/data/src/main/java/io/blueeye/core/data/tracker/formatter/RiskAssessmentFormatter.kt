package io.blueeye.core.data.tracker.formatter

import javax.inject.Inject
import javax.inject.Singleton

/** Formats risk assessment scores into human-readable labels and actions. */
@Singleton
class RiskAssessmentFormatter
@Inject
constructor() {
    /** Get human-readable risk level label. */
    fun getRiskLabel(score: Int): String {
        return when {
            score >= THRESHOLD_DANGER -> "High follow-me score"
            score >= THRESHOLD_SUSPICIOUS -> "Possible follow-me pattern"
            score >= THRESHOLD_ATTENTION -> "Needs attention"
            else -> "Low signal"
        }
    }

    /** Get recommended action for the user based on the score. */
    fun getRecommendedAction(score: Int): String {
        return when {
            score >= THRESHOLD_DANGER ->
                "Review the evidence and consider pausing Bluetooth if the pattern continues."
            score >= THRESHOLD_SUSPICIOUS -> "Evidence suggests a possible movement pattern. Monitor the evidence."
            score >= THRESHOLD_ATTENTION -> "Keep an eye on this device if the signal keeps appearing."
            else -> "No action needed."
        }
    }

    private companion object {
        private const val THRESHOLD_DANGER = 76
        private const val THRESHOLD_SUSPICIOUS = 51
        private const val THRESHOLD_ATTENTION = 21
    }
}
