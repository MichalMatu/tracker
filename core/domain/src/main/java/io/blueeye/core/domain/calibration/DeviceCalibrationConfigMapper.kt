package io.blueeye.core.domain.calibration

import io.blueeye.core.domain.repository.DeviceConfig
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel

fun Device.toCalibrationDeviceConfig(label: DeviceCalibrationLabel): DeviceConfig {
    val suppress = label.suppressesTracking()
    return DeviceConfig(
        alias = userAlias,
        notes = userNotes,
        isSafe = suppress,
        alertSound = if (suppress) false else alertSound,
        alertVibration = if (suppress) false else alertVibration,
        isTrackingEnabled = !suppress,
    )
}

fun DeviceCalibrationLabel.suppressesTracking(): Boolean =
    this == DeviceCalibrationLabel.FALSE_POSITIVE || this == DeviceCalibrationLabel.KNOWN_SAFE
