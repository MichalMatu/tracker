package io.blueeye.feature.settings

import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.SignalSample

data class SessionReviewDeviceQueueItem(
    val fingerprint: String,
    val displayName: String,
    val reasonText: String,
    val actionText: String,
    val decisions: List<SessionReviewDeviceQueueDecision> = emptyList(),
)

data class SessionReviewDeviceQueueDecision(
    val text: String,
    val deviceCalibrationLabel: DeviceCalibrationLabel? = null,
    val identityCarryoverVerdict: IdentityCarryoverVerdict? = null,
    val watchlistTrackingEnabled: Boolean? = null,
) {
    init {
        val targetCount =
            listOfNotNull(
                deviceCalibrationLabel,
                identityCarryoverVerdict,
                watchlistTrackingEnabled,
            ).size
        require(targetCount == 1) {
            "Review queue decision must target exactly one verdict type."
        }
    }
}

internal object SessionReviewDeviceQueueBuilder {
    fun build(
        devices: List<Device>,
        samples: List<SignalSample>,
        alertEvidenceEvents: List<AlertEvidenceEvent>,
    ): List<SessionReviewDeviceQueueItem> {
        val samplesByDevice = samples.groupBy { sample -> sample.deviceFingerprint }
        val alertEventsByDevice = alertEvidenceEvents.groupBy { event -> event.deviceFingerprint }

        return devices
            .mapNotNull { device ->
                val category = device.sessionExportReviewCategory()
                device.toQueueCandidate(
                    category = category,
                    samples = samplesByDevice[device.fingerprint].orEmpty(),
                    alertEvents = alertEventsByDevice[device.fingerprint].orEmpty(),
                )
            }
            .sortedWith(
                compareBy<SessionReviewDeviceQueueCandidate> { candidate -> candidate.priority }
                    .thenByDescending { candidate -> candidate.lastSeenAt },
            )
            .take(SessionReviewDeviceQueueLimits.MAX_ITEMS)
            .map { candidate -> candidate.item }
    }

    private fun Device.toQueueCandidate(
        category: SessionExportReviewCategory,
        samples: List<SignalSample>,
        alertEvents: List<AlertEvidenceEvent>,
    ): SessionReviewDeviceQueueCandidate? {
        if (isSuppressedSessionReviewNoise()) return null

        return reviewReason(
            category = category,
            samples = samples,
            alertEvents = alertEvents,
        )?.let { reason ->
            SessionReviewDeviceQueueCandidate(
                item =
                    SessionReviewDeviceQueueItem(
                        fingerprint = fingerprint,
                        displayName = getDisplayName(),
                        reasonText = reason.text,
                        actionText = reason.actionText,
                        decisions = reason.decisions,
                    ),
                priority = reason.priority,
                lastSeenAt = lastSeenAt,
            )
        }
    }

    private fun Device.reviewReason(
        category: SessionExportReviewCategory,
        samples: List<SignalSample>,
        alertEvents: List<AlertEvidenceEvent>,
    ): SessionReviewDeviceQueueReason? =
        when {
            alertEvents.hasEvent(AlertEvidenceEventType.FOLLOW_ME_ALERT) ->
                SessionReviewDeviceQueueReason(
                    priority = SessionReviewDeviceQueuePriority.FOLLOW_ME_ALERT,
                    text = "Follow-Me alert",
                    actionText = SessionReviewQueueCopy.FOLLOW_ME_ALERT,
                    decisions = SessionReviewDeviceQueueDecisions.SUSPICIOUS_REVIEW,
                )
            hasActionableIdentityCarryoverEvidence() ->
                SessionReviewDeviceQueueReason(
                    priority = SessionReviewDeviceQueuePriority.IDENTITY_CARRYOVER,
                    text = "Identity carryover",
                    actionText = SessionReviewQueueCopy.IDENTITY_CARRYOVER,
                    decisions = SessionReviewDeviceQueueDecisions.IDENTITY_CARRYOVER_REVIEW,
                )
            category == SessionExportReviewCategory.SUSPICIOUS ->
                SessionReviewDeviceQueueReason(
                    priority = SessionReviewDeviceQueuePriority.SUSPICIOUS,
                    text = "Suspicious device",
                    actionText = sessionExportReviewAction(category),
                    decisions = SessionReviewDeviceQueueDecisions.SUSPICIOUS_REVIEW,
                )
            samples.hasStrengtheningRssiTrend() ->
                SessionReviewDeviceQueueReason(
                    priority = SessionReviewDeviceQueuePriority.STRENGTHENING_RSSI,
                    text = "Strengthening RSSI trend",
                    actionText = SessionReviewQueueCopy.STRENGTHENING_RSSI,
                    decisions = SessionReviewDeviceQueueDecisions.SUSPICIOUS_REVIEW,
                )
            alertEvents.hasEvent(AlertEvidenceEventType.WATCHLIST_RETURN) ->
                SessionReviewDeviceQueueReason(
                    priority = SessionReviewDeviceQueuePriority.WATCHLIST_RETURN,
                    text = "Watchlist return",
                    actionText = SessionReviewQueueCopy.WATCHLIST_RETURN,
                    decisions = SessionReviewDeviceQueueDecisions.WATCHLIST_RETURN_REVIEW,
                )
            alertEvents.hasEvent(AlertEvidenceEventType.PUBLIC_SAFETY_SIGNAL) ->
                SessionReviewDeviceQueueReason(
                    priority = SessionReviewDeviceQueuePriority.PUBLIC_SAFETY_ALERT,
                    text = "Public-safety-like signal",
                    actionText = SessionReviewQueueCopy.PUBLIC_SAFETY_ALERT,
                    decisions = SessionReviewDeviceQueueDecisions.EXPECTED_OR_FALSE_POSITIVE,
                )
            category == SessionExportReviewCategory.WATCHLIST ->
                SessionReviewDeviceQueueReason(
                    priority = SessionReviewDeviceQueuePriority.WATCHLIST,
                    text = "Watchlist device",
                    actionText = sessionExportReviewAction(category),
                )
            category == SessionExportReviewCategory.PUBLIC_SAFETY ->
                SessionReviewDeviceQueueReason(
                    priority = SessionReviewDeviceQueuePriority.PUBLIC_SAFETY,
                    text = "Public-safety-like signal",
                    actionText = sessionExportReviewAction(category),
                    decisions = SessionReviewDeviceQueueDecisions.EXPECTED_OR_FALSE_POSITIVE,
                )
            category == SessionExportReviewCategory.UNKNOWN_NOISE ->
                SessionReviewDeviceQueueReason(
                    priority = SessionReviewDeviceQueuePriority.UNKNOWN_NOISE,
                    text = "Unknown/noise device",
                    actionText = sessionExportReviewAction(category),
                    decisions = SessionReviewDeviceQueueDecisions.EXPECTED_OR_FALSE_POSITIVE,
                )
            else -> null
        }
}

