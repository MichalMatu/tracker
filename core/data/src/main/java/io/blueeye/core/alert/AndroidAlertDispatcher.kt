package io.blueeye.core.alert

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.blueeye.core.data.classifier.vendor.tactical.ConfidenceLevel
import io.blueeye.core.domain.alert.AlertCategory
import io.blueeye.core.domain.alert.AlertChannelDiagnostics
import io.blueeye.core.domain.alert.AlertDeliveryDiagnostics
import io.blueeye.core.domain.alert.AlertDeliveryResult
import io.blueeye.core.domain.alert.AlertDeliveryStatus
import io.blueeye.core.domain.alert.AlertDispatcher
import io.blueeye.core.domain.alert.AlertRequest
import io.blueeye.core.domain.alert.AlertVibrationPattern
import io.blueeye.core.domain.repository.SettingsPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAlertDispatcher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settingsPreferencesRepository: SettingsPreferencesRepository,
        private val vibrationHandler: TacticalVibrationHandler,
    ) : AlertDispatcher {
        private val notificationManager: NotificationManager =
            context.getSystemService(NotificationManager::class.java)
        private val notificationManagerCompat = NotificationManagerCompat.from(context)
        private val lastDeliveryByKey = ConcurrentHashMap<String, Long>()

        private val _diagnostics = MutableStateFlow(buildDiagnostics(AlertDeliveryResult()))
        override val diagnostics: StateFlow<AlertDeliveryDiagnostics> = _diagnostics.asStateFlow()

        override suspend fun dispatch(request: AlertRequest): Result<AlertDeliveryResult> =
            runCatching {
                ensureChannels()
                val policy = settingsPreferencesRepository.trackerAlerts.first()
                val timestamp = System.currentTimeMillis()
                val targetChannelId = if (policy.headsUpEnabled) HEADS_UP_CHANNEL_ID else TRAY_CHANNEL_ID
                val cooldownKey = "${request.category}:${request.key}"
                val lastDelivery = lastDeliveryByKey[cooldownKey] ?: 0L

                val result =
                    when {
                        !policy.detectionEnabled ->
                            request.blocked(
                                status = AlertDeliveryStatus.BLOCKED_BY_POLICY,
                                message = "Alert blocked: alert delivery is disabled in Settings.",
                                timestamp = timestamp,
                            )
                        request.cooldownMs > 0L && timestamp - lastDelivery < request.cooldownMs ->
                            request.blocked(
                                status = AlertDeliveryStatus.COOLED_DOWN,
                                message = "Alert cooled down to avoid repeated notifications.",
                                timestamp = timestamp,
                            )
                        !canPostNotifications() ->
                            request.blocked(
                                status = AlertDeliveryStatus.BLOCKED_BY_PERMISSION,
                                message = "Alert blocked: POST_NOTIFICATIONS is not granted.",
                                timestamp = timestamp,
                            )
                        isChannelBlocked(targetChannelId) ->
                            request.blocked(
                                status = AlertDeliveryStatus.BLOCKED_BY_CHANNEL,
                                message = "Alert blocked: Android notification channel is disabled.",
                                timestamp = timestamp,
                            )
                        else ->
                            postAlert(
                                request = request,
                                channelId = targetChannelId,
                                headsUpEnabled = policy.headsUpEnabled,
                                soundEnabled = policy.soundEnabled,
                                vibrationEnabled = policy.vibrationEnabled,
                                timestamp = timestamp,
                            )
                    }

                if (result.status == AlertDeliveryStatus.POSTED) {
                    lastDeliveryByKey[cooldownKey] = timestamp
                }
                _diagnostics.value = buildDiagnostics(result)
                result
            }.onFailure { error ->
                val result =
                    AlertDeliveryResult(
                        category = request.category,
                        key = request.key,
                        status = AlertDeliveryStatus.FAILED,
                        message = "Alert dispatch failed: ${error.message ?: error.javaClass.simpleName}",
                        timestamp = System.currentTimeMillis(),
                    )
                _diagnostics.value = buildDiagnostics(result)
            }

        override fun refreshDiagnostics(): Result<Unit> =
            runCatching {
                ensureChannels()
                _diagnostics.value = buildDiagnostics(_diagnostics.value.lastResult)
            }

        private fun postAlert(
            request: AlertRequest,
            channelId: String,
            headsUpEnabled: Boolean,
            soundEnabled: Boolean,
            vibrationEnabled: Boolean,
            timestamp: Long,
        ): AlertDeliveryResult {
            val pendingIntent =
                context.packageManager.getLaunchIntentForPackage(context.packageName)
                    ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
                    ?.let { intent ->
                        PendingIntent.getActivity(
                            context,
                            request.key.hashCode(),
                            intent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                    }

            val notification =
                NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(request.title)
                    .setContentText(request.body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(request.body))
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setPriority(
                        if (headsUpEnabled) {
                            NotificationCompat.PRIORITY_HIGH
                        } else {
                            NotificationCompat.PRIORITY_DEFAULT
                        },
                    )
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

            @Suppress("MissingPermission")
            notificationManagerCompat.notify(request.notificationTag(), request.key.hashCode(), notification)

            val vibrationTriggered =
                if (vibrationEnabled && request.vibrationPattern != AlertVibrationPattern.NONE) {
                    triggerVibration(request.vibrationPattern)
                    true
                } else {
                    false
                }
            val soundPlayed = if (soundEnabled) playAlertSound() else false

            return AlertDeliveryResult(
                category = request.category,
                key = request.key,
                status = AlertDeliveryStatus.POSTED,
                postedNotification = true,
                soundPlayed = soundPlayed,
                vibrationTriggered = vibrationTriggered,
                message = "Alert posted by unified dispatcher.",
                timestamp = timestamp,
            )
        }

        private fun triggerVibration(pattern: AlertVibrationPattern) {
            when (pattern) {
                AlertVibrationPattern.NONE -> Unit
                AlertVibrationPattern.MEDIUM -> vibrationHandler.vibrate(ConfidenceLevel.MEDIUM)
                AlertVibrationPattern.HIGH -> vibrationHandler.vibrate(ConfidenceLevel.HIGH)
                AlertVibrationPattern.CRITICAL -> vibrationHandler.vibrate(ConfidenceLevel.CRITICAL)
                AlertVibrationPattern.WATCHLIST_RETURN -> vibrationHandler.vibrateForFavorite()
            }
        }

        private fun playAlertSound(): Boolean {
            val uri = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
                ?: android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
                ?: android.provider.Settings.System.DEFAULT_RINGTONE_URI
                ?: return false
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return false
            ringtone.audioAttributes =
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            ringtone.play()
            return true
        }

        private fun ensureChannels() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

            val headsUp =
                NotificationChannel(
                    HEADS_UP_CHANNEL_ID,
                    "BlueEye urgent alerts",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Heads-up alerts for watchlist, Follow-Me, public-safety-like signals, and tests"
                    enableVibration(false)
                    setSound(null, null)
                    setShowBadge(true)
                }
            val tray =
                NotificationChannel(
                    TRAY_CHANNEL_ID,
                    "BlueEye alert tray",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Tray notifications when heads-up is disabled"
                    enableVibration(false)
                    setSound(null, null)
                    setShowBadge(true)
                }
            notificationManager.createNotificationChannels(listOf(headsUp, tray))
        }

        private fun buildDiagnostics(lastResult: AlertDeliveryResult): AlertDeliveryDiagnostics =
            AlertDeliveryDiagnostics(
                postNotificationsGranted = canPostNotifications(),
                notificationsEnabled = notificationManagerCompat.areNotificationsEnabled(),
                headsUpChannel = channelDiagnostics(HEADS_UP_CHANNEL_ID),
                trayChannel = channelDiagnostics(TRAY_CHANNEL_ID),
                lastResult = lastResult,
            )

        private fun channelDiagnostics(channelId: String): AlertChannelDiagnostics {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return AlertChannelDiagnostics(
                    channelId = channelId,
                    importance = null,
                    blocked = false,
                    soundEnabled = null,
                    vibrationEnabled = null,
                )
            }
            val channel = notificationManager.getNotificationChannel(channelId)
            return AlertChannelDiagnostics(
                channelId = channelId,
                importance = channel?.importance,
                blocked = channel?.importance == NotificationManager.IMPORTANCE_NONE,
                soundEnabled = channel?.sound != null,
                vibrationEnabled = channel?.shouldVibrate(),
            )
        }

        private fun canPostNotifications(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        private fun isChannelBlocked(channelId: String): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                notificationManager.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE

        private fun AlertRequest.blocked(
            status: AlertDeliveryStatus,
            message: String,
            timestamp: Long,
        ): AlertDeliveryResult =
            AlertDeliveryResult(
                category = category,
                key = key,
                status = status,
                postedNotification = false,
                soundPlayed = false,
                vibrationTriggered = false,
                message = message,
                timestamp = timestamp,
            )

        private fun AlertRequest.notificationTag(): String =
            when (category) {
                AlertCategory.WATCHLIST_RETURN -> "watchlist-return"
                AlertCategory.FOLLOW_ME -> "follow-me"
                AlertCategory.PUBLIC_SAFETY_SIGNAL -> "public-safety-signal"
                AlertCategory.TEST -> "test-alert"
            }

        private companion object {
            private const val HEADS_UP_CHANNEL_ID = "field_mvp_alerts_heads_up_v1"
            private const val TRAY_CHANNEL_ID = "field_mvp_alerts_tray_v1"
        }
    }
