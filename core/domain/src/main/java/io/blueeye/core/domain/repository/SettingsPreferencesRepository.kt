package io.blueeye.core.domain.repository

import io.blueeye.core.model.DeviceCalibrationLabel
import kotlinx.coroutines.flow.Flow

data class TrackerAlertSettings(
    val detectionEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val soundEnabled: Boolean,
    val headsUpEnabled: Boolean,
)

data class AppearanceSettings(
    val themeMode: String,
    val colorScheme: String,
    val useDynamicColors: Boolean,
)

data class SessionSettings(
    val calibrationLabel: DeviceCalibrationLabel,
    val startedAt: Long,
    val notes: String,
)

enum class TrackerAlertSetting {
    DETECTION,
    VIBRATION,
    SOUND,
    HEADS_UP,
}

interface SettingsPreferencesRepository {
    val trackerAlerts: Flow<TrackerAlertSettings>
    val lastDbUpdateTimestamp: Flow<Long>
    val appearance: Flow<AppearanceSettings>
    val session: Flow<SessionSettings>

    suspend fun setTrackerAlertSetting(
        setting: TrackerAlertSetting,
        enabled: Boolean,
    ): Result<Unit>

    suspend fun setLastDbUpdateTimestamp(timestamp: Long): Result<Unit>

    suspend fun setThemeMode(mode: String): Result<Unit>

    suspend fun setColorScheme(scheme: String): Result<Unit>

    suspend fun setUseDynamicColors(use: Boolean): Result<Unit>

    suspend fun setSessionCalibrationLabel(label: DeviceCalibrationLabel): Result<Unit>

    suspend fun setSessionNotes(notes: String): Result<Unit>

    suspend fun startNewSession(): Result<Unit>
}
