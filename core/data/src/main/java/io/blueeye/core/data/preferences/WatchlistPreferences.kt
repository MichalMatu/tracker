package io.blueeye.core.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences for Watchlist and Tactical Detection settings.
 */
@Singleton
open class WatchlistPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val tacticalDetectionEnabledKey = booleanPreferencesKey("tactical_detection_enabled")
    private val tacticalVibrationEnabledKey = booleanPreferencesKey("tactical_vibration_enabled")
    private val favoriteVibrationEnabledKey = booleanPreferencesKey("favorite_vibration_enabled")
    private val trackerDetectionEnabledKey = booleanPreferencesKey("tracker_detection_enabled")
    private val trackerVibrationEnabledKey = booleanPreferencesKey("tracker_vibration_enabled")
    private val trackerSoundEnabledKey = booleanPreferencesKey("tracker_sound_enabled")
    private val trackerHeadsUpEnabledKey = booleanPreferencesKey("tracker_heads_up_enabled")
    private val autoActiveProbeEnabledKey = booleanPreferencesKey("auto_active_probe_enabled")

    /** Whether professional/public-safety signal detection is enabled. */
    val tacticalDetectionEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[tacticalDetectionEnabledKey] ?: true // Default ON
        }

    /**
     * Whether personal safety tracker detection (AirTag, Tile) is enabled.
     */
    val trackerDetectionEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[trackerDetectionEnabledKey] ?: true // Default ON
        }

    /**
     * Whether to vibrate when a tracker is detected.
     */
    val trackerVibrationEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[trackerVibrationEnabledKey] ?: true // Default ON
        }

    /**
     * Whether to play sound when a tracker is detected.
     */
    val trackerSoundEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[trackerSoundEnabledKey] ?: true // Default ON
        }

    /**
     * Whether to show Heads-Up notification (high priority) for tracker alerts.
     */
    val trackerHeadsUpEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[trackerHeadsUpEnabledKey] ?: true // Default ON
        }

    /**
     * Whether scanner may automatically connect to connectable BLE devices to collect GATT evidence.
     */
    val autoActiveProbeEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[autoActiveProbeEnabledKey] ?: false
        }

    /**
     * Whether to vibrate when public-safety-style signal evidence is observed.
     */
    val tacticalVibrationEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[tacticalVibrationEnabledKey] ?: true // Default ON
        }

    /**
     * Whether to vibrate when favorite device appears/disappears.
     */
    val favoriteVibrationEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[favoriteVibrationEnabledKey] ?: true // Default ON
        }

    suspend fun setTacticalDetectionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[tacticalDetectionEnabledKey] = enabled
        }
    }

    suspend fun setTrackerDetectionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[trackerDetectionEnabledKey] = enabled
        }
    }

    suspend fun setTrackerVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[trackerVibrationEnabledKey] = enabled
        }
    }

    suspend fun setTrackerSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[trackerSoundEnabledKey] = enabled
        }
    }

    suspend fun setTrackerHeadsUpEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[trackerHeadsUpEnabledKey] = enabled
        }
    }

    suspend fun setAutoActiveProbeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[autoActiveProbeEnabledKey] = enabled
        }
    }

    suspend fun setTacticalVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[tacticalVibrationEnabledKey] = enabled
        }
    }

    suspend fun setFavoriteVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[favoriteVibrationEnabledKey] = enabled
        }
    }
}
