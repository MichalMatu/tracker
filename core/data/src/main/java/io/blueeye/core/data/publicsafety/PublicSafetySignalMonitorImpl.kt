package io.blueeye.core.data.publicsafety

import io.blueeye.core.alert.TacticalAlertService
import io.blueeye.core.alert.TacticalDetection
import io.blueeye.core.data.preferences.WatchlistPreferences
import io.blueeye.core.domain.publicsafety.PublicSafetySignalMonitor
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.PublicSafetySignal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PublicSafetySignalMonitorImpl
    @Inject
    constructor(
        private val watchlistPreferences: WatchlistPreferences,
        private val tacticalAlertService: TacticalAlertService,
    ) : PublicSafetySignalMonitor {
        override val detectionEnabled: Flow<Boolean> = watchlistPreferences.tacticalDetectionEnabled
        override val activeSignalCount: StateFlow<Int> = tacticalAlertService.activeCount
        override val activeSignals: Flow<List<PublicSafetySignal>> =
            tacticalAlertService.activeDetections.map { detections ->
                detections.map { it.toPublicSafetySignal() }
            }

        override suspend fun setDetectionEnabled(enabled: Boolean): Result<Unit> =
            runCatching {
                watchlistPreferences.setTacticalDetectionEnabled(enabled)
            }

        override fun clearActiveSignals(): Result<Unit> =
            runCatching {
                tacticalAlertService.clearAll()
            }

        private fun TacticalDetection.toPublicSafetySignal(): PublicSafetySignal =
            PublicSafetySignal(
                deviceId = macAddress,
                vendorName = vendorName,
                category = category,
                confidence = confidence.toDetectionConfidence(),
                description = description,
                evidence = listOf(evidence),
                rssi = rssi,
                firstSeenAt = firstSeenAt,
                lastSeenAt = lastSeenAt,
            )

        private fun String.toDetectionConfidence(): DetectionConfidence =
            when (uppercase()) {
                "CRITICAL" -> DetectionConfidence.CRITICAL
                "HIGH" -> DetectionConfidence.HIGH
                "MEDIUM" -> DetectionConfidence.MEDIUM
                else -> DetectionConfidence.LOW
            }
    }
