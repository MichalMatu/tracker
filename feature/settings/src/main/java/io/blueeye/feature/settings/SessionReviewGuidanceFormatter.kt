package io.blueeye.feature.settings

internal object SessionReviewGuidanceFormatter {
    fun format(readiness: SessionReviewReadiness): String =
        when {
            readiness.blockers.isNotEmpty() ->
                readiness.blockers.joinToString(
                    prefix = "Before export: ",
                    separator = "; ",
                ) { item -> item.blockerActionText }

            readiness.warnings.isNotEmpty() ->
                readiness.warnings.joinToString(
                    prefix = "Exportable now. Better calibration if you ",
                    separator = " and ",
                ) { item -> item.warningActionText }

            else ->
                "Export is complete enough for heuristic review."
        }
}

private val SessionReviewReadinessItem.blockerActionText: String
    get() =
        when (this) {
            SessionReviewReadinessItem.SESSION_LABEL -> "choose a session verdict"
            SessionReviewReadinessItem.SESSION_DEVICES -> "collect at least one device"
            SessionReviewReadinessItem.RSSI_SAMPLES -> "keep scanning until RSSI samples are saved"
            SessionReviewReadinessItem.SESSION_NOTES -> "add context notes"
            SessionReviewReadinessItem.ATTENTION_EVIDENCE -> "capture a review signal"
            SessionReviewReadinessItem.ACTIVE_PROBE_DATA -> "wait for active probe data"
        }

private val SessionReviewReadinessItem.warningActionText: String
    get() =
        when (this) {
            SessionReviewReadinessItem.SESSION_LABEL -> "choose a session verdict"
            SessionReviewReadinessItem.SESSION_DEVICES -> "collect at least one device"
            SessionReviewReadinessItem.RSSI_SAMPLES -> "keep scanning until RSSI samples are saved"
            SessionReviewReadinessItem.SESSION_NOTES -> "add context notes"
            SessionReviewReadinessItem.ATTENTION_EVIDENCE -> "capture at least one review signal"
            SessionReviewReadinessItem.ACTIVE_PROBE_DATA -> "let active collection finish or turn it off"
        }
