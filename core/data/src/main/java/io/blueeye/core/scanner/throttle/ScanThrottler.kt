package io.blueeye.core.scanner.throttle

import io.blueeye.core.model.TrackingStatus
import io.blueeye.core.scanner.manager.ScannerConstants
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class SignalSampleThrottleParams(
    val identityKey: String,
    val observedMac: String,
    val currentRssi: Int,
    val trackingStatus: TrackingStatus,
    val isPriorityDevice: Boolean = false,
    val now: Long = System.currentTimeMillis(),
)

private data class SignalSampleState(
    val writtenAt: Long,
    val observedMac: String,
    val rssi: Int,
    val trackingStatus: TrackingStatus,
)

/**
 * Throttles database updates to prevent excessive I/O.
 */
@Singleton
class ScanThrottler @Inject constructor() {
    private companion object {
        const val SIGNIFICANT_RSSI_CHANGE = 10
        const val CLEANUP_AGE_MS = 300_000L
    }

    private val lastDbUpdate = ConcurrentHashMap<String, Long>()
    private val lastSampleState = ConcurrentHashMap<String, SignalSampleState>()
    private val lastRssi = ConcurrentHashMap<String, Int>()

    fun shouldUpdateDevice(params: ThrottleParams): Boolean {
        val lastUpdate = lastDbUpdate[params.mac]
        val previousRssi = lastRssi[params.mac]

        val isCritical = lastUpdate == null || params.hasNewName || params.hasNewType
        val throttleLimit = if (params.isPriorityDevice) {
            ScannerConstants.TACTICAL_UPDATE_THROTTLE_MS
        } else {
            ScannerConstants.DB_UPDATE_THROTTLE_MS
        }
        val throttleOver = params.now - (lastUpdate ?: 0L) >= throttleLimit
        val rssiSignificant = previousRssi?.let {
            kotlin.math.abs(params.currentRssi - it) > SIGNIFICANT_RSSI_CHANGE
        } ?: false

        val shouldUpdate = isCritical || throttleOver || rssiSignificant

        if (shouldUpdate) {
            recordUpdate(params.mac, params.now, params.currentRssi)
        }

        return shouldUpdate
    }

    fun shouldWriteSample(params: SignalSampleThrottleParams): Boolean {
        val previous = lastSampleState[params.identityKey]
        if (previous == null) {
            recordSample(params)
            return true
        }

        val throttleLimit =
            if (params.isPriorityDevice) {
                ScannerConstants.TACTICAL_UPDATE_THROTTLE_MS
            } else {
                ScannerConstants.SIGNAL_SAMPLE_THROTTLE_MS
            }
        val throttleOver = params.now - previous.writtenAt >= throttleLimit
        val meaningfulChange =
            !params.isPriorityDevice &&
                (
                    previous.observedMac != params.observedMac ||
                        previous.trackingStatus != params.trackingStatus ||
                        abs(params.currentRssi - previous.rssi) >= SIGNIFICANT_RSSI_CHANGE
                )
        val shouldWrite = throttleOver || meaningfulChange

        if (shouldWrite) {
            recordSample(params)
        }
        return shouldWrite
    }

    private fun recordSample(params: SignalSampleThrottleParams) {
        lastSampleState[params.identityKey] =
            SignalSampleState(
                writtenAt = params.now,
                observedMac = params.observedMac,
                rssi = params.currentRssi,
                trackingStatus = params.trackingStatus,
            )
    }

    private fun recordUpdate(mac: String, timestamp: Long, rssi: Int) {
        lastDbUpdate[mac] = timestamp
        lastRssi[mac] = rssi
    }

    fun cleanup(maxAgeMs: Long = CLEANUP_AGE_MS) {
        val now = System.currentTimeMillis()
        lastDbUpdate.entries.removeIf { now - it.value > maxAgeMs }
        lastSampleState.entries.removeIf { now - it.value.writtenAt > maxAgeMs }
        lastRssi.keys.retainAll(lastDbUpdate.keys)
    }

    fun getTrackedCount(): Int = lastDbUpdate.size
}
