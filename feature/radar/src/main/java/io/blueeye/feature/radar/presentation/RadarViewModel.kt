package io.blueeye.feature.radar.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.blueeye.core.domain.calibration.suppressesTracking
import io.blueeye.core.domain.calibration.toCalibrationDeviceConfig
import io.blueeye.core.domain.model.DeviceFilter
import io.blueeye.core.domain.repository.ActiveCollectionRepository
import io.blueeye.core.domain.repository.WatchlistRepository
import io.blueeye.core.domain.scanner.ScannerRuntimeController
import io.blueeye.core.domain.scanner.ScannerRuntimeState
import io.blueeye.core.domain.usecase.GetScannedDevicesUseCase
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RadarViewModel
    @Inject
    constructor(
        getScannedDevicesUseCase: GetScannedDevicesUseCase,
        private val deviceRepository: io.blueeye.core.domain.repository.DeviceRepository,
        private val watchlistRepository: WatchlistRepository,
        private val scannerRuntimeController: ScannerRuntimeController,
        private val activeCollectionRepository: ActiveCollectionRepository,
    ) : ViewModel() {
        val scannerState: StateFlow<ScannerRuntimeState> = scannerRuntimeController.scannerState
        val autoActiveProbeEnabled: StateFlow<Boolean> =
            activeCollectionRepository.autoActiveProbeEnabled
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = false,
                )

        fun toggleScanning() {
            when (scannerState.value) {
                ScannerRuntimeState.Running,
                ScannerRuntimeState.Starting,
                -> scannerRuntimeController.stopScanning()
                ScannerRuntimeState.Idle,
                is ScannerRuntimeState.Error,
                -> scannerRuntimeController.startScanning()
            }
        }

        // === FILTROWANIE ===
        private val _filter = MutableStateFlow(DeviceFilter())
        val filter: StateFlow<DeviceFilter> = _filter.asStateFlow()

        private val _availableVendors = MutableStateFlow<List<String>>(emptyList())
        val availableVendors: StateFlow<List<String>> = _availableVendors.asStateFlow()

        fun updateFilter(newFilter: DeviceFilter) {
            _filter.value = newFilter
        }

        // Zbiór fingerprintów, które były widoczne w momencie włączenia trybu Baseline
        private val baselineDevices = MutableStateFlow<Set<String>?>(null)

        // Raw device stream (used for vendor extraction)
        private val rawDevicesFlow = getScannedDevicesUseCase(sinceSecondsAgo = 180)

        private val activeProbeFlow = deviceRepository.getActiveProbe()

        // Główny strumień danych z filtrowaniem
        val uiState: StateFlow<RadarUiState> =
            combine(
                rawDevicesFlow,
                baselineDevices,
                _filter,
                activeProbeFlow,
                scannerState,
            ) { devicesResult, baseline, currentFilter, activeProbe, currentScannerState ->
                if (currentScannerState is ScannerRuntimeState.Error) {
                    return@combine RadarUiState.Error(currentScannerState.message)
                }

                devicesResult.fold(
                    onSuccess = { devices ->
                        // Update available vendors for filter dialog (using full list or throttled
                        // is fine here)
                        // Note: Using throttled list to update vendors avoids recalculating on
                        // every raw emission
                        if (devices.isNotEmpty()) {
                            updateAvailableVendors(devices)
                        }

                        // Apply filters
                        val filteredDevices = applyFilter(devices, currentFilter)

                        if (filteredDevices.isEmpty()) {
                            if (devices.isEmpty()) {
                                RadarUiState.Empty
                            } else {
                                // Devices exist but filtered out
                                RadarUiState.FilteredEmpty(
                                    totalCount = devices.size,
                                    filter = currentFilter,
                                )
                            }
                        } else {
                            // Mapowanie na modele UI z flagą isNew
                            val items =
                                filteredDevices
                                    .map { device ->
                                        val isNew =
                                            baseline != null &&
                                                !baseline.contains(
                                                    device.fingerprint,
                                                )
                                        RadarUiMapper.mapToUi(device, isNew, activeProbe)
                                    }
                                    .sortedWith(RadarUiCardOrder.comparator)
                            val sections = RadarUiSectionMapper.map(items)

                            RadarUiState.Success(
                                items = items,
                                sections = sections,
                                decisionSummary = RadarDecisionSummaryMapper.summarize(sections),
                                isBaselineActive = baseline != null,
                                isFilterActive = currentFilter.isActive(),
                                totalCount = devices.size,
                            )
                        }
                    },
                    onFailure = { error ->
                        RadarUiState.Error(error.message ?: "Unknown error")
                    }
                )
            }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = RadarUiState.Loading,
                )

        private fun applyFilter(
            devices: List<Device>,
            filter: DeviceFilter,
        ): List<Device> {
            if (!filter.isActive()) return devices

            return devices.filter { device ->
                // Technology filter
                val techMatch =
                    filter.technologies.isEmpty() ||
                        filter.technologies.any { tech ->
                            tech.technologyValues.contains(device.technology)
                        }

                // Device type filter
                val typeMatch =
                    filter.deviceTypes.isEmpty() || filter.deviceTypes.contains(device.deviceType)

                // Vendor filter
                val vendorMatch =
                    filter.vendors.isEmpty() ||
                        device.vendorName?.let { filter.vendors.contains(it) } == true

                // Connectable filter
                val connectableMatch = !filter.onlyConnectable || device.isConnectable == true

                // Hide unknown filter
                val unknownMatch = !filter.hideUnknown || device.deviceType != DeviceType.UNKNOWN

                techMatch && typeMatch && vendorMatch && connectableMatch && unknownMatch
            }
        }

        private fun updateAvailableVendors(devices: List<Device>) {
            val vendors =
                devices
                    .mapNotNull { it.vendorName }
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .map { it.key }

            if (vendors != _availableVendors.value) {
                _availableVendors.value = vendors
            }
        }

        fun toggleBaseline(currentDevices: List<Device>) {
            baselineDevices.update { current ->
                if (current == null) {
                    currentDevices.map { it.fingerprint }.toSet()
                } else {
                    null
                }
            }
        }

        fun toggleAutoActiveProbe() {
            val enabled = !autoActiveProbeEnabled.value
            viewModelScope.launch {
                activeCollectionRepository.setAutoActiveProbeEnabled(enabled)
            }
        }

        fun clearAllData(keepWatchlist: Boolean = true) {
            viewModelScope.launch {
                deviceRepository.clearAllData(keepWatchlist)
                if (!keepWatchlist) {
                    scannerRuntimeController.resetTrackingMemory()
                }
            }
        }

        fun toggleWatchlist(device: Device) {
            viewModelScope.launch {
                watchlistRepository.toggleWatchlist(device.fingerprint)
            }
        }

        fun updateCalibrationLabel(
            device: Device,
            label: DeviceCalibrationLabel,
        ) {
            viewModelScope.launch {
                val result =
                    deviceRepository.updateDeviceConfig(
                        fingerprint = device.fingerprint,
                        config = device.toCalibrationDeviceConfig(label),
                    )
                if (result.isSuccess) {
                    deviceRepository.setIgnoredForTracking(device.fingerprint, label.suppressesTracking())
                    deviceRepository.setCalibrationLabel(device.fingerprint, label)
                }
            }
        }
    }

sealed class RadarUiState {
    object Loading : RadarUiState()

    object Empty : RadarUiState()

    data class FilteredEmpty(val totalCount: Int, val filter: DeviceFilter) : RadarUiState()

    data class Error(val message: String) : RadarUiState()

    data class Success(
        val items: List<RadarUiItem>,
        val sections: List<RadarUiSection>,
        val decisionSummary: RadarDecisionSummary,
        val isBaselineActive: Boolean,
        val isFilterActive: Boolean = false,
        val totalCount: Int = 0,
    ) : RadarUiState()
}
