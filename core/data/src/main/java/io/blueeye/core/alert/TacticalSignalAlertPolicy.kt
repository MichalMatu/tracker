package io.blueeye.core.alert

import io.blueeye.core.data.classifier.vendor.tactical.ConfidenceLevel
import io.blueeye.core.model.EvidenceSource

internal object TacticalSignalAlertPolicy {
    fun shouldVibrate(isNewDevice: Boolean, evidenceSource: EvidenceSource): Boolean =
        isNewDevice && evidenceSource != EvidenceSource.NAME

    fun vibrationLevel(confidence: ConfidenceLevel): ConfidenceLevel =
        when (confidence) {
            ConfidenceLevel.CRITICAL -> ConfidenceLevel.HIGH
            ConfidenceLevel.HIGH -> ConfidenceLevel.MEDIUM
            ConfidenceLevel.MEDIUM -> ConfidenceLevel.MEDIUM
        }
}
