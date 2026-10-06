package io.blueeye.core.data.repository.handler.ble

import io.blueeye.core.alert.TacticalAlertService
import io.blueeye.core.connectivity.manager.AutoActiveProbeCoordinator
import io.blueeye.core.connectivity.manager.AutoActiveProbeScanCandidate
import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.data.evidence.AlertEvidenceEventRecorder
import io.blueeye.core.data.watchlist.WatchlistReturnAlertDecision
import io.blueeye.core.data.watchlist.WatchlistReturnAlertPolicy
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.scanner.analysis.BlePacketAnalyzer
import io.blueeye.core.scanner.model.BleScanResultData
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

internal data class BleScanProcessingOutcome(
    val provisionalDiscarded: Boolean,
    val persistenceOutcome: BlePersistenceOutcome?,
)

/**
 * Main coordinator for handling BLE scan results.
 *
 * This class orchestrates the scan processing pipeline from a raw scan result
 * through enrichment, scoring, evidence recording, persistence, and alerts.
 */
@Singleton
@Suppress("LongParameterList")
class BleScanHandler @Inject constructor(
    private val deviceDao: DeviceDao,
    private val macAddressResolver: MacAddressResolver,
    private val deviceEnricher: DeviceEnricher,
    private val classifier: ScanResultClassifier,
    private val persister: DevicePersister,
    private val tacticalAlertService: TacticalAlertService,
    private val followMeAnalysisCoordinator: FollowMeAnalysisCoordinator,
    private val autoActiveProbeCoordinator: AutoActiveProbeCoordinator,
    private val alertEvidenceEventRecorder: AlertEvidenceEventRecorder,
) {
    private val recentWatchlistAlerts = ConcurrentHashMap<String, Long>()

    /**
     * Main entry point for processing a BLE scan result.
     * Orchestrates the processing pipeline and triggers alerts.
     */
    internal suspend fun handle(data: BleScanResultData): BleScanProcessingOutcome {
        try {
            val ctx = ScanDataContext.fromScan(data)

            macAddressResolver.resolve(ctx)
            ctx.existingDevice = deviceDao.getByFingerprint(ctx.fingerprint)
            checkWatchlistAlert(ctx)

            classifier.classify(ctx)
            deviceEnricher.enrich(ctx)
            followMeAnalysisCoordinator.analyze(ctx)
            logBeaconDetection(ctx)

            if (ctx.isProvisional) {
                return BleScanProcessingOutcome(
                    provisionalDiscarded = true,
                    persistenceOutcome = null,
                )
            }

            val persistenceOutcome = persister.persist(ctx, classifier)
            ctx.followMeAlertEvidence?.let { evidence ->
                alertEvidenceEventRecorder.recordFollowMeAlert(
                    deviceFingerprint = ctx.fingerprint,
                    observedMac = ctx.mac,
                    evidence = evidence,
                )
            }
            recordPublicSafetyEvidenceEvents(ctx)
            queueAutoActiveProbe(ctx)
            return BleScanProcessingOutcome(
                provisionalDiscarded = false,
                persistenceOutcome = persistenceOutcome,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error handling scan result", e)
            throw e
        }
    }

    private fun queueAutoActiveProbe(ctx: ScanDataContext) {
        val existing = ctx.existingDevice
        autoActiveProbeCoordinator.enqueueCandidate(
            AutoActiveProbeScanCandidate(
                fingerprint = ctx.fingerprint,
                mac = ctx.mac,
                isConnectable = ctx.isConnectable,
                connectionStatus = existing?.connectionStatus,
                lastProbeTimestamp = existing?.lastProbeTimestamp ?: 0L,
                now = ctx.timestamp,
            ),
        )
    }

    private suspend fun checkWatchlistAlert(ctx: ScanDataContext) {
        val existing = ctx.existingDevice ?: return
        val lastAlertAt =
            listOfNotNull(
                recentWatchlistAlerts[ctx.fingerprint],
                existing.lastWatchlistReturnAlertAt.takeIf { it > 0L },
            ).maxOrNull()
        val decision =
            WatchlistReturnAlertPolicy.evaluate(
                isWatchlisted = existing.isInWatchlist,
                isTrackingEnabled = existing.isTrackingEnabled,
                previousLastSeenAt = existing.lastSeenAt,
                currentSeenAt = ctx.timestamp,
                lastAlertAt = lastAlertAt,
            )

        if (decision is WatchlistReturnAlertDecision.Alert) {
            recentWatchlistAlerts[ctx.fingerprint] = ctx.timestamp
            val evidence = existing.toWatchlistReturnEvidence(ctx, decision)
            deviceDao.recordWatchlistReturnAlert(
                fingerprint = ctx.fingerprint,
                timestamp = ctx.timestamp,
                offlineDurationMs = decision.offlineDurationMs,
            )
            alertEvidenceEventRecorder.recordWatchlistReturn(
                deviceFingerprint = ctx.fingerprint,
                observedMac = ctx.mac,
                evidence = evidence,
            )
            tacticalAlertService.onWatchlistDeviceReturned(
                mac = ctx.mac,
                rssi = ctx.rssi,
                evidence = evidence,
            )
        }
    }

    private suspend fun recordPublicSafetyEvidenceEvents(ctx: ScanDataContext) {
        if (!ctx.isTactical) return

        ctx.tacticalEvidence.forEach { evidence ->
            alertEvidenceEventRecorder.recordPublicSafetySignal(
                deviceFingerprint = ctx.fingerprint,
                observedMac = ctx.mac,
                evidence = evidence,
            )
        }
    }

    private fun DeviceEntity.toWatchlistReturnEvidence(
        ctx: ScanDataContext,
        decision: WatchlistReturnAlertDecision.Alert,
    ): DetectionEvidence =
        DetectionEvidence(
            source = EvidenceSource.WATCHLIST,
            confidence = DetectionConfidence.CRITICAL,
            reasonText = "Watchlist device returned after ${decision.offlineDurationMs / 1000}s offline.",
            timestamp = ctx.timestamp,
            rawValue = fingerprint,
            parsedValue = userAlias ?: lastDeviceName ?: ctx.name,
            isPassive = true,
            provenance = EvidenceProvenance.BLE_ADVERTISEMENT,
        )

    private fun logBeaconDetection(ctx: ScanDataContext) {
        val beaconType = ctx.beaconType ?: return

        android.util.Log.d(TAG, "Beacon signal recognized: $beaconType for ${ctx.mac}")

        val rawData = ctx.rawData
        if (rawData != null) {
            val analysis = BlePacketAnalyzer.analyze(rawData)
            android.util.Log.v("BlePacketAnalyzer", "Structure for ${ctx.mac} ($beaconType):\n$analysis")
        }
    }

    /** Reset logical tracking memory only on an explicit tracking reset, never on a technical scan restart. */
    suspend fun resetSession() {
        followMeAnalysisCoordinator.reset()
        autoActiveProbeCoordinator.reset()
    }

    private companion object {
        private const val TAG = "BleScanHandler"
    }
}
