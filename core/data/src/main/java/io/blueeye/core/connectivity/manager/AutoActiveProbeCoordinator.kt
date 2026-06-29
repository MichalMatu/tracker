package io.blueeye.core.connectivity.manager

import android.util.Log
import dagger.Lazy
import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.preferences.WatchlistPreferences
import io.blueeye.core.data.repository.ProbeStateManager
import io.blueeye.core.model.DeviceConnectionState
import io.blueeye.core.model.DeviceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

data class AutoActiveProbeScanCandidate(
    val fingerprint: String,
    val mac: String,
    val isConnectable: Boolean,
    val connectionStatus: String?,
    val lastProbeTimestamp: Long,
    val now: Long,
)

@Singleton
@Suppress("TooManyFunctions")
class AutoActiveProbeCoordinator @Inject constructor(
    private val bleConnectionManager: Lazy<BleConnectionManager>,
    private val deviceDao: DeviceDao,
    private val probeStateManager: ProbeStateManager,
    watchlistPreferences: WatchlistPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val enabled = AtomicBoolean(false)
    private val observerStarted = AtomicBoolean(false)
    private val lastQueuedAtByFingerprint = ConcurrentHashMap<String, Long>()
    private val queue = ArrayDeque<AutoActiveProbeRequest>()
    private val queuedFingerprints = mutableSetOf<String>()
    private val lock = Any()

    @Volatile
    private var activeRequest: AutoActiveProbeRequest? = null

    @Volatile
    private var activeStartedAt: Long = 0L

    @Volatile
    private var activeProgressSeen: Boolean = false

    private var timeoutJob: Job? = null

    init {
        scope.launch {
            watchlistPreferences.autoActiveProbeEnabled.collect { isEnabled ->
                enabled.set(isEnabled)
                Log.i(TAG, "Auto active GATT collection ${if (isEnabled) "enabled" else "disabled"}")
                if (!isEnabled) {
                    clearQueue(disconnectActive = true)
                }
            }
        }
    }

    fun enqueueCandidate(candidate: AutoActiveProbeScanCandidate) {
        val decision =
            AutoActiveProbePolicy.evaluate(
                AutoActiveProbeCandidate(
                    enabled = enabled.get(),
                    isConnectable = candidate.isConnectable,
                    mac = candidate.mac,
                    connectionStatus = candidate.connectionStatus,
                    lastProbeTimestamp = candidate.lastProbeTimestamp,
                    lastQueuedAt = lastQueuedAtByFingerprint[candidate.fingerprint],
                    now = candidate.now,
                )
            )
        if (decision != AutoActiveProbeDecision.Queue) return

        val request = AutoActiveProbeRequest(fingerprint = candidate.fingerprint, mac = candidate.mac)
        val added =
            synchronized(lock) {
                when {
                    activeRequest?.fingerprint == candidate.fingerprint -> false
                    queuedFingerprints.contains(candidate.fingerprint) -> false
                    else -> {
                        queue.add(request)
                        queuedFingerprints.add(candidate.fingerprint)
                        lastQueuedAtByFingerprint[candidate.fingerprint] = candidate.now
                        true
                    }
                }
            }

        if (added) {
            Log.i(TAG, "Queued auto active GATT probe for ${candidate.mac}")
            ensureConnectionObserverStarted()
            drainQueue()
        }
    }

    fun reset() {
        clearQueue(disconnectActive = true)
    }

    private fun ensureConnectionObserverStarted() {
        if (!observerStarted.compareAndSet(false, true)) return

        scope.launch {
            bleConnectionManager.get().connectionState.collect { state ->
                onDeviceConnectionState(state)
            }
        }
    }

    private fun drainQueue() {
        scope.launch {
            startNextIfIdle()
        }
    }

    private suspend fun startNextIfIdle() {
        val request =
            synchronized(lock) {
                if (activeRequest != null || queue.isEmpty()) {
                    null
                } else {
                    val next = queue.removeFirst()
                    queuedFingerprints.remove(next.fingerprint)
                    activeRequest = next
                    activeStartedAt = System.currentTimeMillis()
                    activeProgressSeen = false
                    next
                }
            } ?: return

        markProbeStarted(request.fingerprint)
        probeStateManager.setActiveProbe(request.fingerprint)
        Log.i(TAG, "Starting auto active GATT probe for ${request.mac}")
        bleConnectionManager.get().connect(
            macAddress = request.mac,
            recordFingerprint = request.fingerprint,
        )
        scheduleTimeout(request)
    }

    private fun scheduleTimeout(request: AutoActiveProbeRequest) {
        timeoutJob?.cancel()
        timeoutJob =
            scope.launch {
                delay(AutoActiveProbePolicy.PROBE_TIMEOUT_MS)
                onProbeTimeout(request)
            }
    }

    private suspend fun onDeviceConnectionState(state: DeviceConnectionState) {
        val request = activeRequest ?: return
        when (state) {
            is DeviceConnectionState.Connected -> {
                if (state.macAddress == request.mac) {
                    activeProgressSeen = true
                }
            }
            is DeviceConnectionState.Error -> {
                finishActiveProbe(
                    request = request,
                    markFailed = !activeProgressSeen,
                    error = state.message,
                    cancelTimeout = true,
                )
            }
            DeviceConnectionState.Disconnected -> {
                val elapsed = System.currentTimeMillis() - activeStartedAt
                if (activeProgressSeen || elapsed >= STARTUP_GRACE_MS) {
                    finishActiveProbe(
                        request = request,
                        markFailed = false,
                        error = null,
                        cancelTimeout = true,
                    )
                }
            }
            DeviceConnectionState.Connecting -> Unit
        }
    }

    private suspend fun onProbeTimeout(request: AutoActiveProbeRequest) {
        val hadProgress = activeProgressSeen
        Log.i(TAG, "Auto active GATT probe timeout for ${request.mac}")
        disconnectIfCurrent(request)
        finishActiveProbe(
            request = request,
            markFailed = !hadProgress,
            error = "Auto active probe timeout",
            cancelTimeout = false,
        )
    }

    private suspend fun finishActiveProbe(
        request: AutoActiveProbeRequest,
        markFailed: Boolean,
        error: String?,
        cancelTimeout: Boolean,
    ) {
        val shouldFinish =
            synchronized(lock) {
                if (activeRequest != request) {
                    false
                } else {
                    activeRequest = null
                    activeStartedAt = 0L
                    activeProgressSeen = false
                    true
                }
            }
        if (!shouldFinish) return

        if (cancelTimeout) {
            timeoutJob?.cancel()
        }
        timeoutJob = null
        probeStateManager.setActiveProbe(null)
        if (markFailed) {
            markProbeFailed(request.fingerprint, error)
        }
        drainQueue()
    }

    private fun clearQueue(disconnectActive: Boolean) {
        val request =
            synchronized(lock) {
                queue.clear()
                queuedFingerprints.clear()
                val active = activeRequest
                activeRequest = null
                activeStartedAt = 0L
                activeProgressSeen = false
                active
            }

        timeoutJob?.cancel()
        timeoutJob = null
        probeStateManager.setActiveProbe(null)
        if (disconnectActive && request != null) {
            disconnectIfCurrent(request)
        }
    }

    private fun disconnectIfCurrent(request: AutoActiveProbeRequest) {
        val manager = bleConnectionManager.get()
        if (manager.currentDeviceAddress.value == request.mac) {
            manager.disconnect()
        }
    }

    private suspend fun markProbeStarted(mac: String) {
        updateProbeStatus(
            mac = mac,
            status = STATUS_PROBING,
            error = null,
        )
    }

    private suspend fun markProbeFailed(
        mac: String,
        error: String?,
    ) {
        updateProbeStatus(
            mac = mac,
            status = STATUS_FAILED,
            error = error,
        )
    }

    private suspend fun updateProbeStatus(
        mac: String,
        status: String,
        error: String?,
    ) {
        runCatching {
            deviceDao.updateProbeData(
                fingerprint = mac,
                status = status,
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
                charData = null,
                error = error,
                newDeviceType = DeviceType.UNKNOWN,
            )
        }.onFailure { failure ->
            Log.w(TAG, "Failed to mark $mac probe status $status: ${failure.message}")
        }
    }

    private companion object {
        private const val TAG = "AutoActiveProbe"
        private const val STARTUP_GRACE_MS = 1_000L
        private const val STATUS_PROBING = "PROBING"
        private const val STATUS_FAILED = "FAILED"
    }
}

private data class AutoActiveProbeRequest(
    val fingerprint: String,
    val mac: String,
)
