package io.blueeye.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.blueeye.core.domain.calibration.suppressesTracking
import io.blueeye.core.domain.calibration.toCalibrationDeviceConfig
import io.blueeye.core.domain.details.DeviceConnectionController
import io.blueeye.core.domain.details.DeviceFocusedScanController
import io.blueeye.core.domain.details.DeviceSensorDataDecoder
import io.blueeye.core.domain.details.DeviceServiceResolver
import io.blueeye.core.domain.repository.DeviceConfig
import io.blueeye.core.domain.repository.DeviceRepository
import io.blueeye.core.domain.repository.WatchlistRepository
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.GattService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

@Suppress("LongParameterList", "TooGenericExceptionCaught", "TooManyFunctions", "MagicNumber")
@HiltViewModel
class DetailsViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val repository: DeviceRepository,
        private val watchlistRepository: WatchlistRepository,
        private val deviceConnectionController: DeviceConnectionController,
        private val focusedScanController: DeviceFocusedScanController,
        private val sensorDataDecoder: DeviceSensorDataDecoder,
        private val deviceServiceResolver: DeviceServiceResolver,
    ) : ViewModel() {
        val fingerprint: String = savedStateHandle.get<String>("deviceId") ?: ""
        private val exportJson = Json { prettyPrint = true }

        private val _device =
            MutableStateFlow<Device?>(
                null,
            )
        val device = _device.asStateFlow()

        val signalSamples =
            repository.getSignalSamples(fingerprint)
                .map { result -> result.getOrDefault(emptyList()) }

        val followMeHistory =
            repository.getFollowMeHistory(fingerprint)
                .map { result -> result.getOrDefault(emptyList()) }

        val alertEvidenceEvents =
            repository.getAlertEvidenceEvents(fingerprint)
                .map { result -> result.getOrDefault(emptyList()) }

        val sensorData =
            device.map { dev ->
                dev?.let { sensorDataDecoder.decode(it).getOrNull() }
            }

        val connectionState = deviceConnectionController.connectionState

        val discoveredServices =
            combine(device, deviceConnectionController.services) { dev: Device?, liveServices: List<GattService> ->
                if (liveServices.isNotEmpty()) {
                    liveServices
                } else if (dev != null) {
                    deviceServiceResolver.resolvePersistedServices(dev).getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            }

        init {
            loadDevice()
        }

        private var focusedScanMac: String? = null

        private fun loadDevice() {
            viewModelScope.launch {
                // OPTIMIZATION: Try to fetch immediately to show data while waiting for flow
                val initial = repository.getDeviceByFingerprint(fingerprint).getOrNull()

                if (initial != null) {
                    _device.value = initial
                }

                // Then subscribe to updates
                repository.getDeviceFlow(fingerprint).collect { result ->
                    val updated = result.getOrNull()

                    if (updated != null) {
                        _device.value = updated
                    } else if (_device.value == null) {
                        // Only if we still have nothing, maybe handle error/empty state
                        // For now, keep loading or previous state
                    }
                }
            }
        }

        fun refreshFocusedScan() {
            val dev = _device.value ?: return
            if (DetailsFocusedScanPolicy.canStartFocusedScan(dev.technology) && focusedScanMac != fingerprint) {
                focusedScanMac = fingerprint
                focusedScanController.startFocusedScan(fingerprint)
            }
        }

        fun toggleWatchlist() {
            val current = _device.value ?: return
            viewModelScope.launch {
                if (current.isInWatchlist) {
                    watchlistRepository.removeFromWatchlist(fingerprint)
                } else {
                    watchlistRepository.addToWatchlist(fingerprint)
                }
            }
        }

        // ... existing updateDeviceConfig ...
        fun updateDeviceConfig(
            alias: String?,
            notes: String?,
            alertSound: Boolean,
            alertVibration: Boolean,
        ) {
            val current = _device.value ?: return
            viewModelScope.launch {
                repository.updateDeviceConfig(
                    fingerprint = fingerprint,
                    config =
                        DeviceConfig(
                            alias = alias,
                            notes = notes,
                            isSafe = current.isSafeBeacon, // keep existing safe status
                            alertSound = alertSound,
                            alertVibration = alertVibration,
                            isTrackingEnabled = current.isTrackingEnabled,
                        )
                )
            }
        }

        fun updateCalibrationLabel(label: DeviceCalibrationLabel) {
            val current = _device.value ?: return
            viewModelScope.launch {
                val config = current.toCalibrationDeviceConfig(label)
                val result =
                    repository.updateDeviceConfig(
                        fingerprint = fingerprint,
                        config = config,
                    )
                if (result.isSuccess) {
                    repository.setIgnoredForTracking(fingerprint, label.suppressesTracking())
                    repository.setCalibrationLabel(fingerprint, label)
                }
            }
        }

        // New BLE Connection methods
        fun connect() {
            viewModelScope.launch {
                deviceConnectionController.connect(fingerprint)
            }
        }

        fun disconnect() {
            deviceConnectionController.disconnect()
        }

        override fun onCleared() {
            super.onCleared()
            deviceConnectionController.disconnect()
            if (focusedScanMac != null) {
                focusedScanMac = null
                focusedScanController.resumePassiveScan()
            }
        }

        fun exportJson(onResult: (String?) -> Unit) {
            viewModelScope.launch {
                try {
                    val freshDevice = repository.getDeviceByFingerprint(fingerprint).getOrNull() ?: _device.value
                    if (freshDevice == null) {
                        onResult(null)
                        return@launch
                    }

                    val servicesList =
                        deviceServiceResolver.resolvePersistedServices(freshDevice).getOrDefault(emptyList())
                    val servicesJson =
                        JsonArray(
                            servicesList.map { service ->
                                buildJsonObject {
                                    put("uuid", service.uuid)
                                    put("name", service.name)
                                    put(
                                        "characteristics",
                                        JsonArray(
                                            service.characteristics.map { characteristic ->
                                                buildJsonObject {
                                                    put("uuid", characteristic.uuid)
                                                    put("name", characteristic.name)
                                                    put("value", characteristic.value)
                                                }
                                            },
                                        ),
                                    )
                                }
                            },
                        )
                    val evidenceJson =
                        JsonArray(
                            freshDevice.evidence.map { evidence ->
                                buildJsonObject {
                                    put("source", evidence.source.name)
                                    put("confidence", evidence.confidence.name)
                                    put("reasonText", evidence.reasonText)
                                    put("timestamp", evidence.timestamp)
                                    put("rawValue", evidence.rawValue)
                                    put("parsedValue", evidence.parsedValue)
                                    put("isPassive", evidence.isPassive)
                                    put("provenance", evidence.provenance.name)
                                }
                            },
                        )

                    val exportData =
                        buildJsonObject {
                            put("mac", freshDevice.macAddress)
                            put("name", freshDevice.name)
                            put("vendor", freshDevice.vendorName)
                            put("type", freshDevice.deviceType.name)
                            put("calibrationLabel", freshDevice.calibrationLabel.name)
                            put("trackingStatus", freshDevice.trackingStatus.name)
                            put("followingScore", freshDevice.followingScore)
                            put("isSafeBeacon", freshDevice.isSafeBeacon)
                            put("isIgnoredForTracking", freshDevice.isIgnoredForTracking)
                            put("isTrackingEnabled", freshDevice.isTrackingEnabled)
                            put("model", freshDevice.modelNumber)
                            put("manufacturer", freshDevice.manufacturerName)
                            put("firmware", freshDevice.firmwareRevision)
                            put("serial", freshDevice.serialNumber)
                            put("battery", freshDevice.batteryLevel)
                            put("rssi", freshDevice.rssi)
                            put("advertisementRaw", freshDevice.lastRawData)
                            put("services", servicesJson)
                            put("evidence", evidenceJson)
                            put("timestamp", System.currentTimeMillis())
                        }
                    onResult(exportJson.encodeToString(exportData))
                } catch (e: Exception) {
                    android.util.Log.e("DetailsViewModel", "Export failed", e)
                    onResult(null)
                }
            }
        }
    }
