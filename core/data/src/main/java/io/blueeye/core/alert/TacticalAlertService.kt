package io.blueeye.core.alert

import android.util.Log
import io.blueeye.core.data.classifier.vendor.tactical.ConfidenceLevel
import io.blueeye.core.data.preferences.WatchlistPreferences
import io.blueeye.core.domain.alert.AlertCategory
import io.blueeye.core.domain.alert.AlertDispatcher
import io.blueeye.core.domain.alert.AlertRequest
import io.blueeye.core.domain.alert.AlertVibrationPattern
import io.blueeye.core.model.DetectionEvidence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks professional/public-safety-like signal evidence for UI review and
 * sends alert intents through the unified dispatcher.
 */
@Singleton
class TacticalAlertService @Inject constructor(
    private val watchlistPreferences: WatchlistPreferences,
    private val alertDispatcher: AlertDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Currently active professional/public-safety-like signals. */
    private val activeDevices = mutableMapOf<String, TacticalDetection>()

    /** Flow of active professional/public-safety-like signal count for UI. */
    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount

    /** Flow of all active detections for detailed UI. */
    private val _activeDetections = MutableStateFlow<List<TacticalDetection>>(emptyList())
    val activeDetections: StateFlow<List<TacticalDetection>> = _activeDetections

    /** Called when professional/public-safety-like signal evidence is observed during BLE scan. */
    fun onDeviceDetected(request: TacticalAlertRequest) {
        scope.launch {
            val isEnabled = watchlistPreferences.tacticalDetectionEnabled.first()
            if (!isEnabled) return@launch

            val now = System.currentTimeMillis()
            val isNewDevice = !activeDevices.containsKey(request.macAddress)
            val detectionEvidence = request.evidence ?: TacticalEvidenceFactory.build(
                match = request.match,
                source = request.evidenceSource,
                rawValue = request.rawEvidenceValue,
                timestamp = now,
                provenance = request.evidenceProvenance,
            )

            val detection = TacticalDetection(
                macAddress = request.macAddress,
                vendorName = request.match.vendorName,
                category = request.match.category.name,
                confidence = request.match.confidence.name,
                description = request.match.description,
                evidence = detectionEvidence,
                rssi = request.rssi,
                firstSeenAt = activeDevices[request.macAddress]?.firstSeenAt ?: now,
                lastSeenAt = now,
            )
            activeDevices[request.macAddress] = detection

            cleanupOldDevices()
            updateFlows()

            if (TacticalSignalAlertPolicy.shouldVibrate(isNewDevice, request.evidenceSource)) {
                dispatchPublicSafetySignalAlert(
                    request = request,
                    evidence = detectionEvidence,
                )
            }

            Log.w(
                TAG,
                "Public-safety-like signal evidence: ${request.match.vendorName} | ${request.match.category} | " +
                    "${request.match.confidence} | MAC: ${request.macAddress} | RSSI: ${request.rssi}",
            )
        }
    }

    private suspend fun dispatchPublicSafetySignalAlert(
        request: TacticalAlertRequest,
        evidence: DetectionEvidence,
    ) {
        val confidence = TacticalSignalAlertPolicy.vibrationLevel(request.match.confidence)
        alertDispatcher.dispatch(
            AlertRequest(
                category = AlertCategory.PUBLIC_SAFETY_SIGNAL,
                key = request.macAddress,
                title = "Bluetooth signal evidence",
                body =
                    "${request.match.vendorName} ${request.match.category.name.lowercase()} signal, " +
                        "confidence ${request.match.confidence.name.lowercase()}, RSSI ${request.rssi}. " +
                        evidence.reasonText,
                vibrationPattern = confidence.toAlertVibrationPattern(),
                cooldownMs = SIGNAL_ALERT_COOLDOWN_MS,
            ),
        ).onFailure { error ->
            Log.e(TAG, "Public-safety-like alert dispatch failed", error)
        }
    }

    private fun cleanupOldDevices() {
        val now = System.currentTimeMillis()
        val expired = activeDevices.entries
            .filter { now - it.value.lastSeenAt > DEVICE_TIMEOUT_MS }
            .map { it.key }

        expired.forEach { activeDevices.remove(it) }
    }

    private fun updateFlows() {
        _activeCount.value = activeDevices.size
        _activeDetections.value = activeDevices.values.toList()
            .sortedByDescending { it.lastSeenAt }
    }

    fun clearAll() {
        activeDevices.clear()
        updateFlows()
    }

    suspend fun isEnabled(): Boolean {
        return watchlistPreferences.tacticalDetectionEnabled.first()
    }

    fun onWatchlistDeviceReturned(
        mac: String,
        rssi: Int,
        evidence: DetectionEvidence,
    ) {
        scope.launch {
            val content = WatchlistReturnAlertContentFormatter.format(mac, rssi, evidence)
            alertDispatcher.dispatch(
                AlertRequest(
                    category = AlertCategory.WATCHLIST_RETURN,
                    key = mac,
                    title = content.title,
                    body = content.body,
                    vibrationPattern = AlertVibrationPattern.WATCHLIST_RETURN,
                    cooldownMs = 0L,
                ),
            ).onFailure { error ->
                Log.e(TAG, "Watchlist return alert dispatch failed", error)
            }
            Log.i(TAG, "Watchlist device returned: $mac | RSSI: $rssi | ${evidence.reasonText}")
        }
    }

    private fun ConfidenceLevel.toAlertVibrationPattern(): AlertVibrationPattern =
        when (this) {
            ConfidenceLevel.CRITICAL -> AlertVibrationPattern.CRITICAL
            ConfidenceLevel.HIGH -> AlertVibrationPattern.HIGH
            ConfidenceLevel.MEDIUM -> AlertVibrationPattern.MEDIUM
        }

    private companion object {
        private const val TAG = "TacticalAlertService"
        private const val DEVICE_TIMEOUT_MS = 5 * 60 * 1000L
        private const val SIGNAL_ALERT_COOLDOWN_MS = 30 * 1000L
    }
}
