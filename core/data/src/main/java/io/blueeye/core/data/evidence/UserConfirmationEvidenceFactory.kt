package io.blueeye.core.data.evidence

import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource

internal object UserConfirmationEvidenceFactory {
    fun build(device: DeviceEntity): DetectionEvidence? {
        val label = device.calibrationLabel.takeUnless { it == DeviceCalibrationLabel.UNKNOWN } ?: return null

        return DetectionEvidence(
            source = EvidenceSource.USER_CONFIRMATION,
            confidence = label.toDetectionConfidence(),
            reasonText = label.reasonText,
            timestamp = device.lastSeenAt,
            rawValue = label.name,
            parsedValue = label.displayText,
            isPassive = true,
            provenance = EvidenceProvenance.USER_ACTION,
        )
    }
}

private fun DeviceCalibrationLabel.toDetectionConfidence(): DetectionConfidence =
    when (this) {
        DeviceCalibrationLabel.TRUE_POSITIVE,
        DeviceCalibrationLabel.SUSPICIOUS,
        -> DetectionConfidence.MEDIUM
        DeviceCalibrationLabel.FALSE_POSITIVE,
        DeviceCalibrationLabel.KNOWN_SAFE,
        DeviceCalibrationLabel.UNKNOWN,
        -> DetectionConfidence.LOW
    }

private val DeviceCalibrationLabel.displayText: String
    get() =
        when (this) {
            DeviceCalibrationLabel.TRUE_POSITIVE -> "True positive"
            DeviceCalibrationLabel.FALSE_POSITIVE -> "False positive"
            DeviceCalibrationLabel.KNOWN_SAFE -> "Known safe"
            DeviceCalibrationLabel.UNKNOWN -> "Unknown"
            DeviceCalibrationLabel.SUSPICIOUS -> "Suspicious"
        }

private val DeviceCalibrationLabel.reasonText: String
    get() =
        when (this) {
            DeviceCalibrationLabel.TRUE_POSITIVE ->
                "User marked this device as a true positive for calibration review."
            DeviceCalibrationLabel.FALSE_POSITIVE ->
                "User marked this device as a false positive; alerts and Follow-Me scoring are suppressed."
            DeviceCalibrationLabel.KNOWN_SAFE ->
                "User marked this device as known safe; alerts and Follow-Me scoring are suppressed."
            DeviceCalibrationLabel.UNKNOWN ->
                "No user calibration verdict."
            DeviceCalibrationLabel.SUSPICIOUS ->
                "User marked this device as suspicious for future evidence review."
        }
