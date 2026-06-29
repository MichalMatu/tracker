package io.blueeye.core.scanner.manager

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.blueeye.core.domain.repository.DeviceRepository
import io.blueeye.core.permission.PermissionManager
import io.blueeye.core.scanner.source.BleScanSource
import io.blueeye.core.scanner.source.ClassicScanSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ScannerState {
    object Idle : ScannerState

    object Starting : ScannerState

    object Scanning : ScannerState

    data class Focused(val macAddress: String) : ScannerState

    data class Error(val message: String) : ScannerState
}

/**
 * Internal sealed class for scan result processing via Channel. This replaces fire-and-forget
 * coroutines with a sequential queue.
 */
private sealed interface ScanEvent {
    data class BleResult(val result: ScanResult) : ScanEvent

    data class ClassicResult(
        val mac: String,
        val name: String?,
        val rssi: Int,
        val classOfDevice: Int?,
        val serviceUuids: List<String>,
    ) : ScanEvent
}

@Singleton
class BleScanner
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val repository: DeviceRepository,
    private val adapter: BluetoothAdapter?,
    private val bleScanSource: BleScanSource,
    private val classicScanSource: ClassicScanSource,
    private val scanResultExtractor: io.blueeye.core.scanner.extractor.ScanResultExtractor,
) {
    companion object {
        private const val TAG = "BleScanner"
        private const val SCAN_EVENT_CHANNEL_CAPACITY = 4_096
        private const val SCAN_EVENT_DROP_LOG_INTERVAL = 100L
    }

    // Use SupervisorJob so child failures don't cancel the parent
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ScannerState>(ScannerState.Idle)
    val state = _state.asStateFlow()

    private var scanJob: Job? = null
    private var processingJob: Job? = null
    private val droppedScanEvents = AtomicLong(0L)

    // Channel for sequential processing of scan results (replaces fire-and-forget)
    // Explicit capacity avoids JVM's tiny default buffered channel under dense BLE traffic.
    private val scanEventChannel =
        Channel<ScanEvent>(
            capacity = SCAN_EVENT_CHANNEL_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
            onUndeliveredElement = {
                recordDroppedScanEvent("queue overflow or shutdown")
            },
        )

    init {
        // Start the event processing loop
        startEventProcessor()
    }

    /**
     * Processes scan events sequentially from the channel. This prevents creating thousands of
     * coroutines under high load.
     */
    private fun startEventProcessor() {
        processingJob =
            scope.launch {
                for (event in scanEventChannel) {
                    try {
                        when (event) {
                            is ScanEvent.BleResult -> handleBleResult(event.result)
                            is ScanEvent.ClassicResult -> handleClassicResult(event)
                        }
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        Log.e(TAG, "Error processing scan event", e)
                    }
                }
            }
    }

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startScanning() {
        val hasPermissions = PermissionManager.hasBlePermissions(context)
        val isEnabled = adapter != null && adapter.isEnabled
        val isNotAlreadyScanning =
            _state.value !is ScannerState.Starting &&
                _state.value !is ScannerState.Scanning &&
                _state.value !is ScannerState.Focused

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
                // Cancel any pending transition/scan job
                scanJob?.cancel()
                scanJob = scope.launch {
                    performPassiveBleScan()
                }
            }
        }
    }

    private suspend fun performPassiveBleScan() {
        try {
            // If we were scanning (e.g. focused), stop first safely
            if (bleScanSource.isScanning()) {
                Log.i(TAG, "Stopping previous scan before passive BLE start...")
                bleScanSource.stop()
                delay(ScannerConstants.SCAN_TRANSITION_DELAY_MS)
            }

            Log.i(TAG, "Starting passive BLE scan...")

            val bleStarted = bleScanSource.start(
                macFilter = null,
                onResult = { result ->
                    enqueueScanEvent(ScanEvent.BleResult(result))
                },
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

            startClassicDiscovery()
            _state.value = ScannerState.Scanning
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Error starting passive BLE scan", e)
            _state.value = ScannerState.Error(e.message ?: "Unknown error")
        }
    }

    @SuppressLint("MissingPermission")
    fun startFocusedScan(macAddress: String) {
        if (!PermissionManager.hasBlePermissions(context)) {
            _state.value = ScannerState.Error("Missing permissions")
            return
        }

        // Check if already focused on this MAC
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

                    // Stop any existing scans first
                    if (bleScanSource.isScanning()) {
                        bleScanSource.stop()
                        delay(ScannerConstants.SCAN_TRANSITION_DELAY_MS)
                    }
                    classicScanSource.stop()

                    // Start only BLE with filter - send to channel
                    val bleStarted = bleScanSource.start(
                        macFilter = macAddress,
                        onResult = { result ->
                            enqueueScanEvent(ScanEvent.BleResult(result))
                        },
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
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Log.e(TAG, "Error starting focused scan", e)
                    _state.value = ScannerState.Error(e.message ?: "Unknown error")
                }
            }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        Log.i(TAG, "Stopping ALL Scans")
        scanJob?.cancel()
        bleScanSource.stop()
        classicScanSource.stop()
        _state.value = ScannerState.Idle
    }

    @SuppressLint("MissingPermission")
    private fun startClassicDiscovery() {
        val classicStarted =
            classicScanSource.start { device, rssi, classOfDevice, uuids ->
                enqueueScanEvent(
                    ScanEvent.ClassicResult(
                        mac = device.address,
                        name = device.name,
                        rssi = rssi,
                        classOfDevice = classOfDevice,
                        serviceUuids = uuids.toServiceUuidStrings(),
                    )
                )
            }

        if (!classicStarted) {
            Log.w(TAG, "Classic discovery unavailable; continuing BLE-only passive scan")
        }
    }

    private fun enqueueScanEvent(event: ScanEvent) {
        val enqueueResult = scanEventChannel.trySend(event)
        if (enqueueResult.isFailure) {
            recordDroppedScanEvent("enqueue failed")
        }
    }

    private fun recordDroppedScanEvent(reason: String) {
        val dropped = droppedScanEvents.incrementAndGet()
        if (dropped == 1L || dropped % SCAN_EVENT_DROP_LOG_INTERVAL == 0L) {
            Log.w(TAG, "Dropped $dropped scan event(s): $reason")
        }
    }

    /** Handles BLE scan result - called sequentially from event processor. */
    @SuppressLint("MissingPermission")
    private suspend fun handleBleResult(result: ScanResult) {
        val data = scanResultExtractor.extract(result)

        val params = io.blueeye.core.domain.repository.ScanResultParams(
            mac = data.mac,
            rssi = data.rssi,
            timestamp = System.currentTimeMillis(),
            technology = data.technology,
            name = data.name,
            manufacturerId = data.manufacturerId,
            manufacturerData = data.manufacturerData,
            manufacturerDataById = data.manufacturerDataById,
            serviceUuids = data.serviceUuids,
            serviceDataByUuid = data.serviceDataByUuid,
            appearance = data.appearance,
            txPower = data.txPower,
            isConnectable = data.isConnectable,
            primaryPhy = data.primaryPhy,
            secondaryPhy = data.secondaryPhy,
            rawData = data.rawData,
        )
        repository.handleScanResult(params)
            .onFailure { error ->
                val message = "Scan processing failed: ${error.message ?: error.javaClass.simpleName}"
                Log.e(TAG, message, error)
                _state.value = ScannerState.Error(message)
            }
    }

    private suspend fun handleClassicResult(result: ScanEvent.ClassicResult) {
        repository.handleClassicDiscovery(
            mac = result.mac,
            name = result.name,
            rssi = result.rssi,
            classOfDevice = result.classOfDevice,
            serviceUuids = result.serviceUuids,
        ).onFailure { error ->
            val message = "Classic scan processing failed: ${error.message ?: error.javaClass.simpleName}"
            Log.e(TAG, message, error)
            _state.value = ScannerState.Error(message)
        }
    }

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
}

private fun List<ParcelUuid>?.toServiceUuidStrings(): List<String> =
    this?.map { it.uuid.toString() }.orEmpty()
