package io.blueeye.feature.settings

import io.blueeye.core.domain.evidence.DetectionEvidenceClassifier
import io.blueeye.core.domain.repository.DeviceRepository
import io.blueeye.core.domain.repository.SettingsPreferencesRepository
import io.blueeye.core.domain.repository.SignalSampleRepository
import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.Device
import io.blueeye.core.model.SignalSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionStatsProvider
    @Inject
    constructor(
        private val deviceRepository: DeviceRepository,
        private val signalSampleRepository: SignalSampleRepository,
        private val settingsPreferencesRepository: SettingsPreferencesRepository,
    ) {
        val stats: Flow<SessionStats> =
            combine(
                settingsPreferencesRepository.session,
                deviceRepository.getAllDevices(),
                signalSampleRepository.getAllSignalSamplesFlow(),
                deviceRepository.getRecentAlertEvidenceEvents(),
            ) { session, devicesResult, samplesResult, alertEventsResult ->
                SessionStatsCalculator.calculate(
                    startedAt = session.startedAt,
                    devices = devicesResult.getOrDefault(emptyList()),
                    samples = samplesResult.getOrDefault(emptyList()),
                    alertEvidenceEvents = alertEventsResult.getOrDefault(emptyList()),
                )
            }
    }

data class SessionStats(
    val hasStarted: Boolean = false,
    val deviceCount: Int = 0,
    val sampleCount: Int = 0,
    val gpsSampleCount: Int = 0,
    val evidenceCount: Int = 0,
    val attentionEvidenceCount: Int = 0,
    val durationMs: Long = 0L,
    val reviewCategoryCounts: SessionReviewCategoryCounts = SessionReviewCategoryCounts(),
    val rssiQuality: RssiQualityStats = RssiQualityStats(),
    val rssiTrendSummary: SessionRssiTrendSummary = SessionRssiTrendSummary(),
    val alertHistorySummary: SessionAlertHistorySummary = SessionAlertHistorySummary(),
    val identityCarryoverSummary: SessionIdentityCarryoverSummary = SessionIdentityCarryoverSummary(),
    val activeProbeSummary: SessionActiveProbeSummary = SessionActiveProbeSummary(),
    val reviewDeviceQueue: List<SessionReviewDeviceQueueItem> = emptyList(),
)

data class SessionReviewCategoryCounts(
    val watchlist: Int = 0,
    val suspicious: Int = 0,
    val publicSafety: Int = 0,
    val nearby: Int = 0,
    val unknownNoise: Int = 0,
)

data class SessionAlertHistorySummary(
    val eventCount: Int = 0,
    val watchlistReturnCount: Int = 0,
    val publicSafetySignalCount: Int = 0,
    val followMeAlertCount: Int = 0,
)

data class SessionIdentityCarryoverSummary(
    val deviceCount: Int = 0,
)

internal object SessionStatsCalculator {
    fun calculate(
        startedAt: Long,
        devices: List<Device>,
        samples: List<SignalSample>,
        alertEvidenceEvents: List<AlertEvidenceEvent>,
    ): SessionStats {
        if (startedAt <= 0L) return SessionStats()

        val sessionDevices = devices.filter { it.lastSeenAt >= startedAt }
        val sessionSamples = samples.filter { it.timestamp >= startedAt }
        val sessionAlertEvidenceEvents = alertEvidenceEvents.filter { event -> event.timestamp >= startedAt }
        val actionableReviewDevices = sessionDevices.filterNot { device -> device.isSuppressedSessionReviewNoise() }
        val sessionDevicesByFingerprint = sessionDevices.associateBy { device -> device.fingerprint }
        val actionableReviewSamples =
            sessionSamples.filter { sample ->
                sessionDevicesByFingerprint[sample.deviceFingerprint]?.isSuppressedSessionReviewNoise() != true
            }
        val actionableReviewAlertEvidenceEvents =
            sessionAlertEvidenceEvents.filter { event ->
                sessionDevicesByFingerprint[event.deviceFingerprint]?.isSuppressedSessionReviewNoise() != true
            }
        val activeProbeSummary = SessionActiveProbeSummaryCalculator.calculate(sessionDevices)
        return SessionStats(
            hasStarted = true,
            deviceCount = sessionDevices.size,
            sampleCount = sessionSamples.size,
            gpsSampleCount = sessionSamples.count { it.latitude != null && it.longitude != null },
            evidenceCount = sessionDevices.sumOf { it.evidence.size },
            attentionEvidenceCount = sessionDevices.sumOf { device -> device.evidence.countAttentionEvidence() },
            durationMs =
                SessionDurationCalculator.observedDurationMs(
                    startedAt = startedAt,
                    devices = sessionDevices,
                    samples = sessionSamples,
                    alertEvidenceEvents = sessionAlertEvidenceEvents,
                ),
            reviewCategoryCounts = actionableReviewDevices.reviewCategoryCounts(),
            rssiQuality = RssiQualityAnalyzer.analyze(sessionSamples),
            rssiTrendSummary = SessionRssiTrendSummaryAnalyzer.analyze(actionableReviewSamples),
            alertHistorySummary = actionableReviewAlertEvidenceEvents.alertHistorySummary(),
            identityCarryoverSummary = actionableReviewDevices.identityCarryoverSummary(),
            activeProbeSummary = activeProbeSummary,
            reviewDeviceQueue =
                SessionReviewDeviceQueueBuilder.build(
                    devices = actionableReviewDevices,
                    samples = actionableReviewSamples,
                    alertEvidenceEvents = actionableReviewAlertEvidenceEvents,
                ),
        )
    }

    private fun List<Device>.reviewCategoryCounts(): SessionReviewCategoryCounts =
        SessionReviewCategoryCounts(
            watchlist =
                count { device -> device.sessionExportReviewCategory() == SessionExportReviewCategory.WATCHLIST },
            suspicious =
                count { device -> device.sessionExportReviewCategory() == SessionExportReviewCategory.SUSPICIOUS },
            publicSafety =
                count { device -> device.sessionExportReviewCategory() == SessionExportReviewCategory.PUBLIC_SAFETY },
            nearby =
                count { device -> device.sessionExportReviewCategory() == SessionExportReviewCategory.NEARBY },
            unknownNoise =
                count { device -> device.sessionExportReviewCategory() == SessionExportReviewCategory.UNKNOWN_NOISE },
        )

    private fun List<AlertEvidenceEvent>.alertHistorySummary(): SessionAlertHistorySummary =
        SessionAlertHistorySummary(
            eventCount = size,
            watchlistReturnCount = countType(AlertEvidenceEventType.WATCHLIST_RETURN),
            publicSafetySignalCount = countType(AlertEvidenceEventType.PUBLIC_SAFETY_SIGNAL),
            followMeAlertCount = countType(AlertEvidenceEventType.FOLLOW_ME_ALERT),
        )

    private fun List<AlertEvidenceEvent>.countType(type: AlertEvidenceEventType): Int {
        return count { event -> event.eventType == type }
    }

    private fun List<Device>.identityCarryoverSummary(): SessionIdentityCarryoverSummary =
        SessionIdentityCarryoverSummary(
            deviceCount = count { device -> device.hasActionableIdentityCarryoverEvidence() },
        )

    private fun List<io.blueeye.core.model.DetectionEvidence>.countAttentionEvidence(): Int =
        count(DetectionEvidenceClassifier::isAttentionEvidence)
}
