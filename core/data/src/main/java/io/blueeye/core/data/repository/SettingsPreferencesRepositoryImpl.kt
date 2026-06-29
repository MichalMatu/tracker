package io.blueeye.core.data.repository

import io.blueeye.core.data.preferences.AppPreferences
import io.blueeye.core.data.preferences.WatchlistPreferences
import io.blueeye.core.domain.repository.AppearanceSettings
import io.blueeye.core.domain.repository.SessionSettings
import io.blueeye.core.domain.repository.SettingsPreferencesRepository
import io.blueeye.core.domain.repository.TrackerAlertSetting
import io.blueeye.core.domain.repository.TrackerAlertSettings
import io.blueeye.core.model.DeviceCalibrationLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsPreferencesRepositoryImpl @Inject constructor(
    private val appPreferences: AppPreferences,
    private val watchlistPreferences: WatchlistPreferences,
) : SettingsPreferencesRepository {
    override val trackerAlerts: Flow<TrackerAlertSettings> =
        combine(
            watchlistPreferences.trackerDetectionEnabled,
            watchlistPreferences.trackerVibrationEnabled,
            watchlistPreferences.trackerSoundEnabled,
            watchlistPreferences.trackerHeadsUpEnabled,
        ) { detectionEnabled, vibrationEnabled, soundEnabled, headsUpEnabled ->
            TrackerAlertSettings(
                detectionEnabled = detectionEnabled,
                vibrationEnabled = vibrationEnabled,
                soundEnabled = soundEnabled,
                headsUpEnabled = headsUpEnabled,
            )
        }

    override val lastDbUpdateTimestamp: Flow<Long> = appPreferences.lastDbUpdateTimestamp

    override val appearance: Flow<AppearanceSettings> =
        combine(
            appPreferences.themeMode,
            appPreferences.colorScheme,
            appPreferences.useDynamicColors,
        ) { themeMode, colorScheme, useDynamicColors ->
            AppearanceSettings(
                themeMode = themeMode,
                colorScheme = colorScheme,
                useDynamicColors = useDynamicColors,
            )
        }

    override val session: Flow<SessionSettings> =
        combine(
            appPreferences.sessionCalibrationLabel,
            appPreferences.sessionStartedAt,
            appPreferences.sessionNotes,
        ) { calibrationLabel, startedAt, notes ->
            SessionSettings(
                calibrationLabel = calibrationLabel,
                startedAt = startedAt,
                notes = notes,
            )
        }

    override suspend fun setTrackerAlertSetting(
        setting: TrackerAlertSetting,
        enabled: Boolean,
    ): Result<Unit> =
        runCatching {
            when (setting) {
                TrackerAlertSetting.DETECTION -> watchlistPreferences.setTrackerDetectionEnabled(enabled)
                TrackerAlertSetting.VIBRATION -> watchlistPreferences.setTrackerVibrationEnabled(enabled)
                TrackerAlertSetting.SOUND -> watchlistPreferences.setTrackerSoundEnabled(enabled)
                TrackerAlertSetting.HEADS_UP -> watchlistPreferences.setTrackerHeadsUpEnabled(enabled)
            }
        }

    override suspend fun setLastDbUpdateTimestamp(timestamp: Long): Result<Unit> =
        runCatching {
            appPreferences.setLastDbUpdateTimestamp(timestamp)
        }

    override suspend fun setThemeMode(mode: String): Result<Unit> =
        runCatching {
            appPreferences.setThemeMode(mode)
        }

    override suspend fun setColorScheme(scheme: String): Result<Unit> =
        runCatching {
            appPreferences.setColorScheme(scheme)
        }

    override suspend fun setUseDynamicColors(use: Boolean): Result<Unit> =
        runCatching {
            appPreferences.setUseDynamicColors(use)
        }

    override suspend fun setSessionCalibrationLabel(label: DeviceCalibrationLabel): Result<Unit> =
        runCatching {
            appPreferences.setSessionCalibrationLabel(label)
        }

    override suspend fun setSessionNotes(notes: String): Result<Unit> =
        runCatching {
            appPreferences.setSessionNotes(notes)
        }

    override suspend fun startNewSession(): Result<Unit> =
        runCatching {
            appPreferences.startNewSession()
        }
}
