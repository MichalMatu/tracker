package io.blueeye.core.alert

import android.util.Log
import io.blueeye.core.data.preferences.WatchlistPreferences
import io.blueeye.core.model.TrackingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing Personal Safety alerts (Tracker Detection).
 *
 * Responsibilities:
 * - Monitor devices with high Follow-Me scores.
 * - Trigger review alerts when movement evidence needs user attention.
 * - Manage vibration cooldowns to avoid panic/spam.
 */
@Singleton
open class TrackerAlertService @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val watchlistPreferences: WatchlistPreferences,
    private val vibrationHandler: TacticalVibrationHandler
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lastAlertTime = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // Versioned channel IDs move users off older alert-channel settings.
    private val channelIdHigh = "security_alerts_urgent_v9"
    private val channelIdSilent = "security_alerts_silent_v9"
    private val groupKeyAlerts = "io.blueeye.SECURITY_ALERTS"

    // Preference snapshots read from the alert path.
    @Volatile private var isDetectionEnabled: Boolean = true
    @Volatile private var isVibrationEnabled: Boolean = true
    @Volatile private var isSoundEnabled: Boolean = true
    @Volatile private var isHeadsUpEnabled: Boolean = true

    private val audioMutex = kotlinx.coroutines.sync.Mutex()
    private val notificationMutex = kotlinx.coroutines.sync.Mutex()

    private var currentRingtone: android.media.Ringtone? = null
    private var lastGlobalSoundTime: Long = 0L

    // Warmup period: ignore alerts for 60 seconds after startup.
    private val serviceStartTime: Long = System.currentTimeMillis()
    private val warmupPeriodMs = 60_000L

    init {
        try {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)

            // Remove old channel IDs so changed alert settings take effect.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Log.i(TAG, "Cleaning up legacy alert channels...")
                // Legacy channel from the original application startup path.
                nm.deleteNotificationChannel("alerts_channel")

                // Prior alert channel versions.
                for (i in 1..8) {
                    nm.deleteNotificationChannel("security_alerts_urgent_v$i")
                    nm.deleteNotificationChannel("security_alerts_silent_v$i")
                }
                Log.d(TAG, "Legacy alert channel cleanup complete.")
            }

            // Unified Preference Stream
            scope.launch {
                kotlinx.coroutines.flow.combine(
                    watchlistPreferences.trackerDetectionEnabled,
                    watchlistPreferences.trackerVibrationEnabled,
                    watchlistPreferences.trackerSoundEnabled,
                    watchlistPreferences.trackerHeadsUpEnabled
                ) { detect, vib, sound, hud ->
                    listOf(detect, vib, sound, hud)
                }.collect { prefs ->
                    val (detect, vib, sound, hud) = prefs

                    isDetectionEnabled = detect
                    isVibrationEnabled = vib
                    isSoundEnabled = sound
                    isHeadsUpEnabled = hud

                    Log.d(TAG, "Prefs Updated: Detect=$detect, Vib=$vib, Sound=$sound, HUD=$hud")

                    if (!detect || !sound) {
                        try {
                            audioMutex.lock()
                            try {
                                if (currentRingtone?.isPlaying == true) {
                                    Log.w(TAG, "Sound disabled: stopping active ringtone.")
                                    currentRingtone?.stop()
                                }
                            } finally { audioMutex.unlock() }
                        } catch (e: Exception) { Log.e(TAG, "Mute error", e) }
                    }

                    if (!detect || !hud) {
                        try {
                            Log.w(TAG, "Alerts restricted (HUD=$hud): clearing posted notifications.")
                            nm.cancelAll()
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                // Recreate the high-priority channel only when heads-up alerts are enabled.
                                nm.deleteNotificationChannel(channelIdHigh)
                            }
                        } catch (e: Exception) { Log.e(TAG, "Cleanup error", e) }
                    } else {
                        createNotificationChannels()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed startup in TrackerAlertService", e)
        }
    }

    fun onDeviceAnalyzed(
        mac: String,
        score: Int,
        status: TrackingStatus,
        evidenceReason: String? = null,
        isKnownTracker: Boolean = false,
    ) {
        if (status == TrackingStatus.SAFE && !isKnownTracker) return
        if (!isDetectionEnabled) return

        scope.launch {
            try {
                if (!isDetectionEnabled) return@launch

                // Warmup: Skip all alerts for the first 60 seconds after service start
                val now = System.currentTimeMillis()
                if (now - serviceStartTime < warmupPeriodMs) {
                    val remainingSeconds = (warmupPeriodMs - (now - serviceStartTime)) / 1000
                    Log.d(TAG, "Warmup active: Skipping alert for $mac (${remainingSeconds}s remaining)")
                    return@launch
                }
                val lastAlert = lastAlertTime[mac] ?: 0L
                if (now - lastAlert < ALERT_COOLDOWN_MS) return@launch

                val canVib = isVibrationEnabled
                val canSound = isSoundEnabled

                // If heads-up alerts are disabled, skip notification creation entirely.

                val vibrationLevel =
                    TrackerAlertSignalPolicy.vibrationLevel(
                        status = status,
                        isKnownTracker = isKnownTracker,
                    ) ?: return@launch
                val content =
                    TrackerAlertContentFormatter.format(
                        mac = mac,
                        score = score,
                        status = status,
                        evidenceReason = evidenceReason,
                        isKnownTracker = isKnownTracker,
                    ) ?: return@launch

                if (canVib) vibrationHandler.vibrate(vibrationLevel)
                if (canSound) playAlertSound()
                showNotification(mac, content.title, content.body)
                lastAlertTime[mac] = now
            } catch (e: Exception) {
                Log.e(TAG, "Error in alert processing", e)
            }
        }
    }

    private suspend fun playAlertSound() {
        audioMutex.lock()
        try {
            if (!isDetectionEnabled || !isSoundEnabled) return

            val now = System.currentTimeMillis()
            if (now - lastGlobalSoundTime < 3000) return

            if (currentRingtone?.isPlaying == true) {
                try { currentRingtone?.stop() } catch (ignored: Exception) {}
            }

            val uri = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
                ?: android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
                ?: android.provider.Settings.System.DEFAULT_RINGTONE_URI
                ?: return

            val ringtone = android.media.RingtoneManager.getRingtone(context, uri)
            if (ringtone != null) {
                ringtone.audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                currentRingtone = ringtone
                ringtone.play()
                lastGlobalSoundTime = now
            }
        } finally {
            audioMutex.unlock()
        }
    }

    private suspend fun showNotification(mac: String, title: String, content: String) {
        notificationMutex.lock()
        try {
            // If heads-up alerts are off, do not build tray or banner notifications.
            if (!isDetectionEnabled || !isHeadsUpEnabled) {
                Log.d(TAG, "Notification blocked by master or HUD preference ($isHeadsUpEnabled)")
                return
            }

            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                 flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            } ?: return

            val pendingIntent: android.app.PendingIntent = android.app.PendingIntent.getActivity(
                context, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE
            )

            // Reaching this point means heads-up alerts are enabled for the current channel version.
            val targetChannel = channelIdHigh
            val priority = androidx.core.app.NotificationCompat.PRIORITY_HIGH

            val builder = androidx.core.app.NotificationCompat.Builder(context, targetChannel)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(priority)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setGroup(groupKeyAlerts)
                .setOngoing(false)

            val nm = androidx.core.app.NotificationManagerCompat.from(context)
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                nm.notify(mac.hashCode(), builder.build())

                // Group Summary
                val summary = androidx.core.app.NotificationCompat.Builder(context, targetChannel)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("BlueEye tracking signals")
                    .setGroup(groupKeyAlerts)
                    .setGroupSummary(true)
                    .build()
                nm.notify(groupKeyAlerts.hashCode(), summary)
            }
        } finally {
            notificationMutex.unlock()
        }
    }

    private fun createNotificationChannels() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)

            val channelHigh = android.app.NotificationChannel(
                channelIdHigh,
                "Tracking Signals",
                android.app.NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alerts for devices with high follow-me scores"
                enableVibration(false)
                setShowBadge(true)
            }

            val channelSilent = android.app.NotificationChannel(
                channelIdSilent,
                "Tracking Signal Summary",
                android.app.NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Information about detected devices"
                enableVibration(false)
                setSound(null, null)
            }

            nm.createNotificationChannels(listOf(channelHigh, channelSilent))
        }
    }

    private companion object {
        private const val TAG = "TrackerAlertService"
        private const val ALERT_COOLDOWN_MS = 300_000L // 5 minutes (was 60s)
    }
}
