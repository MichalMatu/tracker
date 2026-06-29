package io.blueeye.feature.details

import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.IdentityCarryoverVerdict

object DetailsDecisionSummaryFormatter {
    fun format(device: Device): DetailsDecisionSummary {
        val strongestEvidence = DetailsDecisionSignalClassifier.strongestEvidence(device)
        return when {
            device.isInWatchlist -> watchlistSummary(device)
            device.calibrationLabel != DeviceCalibrationLabel.UNKNOWN -> calibrationSummary(device.calibrationLabel)
            device.evidence.any { item -> item.source == EvidenceSource.IDENTITY_CARRYOVER } ->
                identityCarryoverSummary(device.identityCarryoverVerdict)
            DetailsDecisionSignalClassifier.hasTrackerLikeSignal(device) -> trackerSummary(device)
            DetailsDecisionSignalClassifier.hasFollowMeSignal(device) -> followMeSummary(device)
            DetailsDecisionSignalClassifier.hasPublicSafetyLikeSignal(device) -> publicSafetySummary(device)
            strongestEvidence != null && DetailsDecisionSignalClassifier.isReviewEvidence(strongestEvidence) ->
                evidenceSummary(strongestEvidence)
            else -> noAttentionSummary()
        }
    }

    private fun watchlistSummary(device: Device): DetailsDecisionSummary =
        DetailsDecisionSummary(
            headline = "Watchlist device",
            detail =
                if (device.isTrackingEnabled) {
                    "Return alerts are active for this device."
                } else {
                    "Return alerts are paused for this device."
                },
            actionText =
                if (device.isTrackingEnabled) {
                    "Keep watching, edit alerts, or remove from Watchlist if this is expected."
                } else {
                    "Enable return alerts if you want a notification when it appears again."
                },
            tone = DetailsDecisionTone.WARNING,
        )

    private fun calibrationSummary(label: DeviceCalibrationLabel): DetailsDecisionSummary =
        DetailsDecisionSummary(
            headline = label.toSummaryLabel(),
            detail =
                if (label in SUPPRESSED_LABELS) {
                    "Your calibration suppresses alerts and Follow-Me scoring for this device."
                } else {
                    "Your calibration keeps this device in the review path."
                },
            actionText =
                if (label in SUPPRESSED_LABELS) {
                    "Change the verdict if this device starts behaving unexpectedly."
                } else {
                    "Review history, then keep or change the verdict after more evidence."
                },
            tone =
                if (label in SUPPRESSED_LABELS) {
                    DetailsDecisionTone.SAFE
                } else {
                    DetailsDecisionTone.SUSPICIOUS
                },
        )

    private fun identityCarryoverSummary(verdict: IdentityCarryoverVerdict): DetailsDecisionSummary =
        when (verdict) {
            IdentityCarryoverVerdict.UNREVIEWED ->
                DetailsDecisionSummary(
                    headline = "Identity carryover needs review",
                    detail = "A rotating address was correlated with this device record.",
                    actionText = "Review evidence before trusting merged history.",
                    tone = DetailsDecisionTone.WARNING,
                )
            IdentityCarryoverVerdict.CONFIRMED_SAME_DEVICE ->
                DetailsDecisionSummary(
                    headline = "Identity carryover confirmed",
                    detail = "You marked the rotating address correlation as the same device.",
                    actionText = "Keep this verdict unless new evidence suggests the merge was wrong.",
                    tone = DetailsDecisionTone.SAFE,
                )
            IdentityCarryoverVerdict.FALSE_MATCH ->
                DetailsDecisionSummary(
                    headline = "Identity carryover marked false",
                    detail = "You marked the rotating address correlation as a false match.",
                    actionText = "Do not trust merged history for this device without new evidence.",
                    tone = DetailsDecisionTone.WARNING,
                )
            IdentityCarryoverVerdict.INCONCLUSIVE ->
                DetailsDecisionSummary(
                    headline = "Identity carryover inconclusive",
                    detail = "The rotating address correlation still needs more evidence.",
                    actionText = "Keep reviewing future observations before trusting merged history.",
                    tone = DetailsDecisionTone.WARNING,
                )
        }

    private fun trackerSummary(device: Device): DetailsDecisionSummary =
        DetailsDecisionSummary(
            headline = "Tracker-like evidence",
            detail = "Review movement history before acting. Follow-Me score ${device.followingScore.toInt()}/100.",
            actionText = "Check signal history, then mark Suspicious, False Positive, or Known Safe.",
            tone = DetailsDecisionTone.SUSPICIOUS,
        )

    private fun followMeSummary(device: Device): DetailsDecisionSummary =
        DetailsDecisionSummary(
            headline = "Review movement evidence",
            detail =
                "Follow-Me score ${device.followingScore.toInt()}/100. Review movement history before acting.",
            actionText = "Compare the timeline with your movement before changing the verdict.",
            tone = DetailsDecisionTone.SUSPICIOUS,
        )

    private fun publicSafetySummary(device: Device): DetailsDecisionSummary =
        DetailsDecisionSummary(
            headline = "Public-safety-like signal",
            detail =
                "Evidence is consistent with ${DetailsDecisionSignalClassifier.publicSafetyLabel(device)}; " +
                    "this is not a confirmed presence.",
            actionText = "Review evidence only; do not treat this as confirmed presence.",
            tone = DetailsDecisionTone.WARNING,
        )

    private fun evidenceSummary(evidence: DetectionEvidence): DetailsDecisionSummary =
        DetailsDecisionSummary(
            headline = "Evidence needs review",
            detail = evidence.reasonText,
            actionText = "Open Evidence and history before marking this device.",
            tone = DetailsDecisionTone.WARNING,
        )

    private fun noAttentionSummary(): DetailsDecisionSummary =
        DetailsDecisionSummary(
            headline = "No attention evidence",
            detail = "This device has no medium-or-higher confidence evidence right now.",
            actionText = "No action needed unless you recognize it and want return alerts.",
            tone = DetailsDecisionTone.SAFE,
        )

    private fun DeviceCalibrationLabel.toSummaryLabel(): String =
        when (this) {
            DeviceCalibrationLabel.FALSE_POSITIVE -> "Marked false positive"
            DeviceCalibrationLabel.KNOWN_SAFE -> "Marked known safe"
            DeviceCalibrationLabel.SUSPICIOUS -> "Marked suspicious"
            DeviceCalibrationLabel.TRUE_POSITIVE -> "Marked true positive"
            DeviceCalibrationLabel.UNKNOWN -> "No user verdict"
        }

    private val SUPPRESSED_LABELS = setOf(DeviceCalibrationLabel.FALSE_POSITIVE, DeviceCalibrationLabel.KNOWN_SAFE)
}

data class DetailsDecisionSummary(
    val headline: String,
    val detail: String,
    val actionText: String,
    val tone: DetailsDecisionTone,
)

enum class DetailsDecisionTone {
    SAFE,
    WARNING,
    SUSPICIOUS,
}
