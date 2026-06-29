package io.blueeye.feature.details

import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel

object DetailsCalibrationUiFormatter {
    fun format(device: Device): DetailsCalibrationUiInfo {
        val activeLabel = activeLabel(device)
        return DetailsCalibrationUiInfo(
            statusText = activeLabel.displayText,
            description = activeLabel.description,
            actions =
                DeviceCalibrationLabel.values().map { label ->
                    DetailsCalibrationAction(
                        label = label,
                        text = label.displayText,
                        isSelected = label == activeLabel,
                    )
                },
        )
    }

    private fun activeLabel(device: Device): DeviceCalibrationLabel {
        return when {
            device.calibrationLabel != DeviceCalibrationLabel.UNKNOWN -> device.calibrationLabel
            device.isSafeBeacon || device.isIgnoredForTracking || !device.isTrackingEnabled ->
                DeviceCalibrationLabel.KNOWN_SAFE
            else -> DeviceCalibrationLabel.UNKNOWN
        }
    }
}

data class DetailsCalibrationUiInfo(
    val statusText: String,
    val description: String,
    val actions: List<DetailsCalibrationAction>,
)

data class DetailsCalibrationAction(
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

private val DeviceCalibrationLabel.description: String
    get() =
        when (this) {
            DeviceCalibrationLabel.TRUE_POSITIVE ->
                "Confirmed useful signal. This device stays active for evidence and future scoring review."
            DeviceCalibrationLabel.FALSE_POSITIVE ->
                "Confirmed false alarm. Alerts and Follow-Me scoring are suppressed for this device."
            DeviceCalibrationLabel.KNOWN_SAFE ->
                "Known safe device. Alerts and Follow-Me scoring are suppressed for this device."
            DeviceCalibrationLabel.UNKNOWN ->
                "No user verdict yet. This device can still contribute to Follow-Me scoring and evidence review."
            DeviceCalibrationLabel.SUSPICIOUS ->
                "User-marked suspicious signal. The device stays active for future evidence review."
        }