private data class SessionReviewDeviceQueueCandidate(
    val item: SessionReviewDeviceQueueItem,
    val priority: Int,
    val lastSeenAt: Long,
)

private data class SessionReviewDeviceQueueReason(
    val priority: Int,
    val text: String,
    val actionText: String,
    val decisions: List<SessionReviewDeviceQueueDecision> = emptyList(),
)

private fun List<AlertEvidenceEvent>.hasEvent(type: AlertEvidenceEventType): Boolean {
    return any { event -> event.eventType == type }
}

private fun List<SignalSample>.hasStrengtheningRssiTrend(): Boolean {
    return RssiTrendAnalyzer.analyze(this).direction == RssiTrendDirection.STRENGTHENING
}

private object SessionReviewDeviceQueuePriority {
    const val FOLLOW_ME_ALERT = 10
    const val IDENTITY_CARRYOVER = 20
    const val SUSPICIOUS = 30
    const val STRENGTHENING_RSSI = 40
    const val WATCHLIST_RETURN = 50
    const val PUBLIC_SAFETY_ALERT = 60
    const val WATCHLIST = 70
    const val PUBLIC_SAFETY = 80
    const val UNKNOWN_NOISE = 90
}

private object SessionReviewDeviceQueueLimits {
    const val MAX_ITEMS = 3
}

private object SessionReviewDeviceQueueDecisions {
    val SUSPICIOUS_REVIEW =
        listOf(
            SessionReviewDeviceQueueDecision(
                text = "Mark suspicious",
                deviceCalibrationLabel = DeviceCalibrationLabel.SUSPICIOUS,
            ),
            SessionReviewDeviceQueueDecision(
                text = "False positive",
                deviceCalibrationLabel = DeviceCalibrationLabel.FALSE_POSITIVE,
            ),
        )
    val EXPECTED_OR_FALSE_POSITIVE =
        listOf(
            SessionReviewDeviceQueueDecision(
                text = "Known safe",
                deviceCalibrationLabel = DeviceCalibrationLabel.KNOWN_SAFE,
            ),
            SessionReviewDeviceQueueDecision(
                text = "False positive",
                deviceCalibrationLabel = DeviceCalibrationLabel.FALSE_POSITIVE,
            ),
        )
    val IDENTITY_CARRYOVER_REVIEW =
        listOf(
            SessionReviewDeviceQueueDecision(
                text = "Same device",
                identityCarryoverVerdict = IdentityCarryoverVerdict.CONFIRMED_SAME_DEVICE,
            ),
            SessionReviewDeviceQueueDecision(
                text = "False match",
                identityCarryoverVerdict = IdentityCarryoverVerdict.FALSE_MATCH,
            ),
            SessionReviewDeviceQueueDecision(
                text = "Inconclusive",
                identityCarryoverVerdict = IdentityCarryoverVerdict.INCONCLUSIVE,
            ),
        )
    val WATCHLIST_RETURN_REVIEW =
        listOf(
            SessionReviewDeviceQueueDecision(
                text = "Pause alerts",
                watchlistTrackingEnabled = false,
            ),
            SessionReviewDeviceQueueDecision(
                text = "Known safe",
                deviceCalibrationLabel = DeviceCalibrationLabel.KNOWN_SAFE,
            ),
        )
}
