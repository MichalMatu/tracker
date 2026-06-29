package io.blueeye.core.alert

import io.blueeye.core.model.TrackingStatus

internal data class TrackerAlertContent(
    val title: String,
    val body: String,
)

internal object TrackerAlertContentFormatter {
    fun format(
        mac: String,
        score: Int,
        status: TrackingStatus,
        evidenceReason: String? = null,
        isKnownTracker: Boolean = false,
    ): TrackerAlertContent? =
        when {
            isKnownTracker && status == TrackingStatus.SAFE ->
                TrackerAlertContent(
                    title = "Tracker-like signal needs review",
                    body =
                        "Signal from $mac is consistent with a known Bluetooth tracker type. " +
                            knownTrackerEvidenceSentence(evidenceReason) +
                            "Review movement history before acting.",
                )
            status == TrackingStatus.DANGEROUS ->
                TrackerAlertContent(
                    title = "High follow-me evidence score",
                    body =
                        "Signal from $mac has a high Follow-Me evidence score: $score/100. " +
                            evidenceSentence(evidenceReason) +
                            "Open BlueEye to review evidence.",
                )
            status == TrackingStatus.SUSPICIOUS ->
                TrackerAlertContent(
                    title = "Possible follow-me pattern",
                    body =
                        "Signal from $mac matches a possible movement pattern. Score: $score/100. " +
                            evidenceSentence(evidenceReason) +
                            "Review evidence before acting.",
                )
            else -> null
        }

    private fun knownTrackerEvidenceSentence(reason: String?): String {
        val alertEvidence =
            reason
                ?.split(",")
                ?.map { it.trim().trimEnd('.') }
                ?.filter { it.isNotBlank() && !it.isSuppressionReason() }
                ?.joinToString(", ")
                ?.takeIf { it.isNotBlank() }
                ?: "Known tracker type"

        return evidenceSentence(alertEvidence)
    }

    private fun evidenceSentence(reason: String?): String {
        val cleanReason = reason?.trim()?.trimEnd('.')?.takeIf { it.isNotBlank() } ?: return ""
        return "Evidence: $cleanReason. "
    }

    private fun String.isSuppressionReason(): Boolean =
        contains("score suppressed", ignoreCase = true) ||
            contains("movement not detected", ignoreCase = true) ||
            contains("movement source unavailable", ignoreCase = true) ||
            contains("baseline device", ignoreCase = true)
}
