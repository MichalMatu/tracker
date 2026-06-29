package io.blueeye.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.blueeye.core.model.DeviceCalibrationLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class AppPreferences
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    private val lastDbUpdateKey = longPreferencesKey("last_db_update_timestamp")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val colorSchemeKey = stringPreferencesKey("color_scheme")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val sessionCalibrationLabelKey = stringPreferencesKey("session_calibration_label")
    private val sessionStartedAtKey = longPreferencesKey("session_started_at")
    private val sessionNotesKey = stringPreferencesKey("session_notes")

    val lastDbUpdateTimestamp: Flow<Long> =
        context.dataStore.data
            .map { preferences ->
                preferences[lastDbUpdateKey] ?: 0L
            }

    val themeMode: Flow<String> =
        context.dataStore.data
            .map { preferences ->
                preferences[themeModeKey] ?: "System"
            }

    val colorScheme: Flow<String> =
        context.dataStore.data
            .map { preferences ->
                preferences[colorSchemeKey] ?: "Classic"
            }

    val useDynamicColors: Flow<Boolean> =
        context.dataStore.data
            .map { preferences ->
                preferences[dynamicColorKey] ?: false
            }

    val sessionCalibrationLabel: Flow<DeviceCalibrationLabel> =
        context.dataStore.data
            .map { preferences ->
                val value = preferences[sessionCalibrationLabelKey] ?: DeviceCalibrationLabel.UNKNOWN.name
                runCatching { DeviceCalibrationLabel.valueOf(value) }.getOrDefault(DeviceCalibrationLabel.UNKNOWN)
            }

    val sessionStartedAt: Flow<Long> =
        context.dataStore.data
            .map { preferences ->
                preferences[sessionStartedAtKey] ?: 0L
            }

    val sessionNotes: Flow<String> =
        context.dataStore.data
            .map { preferences ->
                preferences[sessionNotesKey].orEmpty()
            }

    suspend fun setLastDbUpdateTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[lastDbUpdateKey] = timestamp
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[themeModeKey] = mode
        }
    }

    suspend fun setColorScheme(scheme: String) {
        context.dataStore.edit { preferences ->
            preferences[colorSchemeKey] = scheme
        }
    }

    suspend fun setUseDynamicColors(use: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[dynamicColorKey] = use
        }
    }

    suspend fun setSessionCalibrationLabel(label: DeviceCalibrationLabel) {
        context.dataStore.edit { preferences ->
            preferences[sessionCalibrationLabelKey] = label.name
            if ((preferences[sessionStartedAtKey] ?: 0L) == 0L) {
                preferences[sessionStartedAtKey] = System.currentTimeMillis()
            }
        }
    }

    suspend fun setSessionNotes(notes: String) {
        context.dataStore.edit { preferences ->
            preferences[sessionNotesKey] = notes
            if ((preferences[sessionStartedAtKey] ?: 0L) == 0L) {
                preferences[sessionStartedAtKey] = System.currentTimeMillis()
            }
        }
    }

    suspend fun startNewSession() {
        context.dataStore.edit { preferences ->
            preferences[sessionCalibrationLabelKey] = DeviceCalibrationLabel.UNKNOWN.name
            preferences[sessionStartedAtKey] = System.currentTimeMillis()
            preferences[sessionNotesKey] = ""
        }
    }
}
