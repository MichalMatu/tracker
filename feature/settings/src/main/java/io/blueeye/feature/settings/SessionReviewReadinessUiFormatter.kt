package io.blueeye.feature.settings

internal object SessionReviewReadinessUiFormatter {
    fun format(readiness: SessionReviewReadiness): SessionReviewReadinessUiInfo =
        SessionReviewReadinessUiInfo(
            statusText = readiness.statusText,
            detailText = readiness.detailText,
            tone =
                if (readiness.readyForHeuristicReview) {
                    SessionReviewReadinessTone.READY
                } else {
                    SessionReviewReadinessTone.NEEDS_CONTEXT
                },
        )
}

internal data class SessionReviewReadinessUiInfo(
    val statusText: String,
    val detailText: String,
    val tone: SessionReviewReadinessTone,
)

internal enum class SessionReviewReadinessTone {
    READY,
    NEEDS_CONTEXT,
}

private val SessionReviewReadiness.statusText: String
    get() =
        if (readyForHeuristicReview) {
            "Ready for heuristic review"
        } else {
            "Needs more session context"
        }

private val SessionReviewReadiness.detailText: String
    get() =
        listOfNotNull(
            blockers
                .takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = "Missing: ") { item -> item.displayText },
            warnings
                .takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = "Useful: ") { item -> item.displayText },
        ).joinToString(separator = " / ")

private val SessionReviewReadinessItem.displayText: String
    get() =
        when (this) {
            SessionReviewReadinessItem.SESSION_LABEL -> "session label"
            SessionReviewReadinessItem.SESSION_DEVICES -> "session devices"
            SessionReviewReadinessItem.RSSI_SAMPLES -> "RSSI samples"
            SessionReviewReadinessItem.SESSION_NOTES -> "session notes"
            SessionReviewReadinessItem.ATTENTION_EVIDENCE -> "review signals"
            SessionReviewReadinessItem.ACTIVE_PROBE_DATA -> "active probe data"
        }
