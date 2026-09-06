package io.blueeye.core.alert

import android.util.Log
import io.blueeye.core.data.classifier.vendor.tactical.ConfidenceLevel
import io.blueeye.core.domain.alert.AlertCategory
import io.blueeye.core.domain.alert.AlertDispatcher
import io.blueeye.core.domain.alert.AlertRequest
import io.blueeye.core.domain.alert.AlertVibrationPattern
import io.blueeye.core.model.TrackingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for Follow-Me alert intent generation.
 * Delivery policy, notification permission checks, sound, vibration, heads-up,
 * and cooldown are handled only by AlertDispatcher.
 */
@Singleton
open class TrackerAlertService @Inject constructor(
    private val alertDispatcher: AlertDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Warmup period: ignore alerts for 60 seconds after startup.
    private val serviceStartTime: Long = System.currentTimeMillis()
    private val warmupPeriodMs = 60_000L

    fun onDeviceAnalyzed(
        mac: String,
        score: Int,
        status: TrackingStatus,
        evidenceReason: String? = null,
        isKnownTracker: Boolean = false,
    ) {
        if (status == TrackingStatus.SAFE && !isKnownTracker) return

        scope.launch {
            try {
                val now = System.currentTimeMillis()
                if (now - serviceStartTime < warmupPeriodMs) {
                    val remainingSeconds = (warmupPeriodMs - (now - serviceStartTime)) / 1000
                    Log.d(TAG, "Warmup active: skipping Follow-Me alert for $mac (${remainingSeconds}s remaining)")
                    return@launch
                }

                val vibrationPattern =
                    TrackerAlertSignalPolicy.vibrationLevel(
                        status = status,
                        isKnownTracker = isKnownTracker,
                    )?.toAlertVibrationPattern() ?: return@launch
                val content =
                    TrackerAlertContentFormatter.format(
                        mac = mac,
                        score = score,
                        status = status,
                        evidenceReason = evidenceReason,
                        isKnownTracker = isKnownTracker,
                    ) ?: return@launch

                alertDispatcher.dispatch(
                    AlertRequest(
                        category = AlertCategory.FOLLOW_ME,
                        key = mac,
                        title = content.title,
                        body = content.body,
                        vibrationPattern = vibrationPattern,
                        cooldownMs = ALERT_COOLDOWN_MS,
                    ),
                ).onFailure { error ->
                    Log.e(TAG, "Follow-Me alert dispatch failed", error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Follow-Me alert processing", e)
            }
        }
    }

    private fun ConfidenceLevel.toAlertVibrationPattern(): AlertVibrationPattern =
        when (this) {
            ConfidenceLevel.CRITICAL -> AlertVibrationPattern.CRITICAL
            ConfidenceLevel.HIGH -> AlertVibrationPattern.HIGH
            ConfidenceLevel.MEDIUM -> AlertVibrationPattern.MEDIUM
        }

    private companion object {
        private const val TAG = "TrackerAlertService"
        private const val ALERT_COOLDOWN_MS = 300_000L
    }
}
