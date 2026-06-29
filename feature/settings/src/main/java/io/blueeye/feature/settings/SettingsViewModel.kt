package io.blueeye.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.blueeye.core.domain.calibration.suppressesTracking
import io.blueeye.core.domain.calibration.toCalibrationDeviceConfig
import io.blueeye.core.domain.repository.ActiveCollectionRepository
import io.blueeye.core.domain.repository.DeviceRepository
import io.blueeye.core.domain.repository.SettingsPreferencesRepository
import io.blueeye.core.domain.repository.TrackerAlertSetting
import io.blueeye.core.domain.repository.WatchlistRepository
import io.blueeye.core.domain.scanner.ScannerRuntimeController
import io.blueeye.core.domain.settings.ReferenceDatabaseCounts
import io.blueeye.core.domain.settings.ReferenceDatabaseRepository
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.IdentityCarryoverVerdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val companyIdCount: Int = 0,
    val ouiCount: Int = 0,
    val gattServiceCount: Int = 0,
    val gattCharacteristicCount: Int = 0,
    val updateStatus: String? = null,
    val isUpdating: Boolean = false,
    val lastUpdateTimestamp: Long = 0L,
    val trackerDetectionEnabled: Boolean = true,
    val trackerVibrationEnabled: Boolean = true,
    val trackerSoundEnabled: Boolean = true,
    val trackerHeadsUpEnabled: Boolean = true,
    val autoActiveProbeEnabled: Boolean = false,
    val themeMode: String = "System",
    val colorSchemeName: String = "Classic",
    val useDynamicColors: Boolean = false,
    val sessionCalibrationLabel: DeviceCalibrationLabel = DeviceCalibrationLabel.UNKNOWN,
    val sessionStartedAt: Long = 0L,
    val sessionNotes: String = "",
    val sessionStats: SessionStats = SessionStats(),
    val sessionReviewReadiness: SessionReviewReadiness = SessionReviewReadiness(),
)

