package io.blueeye.feature.settings

import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel

internal fun Device.isSuppressedSessionReviewNoise(): Boolean =
    sessionExportReviewCategory() == SessionExportReviewCategory.UNKNOWN_NOISE &&
        isSessionReviewSuppressedByUser()

internal fun Device.isSessionReviewSuppressedByUser(): Boolean =
    isIgnoredForTracking ||
        calibrationLabel in USER_SUPPRESSED_CALIBRATION_LABELS

private val USER_SUPPRESSED_CALIBRATION_LABELS =
    setOf(
        DeviceCalibrationLabel.FALSE_POSITIVE,
        DeviceCalibrationLabel.KNOWN_SAFE,
    )
