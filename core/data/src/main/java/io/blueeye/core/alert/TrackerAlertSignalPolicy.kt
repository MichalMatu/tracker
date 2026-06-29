package io.blueeye.core.alert

import io.blueeye.core.data.classifier.vendor.tactical.ConfidenceLevel
import io.blueeye.core.model.TrackingStatus

internal object TrackerAlertSignalPolicy {
    fun vibrationLevel(
        status: TrackingStatus,
        isKnownTracker: Boolean,
    ): ConfidenceLevel? =
        when {
            isKnownTracker -> ConfidenceLevel.HIGH
            status == TrackingStatus.DANGEROUS -> ConfidenceLevel.HIGH
            status == TrackingStatus.SUSPICIOUS -> ConfidenceLevel.MEDIUM
            else -> null
        }
}
