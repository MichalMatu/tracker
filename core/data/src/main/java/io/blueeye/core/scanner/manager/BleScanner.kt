package io.blueeye.core.scanner.manager

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.blueeye.core.domain.repository.DeviceRepository
import io.blueeye.core.domain.scanner.ScannerRuntimePolicy
import io.blueeye.core.permission.PermissionManager
import io.blueeye.core.scanner.extractor.ScanResultExtractor
import io.blueeye.core.scanner.source.BleScanSource
import io.blueeye.core.scanner.source.ClassicScanSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ScannerState {
    object Idle : ScannerState

    object Starting : ScannerState

    object Scanning : ScannerState

    data class Focused(val macAddress: String) : ScannerState

    data class Error(val message: String) : ScannerState
}

internal fun ScannerState.allowsPassiveStart(): Boolean =
    this !is ScannerState.Starting && this !is ScannerState.Scanning

/**
 * Owns Bluetooth scan lifecycle and mode transitions.
 *
 * High-rate callback accounting, coalescing and repository processing are delegated to
 * [ScanIngestPipeline]. Keeping these responsibilities separate protects the accepted Phase 2
 * lifecycle contract from Phase 3 backpressure/observability changes.
 */
@Singleton
class BleScanner
@Inject
constructor(
    @ApplicationContext private val context: Context,
    repository: DeviceRepository,
    private val adapter: BluetoothAdapter?,
    private val bleScanSource: BleScanSource,
    private val classicScanSource: ClassicScanSource,
    scanResultExtractor: ScanResultExtractor,
) {
    companion object {
        private const val TAG = "BleScanner"
    }

    // Process-lifetime scope: ScannerService owns scan Start/Stop, while ingest processing remains
    // available for already-accepted work during the same process lifetime.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ScannerState>(ScannerState.Idle)
    val state = _state.asStateFlow()

    private val ingestPipeline =
        ScanIngestPipeline(
            scope = scope,
            repository = repository,
            scanResultExtractor = scanResultExtractor,
            onProcessingError = { message -> _state.value = ScannerState.Error(message) },
        )

    private var scanJob: Job? = null

    val isBluetoothEnabled: Boolean
        get() = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startScanning() {
        val hasPermissions = PermissionManager.hasBlePermissions(context)
        val isEnabled = adapter != null && adapter.isEnabled
        val isNotAlreadyScanning = _state.value.allowsPassiveStart()

        when {
            !hasPermissions -> {
                Log.e(TAG, "Missing permissions for scanning")
                _state.value = ScannerState.Error("Missing permissions")
            }
            !isEnabled -> {
                Log.e(TAG, "Bluetooth disabled or not available")
                _state.value = ScannerState.Error("Bluetooth disabled")
            }
            !isNotAlreadyScanning -> {
                Log.w(TAG, "Passive BLE scanning already active")
            }
            else -> {
                _state.value = ScannerState.Starting
                scanJob?.cancel()
                scanJob = scope.launch { performPassiveBleScan() }
            }
        }
    }

    @SuppressLint("MissingPermission")
    internal suspend fun performPassiveBleScan() {
        try {
            if (bleScanSource.isScanning()) {
                Log.i(TAG, "Stopping previous scan before passive BLE start...")
                bleScanSource.stop()
                delay(ScannerConstants.SCAN_TRANSITION_DELAY_MS)
            }

            Log.i(TAG, "Starting passive BLE scan...")
            val bleStarted =
                bleScanSource.start(
                    macFilter = null,
                    onResult = ingestPipeline::onBleResult,
                    onError = { errorCode ->
                        val message = describeBleScanError(errorCode)
                        Log.e(TAG, message)
                        _state.value = ScannerState.Error(message)
                    },
                )

            if (!bleStarted) {
                _state.value = ScannerState.Error("BLE scanner unavailable")
                return
            }

            if (ScannerRuntimePolicy.allowsClassicDiscovery) {
                startClassicDiscovery()
            } else {
                Log.i(
                    TAG,
                    "Classic discovery suppressed by runtime profile ${ScannerRuntimePolicy.profile}",
                )
            }
            _state.value = ScannerState.Scanning
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            Log.e(TAG, "Error starting passive BLE scan", error)
            _state.value = ScannerState.Error(error.message ?: "Unknown error")
        }
    }

    @SuppressLint("MissingPermission")
    fun startFocusedScan(macAddress: String) {
        if (!PermissionManager.hasBlePermissions(context)) {
            _state.value = ScannerState.Error("Missing permissions")
            return
        }

        val currentState = _state.value
        if (currentState is ScannerState.Focused && currentState.macAddress == macAddress) {
            return
        }

        scanJob?.cancel()
        _state.value = ScannerState.Starting
        scanJob =
            scope.launch {
                try {
                    Log.i(TAG, "Starting FOCUSED Scan on $macAddress...")
                    if (bleScanSource.isScanning()) {
                        bleScanSource.stop()
                        delay(ScannerConstants.SCAN_TRANSITION_DELAY_MS)
                    }
                    classicScanSource.stop()

                    val bleStarted =
                        bleScanSource.start(
                            macFilter = macAddress,
                            onResult = ingestPipeline::onBleResult,
                            onError = { errorCode ->
                                val message = "Focused ${describeBleScanError(errorCode)}"
                                Log.e(TAG, message)
                                _state.value = ScannerState.Error(message)
                            },
                        )

                    if (!bleStarted) {
                        _state.value = ScannerState.Error("BLE scanner unavailable")
                        return@launch
                    }

                    _state.value = ScannerState.Focused(macAddress)
                } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                    Log.e(TAG, "Error starting focused scan", error)
                    _state.value = ScannerState.Error(error.message ?: "Unknown error")
                }
            }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        Log.i(TAG, "Stopping ALL Scans")
        scanJob?.cancel()
        scanJob = null
        bleScanSource.stop()
        classicScanSource.stop()
        _state.value = ScannerState.Idle
    }

    @SuppressLint("MissingPermission")
    private fun startClassicDiscovery() {
        val classicStarted =
            classicScanSource.start { device, rssi, classOfDevice, uuids ->
                ingestPipeline.onClassicResult(
                    mac = device.address,
                    name = device.name,
                    rssi = rssi,
                    classOfDevice = classOfDevice,
                    serviceUuids = uuids.toServiceUuidStrings(),
                )
            }

        if (!classicStarted) {
            Log.w(TAG, "Classic discovery unavailable; continuing BLE-only passive scan")
        }
    }
}

private fun List<ParcelUuid>?.toServiceUuidStrings(): List<String> =
    this?.map { it.uuid.toString() }.orEmpty()

private fun describeBleScanError(errorCode: Int): String {
    val reason =
        when (errorCode) {
            android.bluetooth.le.ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "already started"
            android.bluetooth.le.ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
                "application registration failed"
            android.bluetooth.le.ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "internal error"
            android.bluetooth.le.ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED ->
                "feature unsupported"
            android.bluetooth.le.ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES ->
                "out of hardware resources"
            android.bluetooth.le.ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY ->
                "scanning too frequently"
            else -> "error code $errorCode"
        }

    return "BLE scan failed: $reason"
}
