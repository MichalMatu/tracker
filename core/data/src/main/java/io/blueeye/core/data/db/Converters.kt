package io.blueeye.core.data.db

import androidx.room.TypeConverter
import io.blueeye.core.model.AlertType
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.MacAddressType
import io.blueeye.core.model.TrackingStatus

/** TypeConverters dla Room - konwersja enumów do/z String. */
class Converters {
    // === DeviceType ===
    @TypeConverter fun fromDeviceType(value: DeviceType): String = value.name

    @TypeConverter
    fun toDeviceType(value: String): DeviceType =
        try {
            DeviceType.valueOf(value)
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            DeviceType.UNKNOWN
        }

    // === TrackingStatus ===
    @TypeConverter fun fromTrackingStatus(value: TrackingStatus): String = value.name

    @TypeConverter
    fun toTrackingStatus(value: String): TrackingStatus =
        try {
            TrackingStatus.valueOf(value)
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            TrackingStatus.SAFE
        }

    // === DeviceCalibrationLabel ===
    @TypeConverter fun fromDeviceCalibrationLabel(value: DeviceCalibrationLabel): String = value.name

    @TypeConverter
    fun toDeviceCalibrationLabel(value: String): DeviceCalibrationLabel =
        try {
            DeviceCalibrationLabel.valueOf(value)
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            DeviceCalibrationLabel.UNKNOWN
        }

    // === AlertType ===
    @TypeConverter fun fromAlertType(value: AlertType): String = value.name

    @TypeConverter
    fun toAlertType(value: String): AlertType =
        try {
            AlertType.valueOf(value)
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            AlertType.ON_APPEAR
        }

    // === MacAddressType ===
    @TypeConverter fun fromMacAddressType(value: MacAddressType): String = value.name

    @TypeConverter
    fun toMacAddressType(value: String): MacAddressType =
        try {
            MacAddressType.valueOf(value)
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            MacAddressType.UNKNOWN
        }
}
