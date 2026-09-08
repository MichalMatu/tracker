package io.blueeye.core.scanner.manager

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.blueeye.core.data.scanner.ScannerRuntimeDiagnosticsStore
import io.blueeye.core.domain.repository.DeviceRepository
import io.blueeye.core.domain.scanner.ScannerRuntimePolicy
import io.blueeye.core.permission.PermissionManager
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
import java.util.concurrent.TimeUnit
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

internal fun ScannerState.allowsPassiveStart(): Boolean =
    this !is ScannerState.Starting && this !is ScannerState.Scanning

/**
 * Internal scan event for bounded sequential ingest. This replaces fire-and-forget
 * coroutines with a latest-per-device pending buffer.
 */
private sealed interface ScanEvent {
    val queueKey: String
    val receivedAtMonotonicMs: Long
    val observedAtEpochMs: Long

    data class BleResult(
        val result: ScanResult,
        val mac: String,
        override val receivedAtMonotonicMs: Long,
        override val observedAtEpochMs: Long,
    ) : ScanEvent {
        override val queueKey: String = "ble:$mac"
    }

    data class ClassicResult(
        val mac: String,
        val name: String?,
        val rssi: Int,
        val classOfDevice: Int?,
        val serviceUuids: List<String>,
        override val receivedAtMonotonicMs: Long,
        override val observedAtEpochMs: Long,
    ) : ScanEvent {
        override val queueKey: String = "classic:$mac"
    }
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
        private const val SCAN_EVENT_BUFFER_CAPACITY = 4_096
        private const val SCAN_EVENT_REJECTION_LOG_INTERVAL = 100L
    }

    // Use SupervisorJob so child failures don't cancel the parent
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ScannerState>(ScannerState.Idle)
    val state = _state.asStateFlow()

    private var scanJob: Job? = null
    private var processingJob: Job? = null
    private val rejectedScanEvents = AtomicLong(0L)

    private val scanEventBuffer =
        LatestPerKeyScanBuffer<String, ScanEvent>(capacity = SCAN_EVENT_BUFFER_CAPACITY)

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
                while (true) {
                    val bufferedEvent = scanEventBuffer.receive()
                    val event = bufferedEvent.value
                    val processingStartedAt = monotonicNowMs()
                    ScannerRuntimeDiagnosticsStore.recordProcessingStarted(
                        queueDepth = bufferedEvent.queueDepthAfterDequeue,
                        queueWaitMs = processingStartedAt - event.receivedAtMonotonicMs,
                    )
                    try {
                        val succeeded =
                            when (event) {
                                is ScanEvent.BleResult -> handleBleResult(event)
                                is ScanEvent.ClassicResult -> handleClassicResult(event)
                            }
                        val durationMs = monotonicNowMs() - processingStartedAt
                        if (succeeded) {
                            ScannerRuntimeDiagnosticsStore.recordProcessingSucceeded(durationMs)
                        } else {
                            ScannerRuntimeDiagnosticsStore.recordProcessingFailed(durationMs)
                        }
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        ScannerRuntimeDiagnosticsStore.recordProcessingFailed(
                            monotonicNowMs() - processingStartedAt
                        )
                        Log.e(TAG, "Error processing scan event", e)
                    }
                }
            }
    }

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
                // Cancel any pending transition/scan job
                scanJob?.cancel()
                scanJob = scope.launch {
                    performPassiveBleScan()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    internal suspend fun performPassiveBleScan() {
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
                    recordRawBleCallback(result)
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

            if (ScannerRuntimePolicy.allowsClassicDiscovery) {
                startClassicDiscovery()
            } else {
                Log.i(TAG, "Classic discovery suppressed by runtime profile ${ScannerRuntimePolicy.profile}")
            }
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
                            recordRawBleCallback(result)
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
        scanJob = null
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
                        receivedAtMonotonicMs = monotonicNowMs(),
                        observedAtEpochMs = System.currentTimeMillis(),
                    )
                )
            }

        if (!classicStarted) {
            Log.w(TAG, "Classic discovery unavailable; continuing BLE-only passive scan")
        }
    }

    @SuppressLint("MissingPermission")
    private fun recordRawBleCallback(result: ScanResult) {
        val mac = result.device.address
        val receivedAtMonotonicMs = monotonicNowMs()
        val observedAtEpochMs = System.currentTimeMillis()
        ScannerRuntimeDiagnosticsStore.recordRawBleCallback(mac)
        enqueueScanEvent(
            ScanEvent.BleResult(
                result = result,
                mac = mac,
                receivedAtMonotonicMs = receivedAtMonotonicMs,
                observedAtEpochMs = observedAtEpochMs,
            )
        )
    }

    private fun enqueueScanEvent(event: ScanEvent) {
        when (
            val offerResult = scanEventBuffer.offer(
                key = event.queueKey,
                value = event,
            )
        ) {
            is ScanBufferOfferResult.Enqueued ->
                ScannerRuntimeDiagnosticsStore.recordQueueAccepted(
                    queueDepth = offerResult.queueDepth,
                    queueHighWaterMark = offerResult.queueHighWaterMark,
                )
            is ScanBufferOfferResult.Coalesced ->
                ScannerRuntimeDiagnosticsStore.recordCoalescedEvent()
            is ScanBufferOfferResult.Rejected -> {
                val rejected = rejectedScanEvents.incrementAndGet()
                ScannerRuntimeDiagnosticsStore.recordQueueRejected()
                if (rejected == 1L || rejected % SCAN_EVENT_REJECTION_LOG_INTERVAL == 0L) {
                    Log.w(TAG, "Rejected $rejected scan event(s): ingest capacity exhausted")
                }
            }
        }
    }

    /** Handles BLE scan result - called sequentially from event processor. */
    @SuppressLint("MissingPermission")
    private suspend fun handleBleResult(event: ScanEvent.BleResult): Boolean {
        val data = scanResultExtractor.extract(event.result)

        val params = io.blueeye.core.domain.repository.ScanResultParams(
            mac = data.mac,
            rssi = data.rssi,
            timestamp = event.observedAtEpochMs,
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
        val processingResult = repository.handleScanResult(params)
        processingResult.onFailure { error ->
            val message = "Scan processing failed: ${error.message ?: error.javaClass.simpleName}"
            Log.e(TAG, message, error)
            _state.value = ScannerState.Error(message)
        }
        return processingResult.isSuccess
    }

    private suspend fun handleClassicResult(result: ScanEvent.ClassicResult): Boolean {
        val processingResult =
            repository.handleClassicDiscovery(
                mac = result.mac,
                name = result.name,
                rssi = result.rssi,
                classOfDevice = result.classOfDevice,
                serviceUuids = result.serviceUuids,
            )
        processingResult.onFailure { error ->
            val message = "Classic scan processing failed: ${error.message ?: error.javaClass.simpleName}"
            Log.e(TAG, message, error)
            _state.value = ScannerState.Error(message)
        }
        return processingResult.isSuccess
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

private fun monotonicNowMs(): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
