package io.blueeye.core.scanner.throttle

import io.blueeye.core.scanner.manager.ScannerConstants
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

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
    private val lastSampleWrite = ConcurrentHashMap<String, Long>()
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

    fun shouldWriteSample(
        mac: String,
        isPriorityDevice: Boolean = false,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val lastWrite = lastSampleWrite[mac]
        val throttleLimit = if (isPriorityDevice) {
            ScannerConstants.TACTICAL_UPDATE_THROTTLE_MS
        } else {
            ScannerConstants.SIGNAL_SAMPLE_THROTTLE_MS
        }

        if (lastWrite == null || now - lastWrite >= throttleLimit) {
            lastSampleWrite[mac] = now
            return true
        }
        return false
    }

    private fun recordUpdate(mac: String, timestamp: Long, rssi: Int) {
        lastDbUpdate[mac] = timestamp
        lastRssi[mac] = rssi
    }

    fun cleanup(maxAgeMs: Long = CLEANUP_AGE_MS) {
        val now = System.currentTimeMillis()
        lastDbUpdate.entries.removeIf { now - it.value > maxAgeMs }
        lastSampleWrite.entries.removeIf { now - it.value > maxAgeMs }
        lastRssi.keys.retainAll(lastDbUpdate.keys)
    }

    fun getTrackedCount(): Int = lastDbUpdate.size
}
