package io.blueeye.core.scanner.rfcomm

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.util.Log
import io.blueeye.core.domain.repository.DeviceRepository
import io.blueeye.core.domain.repository.RepoProbeParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that periodically probes paired Bluetooth devices via RFCOMM.
 *
 * This service:
 * 1. Monitors bonded (paired) devices with compatible handlers
 * 2. Periodically connects and retrieves detailed info (battery, settings)
 * 3. Updates the device repository with freshly probed data
 *
 * Usage:
 * - Call start() only after the user enables a detailed active scan
 * - Call stop() when that explicit active scan ends
 * - Use probeNow() for immediate on-demand probing
 */
@Singleton
class RfcommProbeService
@Inject
constructor(
    private val adapter: BluetoothAdapter?,
    private val connectionManager: RfcommConnectionManager,
    private val repository: DeviceRepository,
) {
    companion object {
        private const val TAG = "RfcommProbeService"

        /** Default interval between probe cycles (1 minute) */
        private const val DEFAULT_PROBE_INTERVAL_MS = 60 * 1000L

        /** Minimum interval to prevent excessive probing */
        private const val MIN_PROBE_INTERVAL_MS = 8 * 1000L

        /** Timeout for each device probe */
        private const val PROBE_TIMEOUT_MS = 2_000L

        /** Delay between probing distinct devices to respect Bluetooth stack */
        private const val PROBE_CYCLE_DELAY_MS = 500L

        /** RFCOMM probing does not provide a radio scan RSSI sample. */
        private const val UNKNOWN_RSSI = Int.MIN_VALUE

        private const val MS_IN_SEC = 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val probeMutex = Mutex()

    private var probeJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastProbeResults = MutableStateFlow<List<RfcommProbeResult>>(emptyList())
    val lastProbeResults: StateFlow<List<RfcommProbeResult>> = _lastProbeResults.asStateFlow()

    private var probeIntervalMs = DEFAULT_PROBE_INTERVAL_MS

    /**
     * Starts the periodic RFCOMM probing service.
     *
     * @param intervalMs interval between probe cycles; clamped to at least 8 seconds.
     */
    fun start(intervalMs: Long = DEFAULT_PROBE_INTERVAL_MS) {
        if (_isRunning.value) {
            Log.w(TAG, "Already running, ignoring start request")
            return
        }

        probeIntervalMs = maxOf(intervalMs, MIN_PROBE_INTERVAL_MS)

        Log.i(TAG, "Starting RFCOMM probe service (interval: ${probeIntervalMs / MS_IN_SEC}s)")

        probeJob =
            scope.launch {
                _isRunning.value = true

                while (isActive) {
                    try {
                        doProbeCycle()
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        Log.e(TAG, "Error in probe cycle", e)
                    }

                    delay(probeIntervalMs)
                }
            }
    }

    /** Stops the periodic probing service. */
    fun stop() {
        Log.i(TAG, "Stopping RFCOMM probe service")
        probeJob?.cancel()
        probeJob = null
        _isRunning.value = false
    }

    /**
     * Triggers an immediate probe of all supported devices. Works regardless of whether the
     * periodic service is running.
     *
     * @return List of probe results
     */
    suspend fun probeNow(): List<RfcommProbeResult> {
        Log.i(TAG, "Immediate probe requested")
        return doProbeCycle()
    }

    /**
     * Probes a specific device by MAC address. The device must be bonded and have a compatible
     * handler.
     *
     * @param macAddress The MAC address of the device to probe
     * @return Probe result or null if device not found/supported
     */
    @SuppressLint("MissingPermission")
    suspend fun probeDevice(macAddress: String): RfcommProbeResult? {
        val device = adapter?.bondedDevices?.find { it.address == macAddress } ?: return null

        if (!connectionManager.hasHandler(device)) {
            return null
        }

        val result = connectionManager.probeDevice(device, timeoutMs = PROBE_TIMEOUT_MS)
        if (result != null) {
            updateRepository(result)
        }
        return result
    }

    /** Gets list of bonded devices that can be probed via RFCOMM. */
    fun getSupportedDevices(): List<BluetoothDevice> {
        return connectionManager
            .getSupportedBondedDevices()
            .map { pair: Pair<BluetoothDevice, RfcommDeviceHandler> -> pair.first }
    }

    // ==================== PRIVATE ====================

    private suspend fun doProbeCycle(): List<RfcommProbeResult> = probeMutex.withLock {
        val supportedDevices = connectionManager.getSupportedBondedDevices()

        if (supportedDevices.isEmpty()) {
            Log.d(TAG, "No supported bonded devices to probe")
            return emptyList()
        }

        Log.i(TAG, "Probing ${supportedDevices.size} RFCOMM device(s)...")

        val results = mutableListOf<RfcommProbeResult>()

        for ((device, handler) in supportedDevices) {
            Log.d(TAG, "Probing ${device.address} (${handler.handlerType})...")

            // Notify the repository so UI can show the active probe state.
            repository.setActiveProbe(device.address)

            try {
                val params =
                    RepoProbeParams(
                        status = "PROBING",
                        attempts = 0,
                        timestamp = System.currentTimeMillis(),
                        model = null,
                        serial = null,
                        firmware = null,
                        hardware = null,
                        software = null,
                        manufacturer = null,
                        battery = null,
                        services = null,
                        error = null,
                    )
                repository.updateProbeData(device.address, params)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set PROBING status", e)
            }

            val result = try {
                connectionManager.probeDevice(device = device, timeoutMs = PROBE_TIMEOUT_MS)
            } finally {
                repository.setActiveProbe(null)
            }

            if (result != null) {
                results.add(result)
                updateRepository(result)

                if (result.success) {
                    Log.i(
                        TAG,
                        "Probe succeeded for ${device.address}: Battery=${result.batteryLevel}%, " +
                            "Status=${result.deviceStatus}",
                    )
                } else {
                    Log.w(TAG, "Probe failed for ${device.address}: ${result.errorMessage}")
                }
            } else {
                Log.w(TAG, "Probe returned no result for ${device.address}")
                try {
                    val params =
                        RepoProbeParams(
                            status = "SKIPPED",
                            attempts = 1,
                            timestamp = System.currentTimeMillis(),
                            model = null,
                            serial = null,
                            firmware = null,
                            hardware = null,
                            software = null,
                            manufacturer = null,
                            battery = null,
                            services = null,
                            error = "Probe skipped with no result",
                        )
                    repository.updateProbeData(device.address, params)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear PROBING status for empty result", e)
                }
            }

            // Small delay between devices to avoid Bluetooth stack congestion
            delay(PROBE_CYCLE_DELAY_MS)
        }

        _lastProbeResults.value = results
        Log.i(
            TAG,
            "Probe cycle complete: ${results.count { it.success }}/${results.size} successful",
        )

        return results
    }

    /** Updates the device repository with probe results. */
    private suspend fun updateRepository(result: RfcommProbeResult) {
        try {
            // Build status string from device status map
            val statusString =
                if (result.deviceStatus.isNotEmpty()) {
                    result.deviceStatus.entries.joinToString(", ") { entry -> "${entry.key}: ${entry.value}" }
                } else {
                    null
                }

            // Ensure the row exists before updateProbeData runs its SQL UPDATE.
            repository.handleClassicDiscovery(
                mac = result.macAddress,
                name = result.deviceName,
                rssi = UNKNOWN_RSSI,
                classOfDevice = null,
            )

            val params = RepoProbeParams(
                status = if (result.success) "RFCOMM_OK" else "RFCOMM_FAIL",
                attempts = 1,
                timestamp = result.timestamp,
                model = result.deviceName,
                serial = result.serialNumber,
                firmware = result.firmwareVersion,
                hardware = null,
                software = null,
                manufacturer = result.handlerType,
                battery = result.batteryLevel,
                services = statusString,
                error = result.errorMessage,
            )
            repository.updateProbeData(result.macAddress, params)

            Log.d(TAG, "Repository updated for ${result.macAddress}")
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Failed to update repository for ${result.macAddress}", e)
        }
    }

}
