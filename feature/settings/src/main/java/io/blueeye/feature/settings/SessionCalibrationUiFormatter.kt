package io.blueeye.feature.settings

import io.blueeye.core.model.DeviceCalibrationLabel

object SessionCalibrationUiFormatter {
    fun format(label: DeviceCalibrationLabel): SessionCalibrationUiInfo {
        return SessionCalibrationUiInfo(
            statusText = label.displayText,
            description = label.sessionDescription,
            actions =
                sessionLabelOrder.map { item ->
                    SessionCalibrationAction(
                        label = item,
                        text = item.displayText,
                        isSelected = item == label,
                    )
                },
        )
    }

    private val sessionLabelOrder =
        listOf(
            DeviceCalibrationLabel.UNKNOWN,
            DeviceCalibrationLabel.TRUE_POSITIVE,
            DeviceCalibrationLabel.SUSPICIOUS,
            DeviceCalibrationLabel.FALSE_POSITIVE,
            DeviceCalibrationLabel.KNOWN_SAFE,
        )
}

data class SessionCalibrationUiInfo(
    val statusText: String,
    val description: String,
    val actions: List<SessionCalibrationAction>,
)

data class SessionCalibrationAction(
    val label: DeviceCalibrationLabel,
    val text: String,
    val isSelected: Boolean,
)

private val DeviceCalibrationLabel.displayText: String
    get() =
        when (this) {
            DeviceCalibrationLabel.TRUE_POSITIVE -> "True positive"
            DeviceCalibrationLabel.FALSE_POSITIVE -> "False positive"
            DeviceCalibrationLabel.KNOWN_SAFE -> "Known safe"
            DeviceCalibrationLabel.UNKNOWN -> "Unknown"
            DeviceCalibrationLabel.SUSPICIOUS -> "Suspicious"
        }

private val DeviceCalibrationLabel.sessionDescription: String
    get() =
        when (this) {
            DeviceCalibrationLabel.TRUE_POSITIVE ->
                "This session contains a confirmed useful detection signal for later heuristic review."
            DeviceCalibrationLabel.FALSE_POSITIVE ->
                "This session produced false alarms and should be used to lower noisy heuristics."
            DeviceCalibrationLabel.KNOWN_SAFE ->
                "This session is a known-safe baseline, useful for suppressing home or routine noise."
            DeviceCalibrationLabel.UNKNOWN ->
                "No verdict yet. Export still includes devices, samples, and evidence for later review."
            DeviceCalibrationLabel.SUSPICIOUS ->
                "This session contains user-marked suspicious signals that need evidence review."
        }
