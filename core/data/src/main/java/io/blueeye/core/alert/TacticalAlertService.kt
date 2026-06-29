package io.blueeye.core.alert

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.blueeye.core.data.classifier.vendor.tactical.ConfidenceLevel
import io.blueeye.core.data.preferences.WatchlistPreferences
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
 * Service for managing professional/public-safety signal detection alerts.
 *
 * Responsibilities:
 * - Track currently detected professional/public-safety signals
 * - Trigger bounded review vibrations when a new signal is detected
 * - Respect user preferences (detection enabled, vibration enabled)
 * - Provide active signal evidence counts for UI review
 */
@Singleton
class TacticalAlertService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val watchlistPreferences: WatchlistPreferences,
    private val vibrationHandler: TacticalVibrationHandler
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Currently active professional/public-safety signals.
     * Key: MAC address, Value: Detection info
     */
    private val activeDevices = mutableMapOf<String, TacticalDetection>()

    /**
     * Last vibration time per device to prevent spam.
     */
    private val lastVibrationTime = mutableMapOf<String, Long>()

    /**
     * Flow of active professional/public-safety signal count for UI.
     */
    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount

    /**
     * Flow of all active detections for detailed UI.
     */
    private val _activeDetections = MutableStateFlow<List<TacticalDetection>>(emptyList())
    val activeDetections: StateFlow<List<TacticalDetection>> = _activeDetections

    /**
     * Called when professional/public-safety signal evidence is observed during BLE scan.
     * This is the main entry point from BleScanHandler.
     */
    fun onDeviceDetected(request: TacticalAlertRequest) {
        scope.launch {
            // Check if professional/public-safety signal review is enabled.
            val isEnabled = watchlistPreferences.tacticalDetectionEnabled.first()
            if (!isEnabled) {
                return@launch
            }

            val now = System.currentTimeMillis()
            val isNewDevice = !activeDevices.containsKey(request.macAddress)
            val detectionEvidence = request.evidence ?: TacticalEvidenceFactory.build(
                match = request.match,
                source = request.evidenceSource,
                rawValue = request.rawEvidenceValue,
                timestamp = now,
                provenance = request.evidenceProvenance,
            )

            // Update or add device
            val detection = TacticalDetection(
                macAddress = request.macAddress,
                vendorName = request.match.vendorName,
                category = request.match.category.name,
                confidence = request.match.confidence.name,
                description = request.match.description,
                evidence = detectionEvidence,
                rssi = request.rssi,
                firstSeenAt = activeDevices[request.macAddress]?.firstSeenAt ?: now,
                lastSeenAt = now
            )
            activeDevices[request.macAddress] = detection

            // Update counts
            cleanupOldDevices()
            updateFlows()

            // Vibrate only for newly observed signals; repeat sightings remain UI evidence.
            if (TacticalSignalAlertPolicy.shouldVibrate(isNewDevice, request.evidenceSource)) {
                maybeVibrate(request.macAddress, request.match.confidence)
            }

            Log.w(
                TAG,
                "Public-safety signal evidence: ${request.match.vendorName} | ${request.match.category} | " +
                    "${request.match.confidence} | MAC: ${request.macAddress} | RSSI: ${request.rssi}"
            )
        }
    }

    /**
     * Trigger vibration if cooldown has passed and vibration is enabled.
     */
    private suspend fun maybeVibrate(macAddress: String, confidence: ConfidenceLevel) {
        val vibrationEnabled = watchlistPreferences.tacticalVibrationEnabled.first()
        if (!vibrationEnabled) return

        val now = System.currentTimeMillis()
        val lastVibration = lastVibrationTime[macAddress] ?: 0L

        if (now - lastVibration < VIBRATION_COOLDOWN_MS) return

        val vibrationLevel = TacticalSignalAlertPolicy.vibrationLevel(confidence)
        vibrationHandler.vibrate(vibrationLevel)
        lastVibrationTime[macAddress] = now
    }

    /**
     * Remove devices not seen for DEVICE_TIMEOUT_MS.
     */
    private fun cleanupOldDevices() {
        val now = System.currentTimeMillis()
        val expired = activeDevices.entries
            .filter { now - it.value.lastSeenAt > DEVICE_TIMEOUT_MS }
            .map { it.key }

        expired.forEach {
            activeDevices.remove(it)
            lastVibrationTime.remove(it)
        }
    }

    /**
     * Update StateFlows for UI.
     */
    private fun updateFlows() {
        _activeCount.value = activeDevices.size
        _activeDetections.value = activeDevices.values.toList()
            .sortedByDescending { it.lastSeenAt }
    }

    /**
     * Manually clear all detections (e.g., user reset).
     */
    fun clearAll() {
        activeDevices.clear()
        lastVibrationTime.clear()
        updateFlows()
    }

    /**
     * Check if detection is currently enabled.
     */
    suspend fun isEnabled(): Boolean {
        return watchlistPreferences.tacticalDetectionEnabled.first()
    }

    fun onWatchlistDeviceReturned(
        mac: String,
        rssi: Int,
        evidence: DetectionEvidence,
    ) {
        scope.launch {
            val vibrationEnabled = watchlistPreferences.favoriteVibrationEnabled.first()
            if (vibrationEnabled) {
                vibrationHandler.vibrateForFavorite()
            }

            showWatchlistReturnNotification(
                mac = mac,
                rssi = rssi,
                evidence = evidence,
            )
            Log.i(TAG, "Watchlist device returned: $mac | RSSI: $rssi | ${evidence.reasonText}")
        }
    }

    private fun showWatchlistReturnNotification(
        mac: String,
        rssi: Int,
        evidence: DetectionEvidence,
    ) {
        if (!canPostNotifications()) {
            Log.d(TAG, "Watchlist notification blocked: POST_NOTIFICATIONS not granted")
            return
        }

        createWatchlistReturnChannel()
        val content = WatchlistReturnAlertContentFormatter.format(mac, rssi, evidence)
        val notificationIntent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            } ?: return
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                WATCHLIST_RETURN_REQUEST_CODE,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat.Builder(context, WATCHLIST_RETURN_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(content.title)
                .setContentText(content.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        NotificationManagerCompat.from(context)
            .notify(WATCHLIST_RETURN_NOTIFICATION_TAG, mac.hashCode(), notification)
    }

    private fun createWatchlistReturnChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel =
            NotificationChannel(
                WATCHLIST_RETURN_CHANNEL_ID,
                WATCHLIST_RETURN_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alerts when a user watchlist device returns to Bluetooth range"
                enableVibration(false)
                setShowBadge(true)
            }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        private const val TAG = "TacticalAlertService"
        private const val WATCHLIST_RETURN_CHANNEL_ID = "watchlist_returns_v1"
        private const val WATCHLIST_RETURN_CHANNEL_NAME = "Watchlist Returns"
        private const val WATCHLIST_RETURN_NOTIFICATION_TAG = "watchlist-return"
        private const val WATCHLIST_RETURN_REQUEST_CODE = 6104

        // Time after which a device is considered "gone" (5 minutes)
        private const val DEVICE_TIMEOUT_MS = 5 * 60 * 1000L

        // Cooldown between vibrations for the same device (30 seconds)
        private const val VIBRATION_COOLDOWN_MS = 30 * 1000L
    }
}