@HiltViewModel
@Suppress(
    "MaxLineLength",
    "ArgumentListWrapping",
    "PropertyWrapping",
    "TooManyFunctions",
    "TooGenericExceptionCaught",
    "LongParameterList",
)
class SettingsViewModel
    @Inject
    constructor(
        private val referenceDatabaseRepository: ReferenceDatabaseRepository,
        private val settingsPreferencesRepository: SettingsPreferencesRepository,
        private val activeCollectionRepository: ActiveCollectionRepository,
        private val deviceRepository: DeviceRepository,
        private val watchlistRepository: WatchlistRepository,
        private val databaseExporter: DatabaseExporter,
        private val scannerRuntimeController: ScannerRuntimeController,
        sessionStatsProvider: SessionStatsProvider,
    ) : ViewModel() {
        private val _counts = MutableStateFlow(ReferenceDatabaseCounts())
        private val _updateStatus = MutableStateFlow<String?>(null)
        private val _isUpdating = MutableStateFlow(false)

        private val alertPreferencesFlow =
            combine(
                settingsPreferencesRepository.trackerAlerts,
                activeCollectionRepository.autoActiveProbeEnabled,
            ) { trackerAlerts, autoProbe ->
                AlertPreferencesState(
                    detection = trackerAlerts.detectionEnabled,
                    vibration = trackerAlerts.vibrationEnabled,
                    sound = trackerAlerts.soundEnabled,
                    headsUp = trackerAlerts.headsUpEnabled,
                    autoActiveProbe = autoProbe,
                )
            }

        private val appPreferencesFlow =
            combine(
                settingsPreferencesRepository.lastDbUpdateTimestamp,
                settingsPreferencesRepository.appearance,
                settingsPreferencesRepository.session,
            ) { lastTimestamp, appearance, session ->
                SettingsPreferencesState(
                    lastUpdateTimestamp = lastTimestamp,
                    themeMode = appearance.themeMode,
                    colorScheme = appearance.colorScheme,
                    useDynamicColors = appearance.useDynamicColors,
                    sessionCalibrationLabel = session.calibrationLabel,
                    sessionStartedAt = session.startedAt,
                    sessionNotes = session.notes,
                )
            }

        private val appSessionFlow =
            combine(
                appPreferencesFlow,
                sessionStatsProvider.stats,
            ) { preferences, sessionStats ->
                AppSessionState(
                    preferences = preferences,
                    sessionStats = sessionStats,
                )
            }

        val uiState: StateFlow<SettingsUiState> =
            combine(
                _counts,
                _updateStatus,
                _isUpdating,
                alertPreferencesFlow,
                appSessionFlow
            ) { counts, status, isUpdating, alerts, appSession ->
                val prefs = appSession.preferences
                val sessionStats = appSession.sessionStats
                SettingsUiState(
                    companyIdCount = counts.companyIdCount,
                    ouiCount = counts.ouiCount,
                    gattServiceCount = counts.gattServiceCount,
                    gattCharacteristicCount = counts.gattCharacteristicCount,
                    updateStatus = status,
                    isUpdating = isUpdating,
                    lastUpdateTimestamp = prefs.lastUpdateTimestamp,
                    trackerDetectionEnabled = alerts.detection,
                    trackerVibrationEnabled = alerts.vibration,
                    trackerSoundEnabled = alerts.sound,
                    trackerHeadsUpEnabled = alerts.headsUp,
                    autoActiveProbeEnabled = alerts.autoActiveProbe,
                    themeMode = prefs.themeMode,
                    colorSchemeName = prefs.colorScheme,
                    useDynamicColors = prefs.useDynamicColors,
                    sessionCalibrationLabel = prefs.sessionCalibrationLabel,
                    sessionStartedAt = prefs.sessionStartedAt,
                    sessionNotes = prefs.sessionNotes,
                    sessionStats = sessionStats,
                    sessionReviewReadiness =
                        SessionReviewReadinessCalculator.calculate(
                            SessionReviewReadinessInput(
                                label = prefs.sessionCalibrationLabel,
                                notes = prefs.sessionNotes,
                                deviceCount = sessionStats.deviceCount,
                                sampleCount = sessionStats.sampleCount,
                                attentionEvidenceCount = sessionStats.attentionEvidenceCount,
                                activeCollection =
                                    SessionReviewActiveCollection(
                                        enabled = alerts.autoActiveProbe,
                                        dataDeviceCount = sessionStats.activeProbeSummary.dataDeviceCount,
                                    ),
                            ),
                        ),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = SettingsUiState(),
            )

        init {
            loadData()
        }

        private fun loadData() {
            viewModelScope.launch {
                referenceDatabaseRepository.initialize()
                refreshCounts()
            }
        }

        private suspend fun refreshCounts() {
            referenceDatabaseRepository
                .getCounts()
                .onSuccess { counts -> _counts.value = counts }
                .onFailure { error -> _updateStatus.value = "Database counts unavailable: ${error.message}" }
        }

        private suspend fun updateLastTimestamp() {
            settingsPreferencesRepository
                .setLastDbUpdateTimestamp(System.currentTimeMillis())
                .onFailure { error ->
                    _updateStatus.value = "Updated, but timestamp was not saved: ${error.message}"
                }
        }

        fun updateAll() {
            if (_isUpdating.value) return
            startUpdate("Starting update...")

            viewModelScope.launch {
                try {
                    val companyOk = downloadItem("Downloading Company IDs...") { referenceDatabaseRepository.updateCompanyIds() }
                    val ouiOk = downloadItem("Downloading MAC OUI...") { referenceDatabaseRepository.updateOui() }
                    val servicesOk = downloadItem("Downloading Services...") { referenceDatabaseRepository.updateGattServices() }
                    val charsOk =
                        downloadItem("Downloading Characteristics...") {
                            referenceDatabaseRepository.updateGattCharacteristics()
                        }

                    _updateStatus.value = "Reloading databases..."
                    refreshCounts()

                    val allSuccess = companyOk && ouiOk && servicesOk && charsOk
                    val anySuccess = companyOk || ouiOk || servicesOk || charsOk

                    if (allSuccess) {
                        _updateStatus.value = "All databases updated successfully"
                        updateLastTimestamp()
                    } else {
                        _updateStatus.value = "Update finished with some errors"
                        if (anySuccess) {
                            updateLastTimestamp()
                        }
                    }
                } catch (e: Exception) {
                    _updateStatus.value = "Error: ${e.message}"
                } finally {
                    _isUpdating.value = false
                }
            }
        }

        fun updateCompanyIds() {
            updateSingleItem(
                "Company IDs",
                { referenceDatabaseRepository.updateCompanyIds() },
            )
        }

        fun updateOui() {
            updateSingleItem(
                "MAC OUI",
                { referenceDatabaseRepository.updateOui() },
            )
        }

        fun updateGattServices() {
            updateSingleItem(
                "GATT Services",
                { referenceDatabaseRepository.updateGattServices() },
            )
        }

        fun updateGattCharacteristics() {
            updateSingleItem(
                "GATT Characteristics",
                { referenceDatabaseRepository.updateGattCharacteristics() },
            )
        }

        private fun updateSingleItem(
            name: String,
            downloadAction: suspend () -> Result<Boolean>,
        ) {
            if (_isUpdating.value) return
            startUpdate("Updating $name...")
            viewModelScope.launch {
                try {
                    if (downloadAction().getOrDefault(false)) {
                        refreshCounts()
                        _updateStatus.value = "$name updated"
                        updateLastTimestamp()
                    } else {
                        _updateStatus.value = "Failed to update $name"
                    }
                } catch (e: Exception) {
                    _updateStatus.value = "Error: ${e.message}"
                } finally {
                    _isUpdating.value = false
                }
            }
        }

        private suspend fun downloadItem(
            status: String,
            action: suspend () -> Result<Boolean>,
        ): Boolean {
            _updateStatus.value = status
            return action().getOrDefault(false)
        }

        private fun startUpdate(initialStatus: String) {
            _isUpdating.value = true
            _updateStatus.value = initialStatus
        }

        fun exportDatabase(onResult: (String?) -> Unit) {
            viewModelScope.launch {
                val json = databaseExporter.export()
                onResult(json)
            }
        }

        fun setTrackerDetectionEnabled(enabled: Boolean) {
            updatePreference {
                settingsPreferencesRepository.setTrackerAlertSetting(
                    setting = TrackerAlertSetting.DETECTION,
                    enabled = enabled,
                )
            }
        }

        fun setTrackerVibrationEnabled(enabled: Boolean) {
            updatePreference {
                settingsPreferencesRepository.setTrackerAlertSetting(
                    setting = TrackerAlertSetting.VIBRATION,
                    enabled = enabled,
                )
            }
        }

        fun setTrackerSoundEnabled(enabled: Boolean) {
            updatePreference {
                settingsPreferencesRepository.setTrackerAlertSetting(
                    setting = TrackerAlertSetting.SOUND,
                    enabled = enabled,
                )
            }
        }

        fun setTrackerHeadsUpEnabled(enabled: Boolean) {
            updatePreference {
                settingsPreferencesRepository.setTrackerAlertSetting(
                    setting = TrackerAlertSetting.HEADS_UP,
                    enabled = enabled,
                )
            }
        }

        fun setAutoActiveProbeEnabled(enabled: Boolean) {
            updatePreference { activeCollectionRepository.setAutoActiveProbeEnabled(enabled) }
        }

        fun setThemeMode(mode: String) {
            updatePreference { settingsPreferencesRepository.setThemeMode(mode) }
        }

        fun setColorScheme(scheme: String) {
            updatePreference { settingsPreferencesRepository.setColorScheme(scheme) }
        }

        fun setUseDynamicColors(use: Boolean) {
            updatePreference { settingsPreferencesRepository.setUseDynamicColors(use) }
        }

        fun setSessionCalibrationLabel(label: DeviceCalibrationLabel) {
            updatePreference { settingsPreferencesRepository.setSessionCalibrationLabel(label) }
        }

        internal fun applyReviewDeviceAction(
            fingerprint: String,
            action: SessionReviewQueueAction,
        ) {
            val calibrationLabel = action.deviceCalibrationLabel
            val identityVerdict = action.identityCarryoverVerdict
            val watchlistTrackingEnabled = action.watchlistTrackingEnabled
            when {
                calibrationLabel != null -> setReviewDeviceCalibrationLabel(fingerprint, calibrationLabel)
                identityVerdict != null -> setReviewDeviceIdentityCarryoverVerdict(fingerprint, identityVerdict)
                watchlistTrackingEnabled != null -> {
                    setReviewDeviceWatchlistTracking(fingerprint, watchlistTrackingEnabled)
                }
                else -> _updateStatus.value = "Review action failed: unsupported action"
            }
        }

        private fun setReviewDeviceCalibrationLabel(
            fingerprint: String,
            label: DeviceCalibrationLabel,
        ) {
            viewModelScope.launch {
                val deviceResult = deviceRepository.getDeviceByFingerprint(fingerprint)
                val device = deviceResult.getOrNull()
                if (device == null) {
                    _updateStatus.value = "Review action failed: device unavailable"
                    return@launch
                }
                val updateResult =
                    deviceRepository.updateDeviceConfig(
                        fingerprint = fingerprint,
                        config = device.toCalibrationDeviceConfig(label),
                    )
                if (updateResult.isSuccess) {
                    deviceRepository.setIgnoredForTracking(fingerprint, label.suppressesTracking())
                    deviceRepository.setCalibrationLabel(fingerprint, label)
                    _updateStatus.value = "Review action saved: ${label.name}"
                } else {
                    _updateStatus.value = "Review action failed"
                }
            }
        }

        private fun setReviewDeviceIdentityCarryoverVerdict(
            fingerprint: String,
            verdict: IdentityCarryoverVerdict,
        ) {
            viewModelScope.launch {
                val device = deviceRepository.getDeviceByFingerprint(fingerprint).getOrNull()
                if (device == null) {
                    _updateStatus.value = "Identity review failed: device unavailable"
                    return@launch
                }
                deviceRepository.setIdentityCarryoverVerdict(fingerprint, verdict)
                    .onSuccess {
                        _updateStatus.value = "Identity review saved: ${verdict.name}"
                    }.onFailure {
                        _updateStatus.value = "Identity review failed"
                    }
            }
        }

        private fun setReviewDeviceWatchlistTracking(
            fingerprint: String,
            enabled: Boolean,
        ) {
            viewModelScope.launch {
                watchlistRepository.setTrackingEnabled(fingerprint, enabled)
                    .onSuccess {
                        _updateStatus.value =
                            if (enabled) {
                                "Watchlist alerts resumed"
                            } else {
                                "Watchlist alerts paused"
                            }
                    }.onFailure {
                        _updateStatus.value = "Watchlist review failed"
                    }
            }
        }

        fun setSessionNotes(notes: String) {
            updatePreference { settingsPreferencesRepository.setSessionNotes(notes) }
        }

        fun startNewSession() {
            viewModelScope.launch {
                settingsPreferencesRepository
                    .startNewSession()
                    .onFailure { error ->
                        _updateStatus.value = "Session could not start: ${error.message}"
                        return@launch
                    }
                scannerRuntimeController.resetTrackingMemory()
                    .onSuccess {
                        _updateStatus.value = "Session started and tracking memory reset"
                    }.onFailure { error ->
                        _updateStatus.value = "Session started, but tracking reset failed: ${error.message}"
                    }
            }
        }

        private fun updatePreference(action: suspend () -> Result<Unit>) {
            viewModelScope.launch {
                action()
                    .onFailure { error ->
                        _updateStatus.value = "Settings update failed: ${error.message}"
                    }
            }
        }
    }

private data class AlertPreferencesState(
    val detection: Boolean,
    val vibration: Boolean,
    val sound: Boolean,
    val headsUp: Boolean,
    val autoActiveProbe: Boolean,
)

private data class SettingsPreferencesState(
    val lastUpdateTimestamp: Long,
    val themeMode: String,
    val colorScheme: String,
    val useDynamicColors: Boolean,
    val sessionCalibrationLabel: DeviceCalibrationLabel = DeviceCalibrationLabel.UNKNOWN,
    val sessionStartedAt: Long = 0L,
    val sessionNotes: String = "",
)

private data class AppSessionState(
    val preferences: SettingsPreferencesState,
    val sessionStats: SessionStats,
)
