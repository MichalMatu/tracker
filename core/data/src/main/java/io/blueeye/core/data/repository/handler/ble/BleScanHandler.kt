package io.blueeye.core.data.repository.handler.ble

import io.blueeye.core.alert.TacticalAlertService
import io.blueeye.core.alert.TrackerAlertService
import io.blueeye.core.connectivity.manager.AutoActiveProbeCoordinator
import io.blueeye.core.connectivity.manager.AutoActiveProbeScanCandidate
import io.blueeye.core.data.db.dao.DeviceDao
import io.blueeye.core.data.db.entity.DeviceEntity
import io.blueeye.core.data.diagnostics.ScoreDiagnosticLogger
import io.blueeye.core.data.evidence.AlertEvidenceEventRecorder
import io.blueeye.core.data.tracker.FollowMeScoreCalculator
import io.blueeye.core.data.tracker.alert.AlertDecisionEngine
import io.blueeye.core.data.tracker.session.FollowMeSessionManager
import io.blueeye.core.data.watchlist.WatchlistReturnAlertDecision
import io.blueeye.core.data.watchlist.WatchlistReturnAlertPolicy
import io.blueeye.core.location.LocationProvider
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.TrackingStatus
import io.blueeye.core.scanner.analysis.BlePacketAnalyzer
import io.blueeye.core.scanner.model.BleScanResultData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

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
    private val followMeScoreCalculator: FollowMeScoreCalculator,
    private val trackerAlertService: TrackerAlertService,
    private val diagnosticLogger: ScoreDiagnosticLogger,
    private val locationProvider: LocationProvider,
    private val sessionManager: FollowMeSessionManager,
    private val alertDecisionEngine: AlertDecisionEngine,
    private val autoActiveProbeCoordinator: AutoActiveProbeCoordinator,
    private val alertEvidenceEventRecorder: AlertEvidenceEventRecorder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recentWatchlistAlerts = ConcurrentHashMap<String, Long>()

    private val rssiBuffer = object : LinkedHashMap<String, ArrayDeque<Int>>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArrayDeque<Int>>?): Boolean {
            return size > 100
        }
    }

    /**
     * Main entry point for processing a BLE scan result.
     * Orchestrates the processing pipeline and triggers alerts.
     */
    suspend fun handle(data: BleScanResultData) {
        try {
            val ctx = ScanDataContext.fromScan(data)

            macAddressResolver.resolve(ctx)
            ctx.existingDevice = deviceDao.getByFingerprint(ctx.fingerprint)
            checkWatchlistAlert(ctx)

            classifier.classify(ctx)
            deviceEnricher.enrich(ctx)
            calculateFollowMeScore(ctx)
            logBeaconDetection(ctx)

            if (!ctx.isProvisional) {
                persister.persist(ctx, classifier)
                ctx.followMeAlertEvidence?.let { evidence ->
                    alertEvidenceEventRecorder.recordFollowMeAlert(
                        deviceFingerprint = ctx.fingerprint,
                        observedMac = ctx.mac,
                        evidence = evidence,
                    )
                }
                recordPublicSafetyEvidenceEvents(ctx)
                queueAutoActiveProbe(ctx)
            }
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
            )
        )
    }

    private suspend fun checkWatchlistAlert(ctx: ScanDataContext) {
        val existing = ctx.existingDevice ?: return
        val lastAlertAt =
            listOfNotNull(
                recentWatchlistAlerts[ctx.fingerprint],
                existing.lastWatchlistReturnAlertAt.takeIf { it > 0L },
            ).maxOrNull()
        val decision = WatchlistReturnAlertPolicy.evaluate(
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

    private suspend fun calculateFollowMeScore(ctx: ScanDataContext) {
        // Public-safety signal evidence is reviewed by its own pipeline.
        if (ctx.isTactical) return

        val existing = ctx.existingDevice
        val now = System.currentTimeMillis()
        val fingerprint = ctx.fingerprint
        val isIgnored = existing?.isIgnoredForTracking == true
        val isSuppressedForFollowMe =
            existing?.isSafeBeacon == true ||
                isIgnored ||
                existing?.isTrackingEnabled == false ||
                existing?.calibrationLabel?.suppressesFollowMeScoring() == true

        if (isSuppressedForFollowMe) {
            ctx.followingScore = 0f
            ctx.trackingStatus = TrackingStatus.SAFE
            ctx.followMeExplanation = "User calibration marks this device safe or disables Follow-Me scoring."
            ctx.clearFollowMeScoreComponents()
            return
        }

        val currentLocation = locationProvider.getFreshCoordinates()
        sessionManager.updateMovement(currentLocation?.first, currentLocation?.second)
        val sessionFirstSeen = sessionManager.recordDeviceSighting(fingerprint)
        val movementTrackingAvailable = sessionManager.hasMovementReference()
        val userHasMoved = sessionManager.hasUserMoved()
        val isBaselineDevice = sessionManager.isDeviceZastane(fingerprint)

        val deviceType = classifier.resolveType(ctx)
        val isKnownTracker = deviceType in listOf(
            DeviceType.AIRTAG,
            DeviceType.TILE,
            DeviceType.SAMSUNG_TAG,
        )

        val encounterCount = (existing?.encounterCount ?: 0) + 1
        val history = rssiBuffer.getOrPut(fingerprint) { ArrayDeque(10) }
        history.addLast(ctx.validRssi)
        if (history.size > 10) history.removeFirst()
        val rssiSamples = history.toList()

        val metrics = FollowMeScoreCalculator.DeviceMetrics(
            deviceType = deviceType,
            firstSeenAt = sessionFirstSeen,
            lastSeenAt = now,
            encounterCount = encounterCount,
            rssiSamples = rssiSamples,
            macChangeCount = ctx.macChangeCount,
            isKnownTracker = isKnownTracker,
            hasStablePayload = ctx.hasStablePayloadEvidence(),
            userHasMoved = userHasMoved,
            isBaselineDevice = isBaselineDevice,
            movementTrackingAvailable = movementTrackingAvailable,
        )

        val result = followMeScoreCalculator.calculateScore(metrics)

        ctx.followingScore = result.totalScore.toFloat()
        ctx.trackingStatus = result.status
        ctx.followMeExplanation = result.explanation
        ctx.followMeDurationScore = result.durationScore
        ctx.followMeRssiStabilityScore = result.rssiStabilityScore
        ctx.followMeDeviceTypeScore = result.deviceTypeScore
        ctx.followMeMacBehaviorScore = result.macBehaviorScore
        ctx.followMeEncounterScore = result.encounterScore
        ctx.followMeUserMoved = userHasMoved
        ctx.followMeBaselineDevice = isBaselineDevice

        scope.launch {
            val location = locationProvider.getFreshCoordinates()
            diagnosticLogger.logScore(ctx.mac, result, location?.first, location?.second)
        }

        val shouldAlert = alertDecisionEngine.shouldAlert(
            isIgnored = isIgnored,
            userHasMoved = userHasMoved,
            isZastane = isBaselineDevice,
            trackingStatus = result.status
        )

        if (shouldAlert) {
            val decisionExplanation = alertDecisionEngine.getDecisionExplanation(
                isIgnored = isIgnored,
                isKnownTracker = isKnownTracker,
                userHasMoved = userHasMoved,
                isZastane = isBaselineDevice,
                trackingStatus = result.status,
            )
            ctx.followMeAlertEvidence =
                ctx.toFollowMeAlertEvidence(
                    result = result,
                    decisionExplanation = decisionExplanation,
                    isKnownTracker = isKnownTracker,
                )
            trackerAlertService.onDeviceAnalyzed(
                mac = ctx.mac,
                score = result.totalScore,
                status = result.status,
                evidenceReason = result.explanation,
                isKnownTracker = isKnownTracker,
            )
        }
    }

    /** Reset session state when scanning starts or restarts. */
    fun resetSession() {
        sessionManager.resetSession()
        rssiBuffer.clear()
        autoActiveProbeCoordinator.reset()
    }

    private fun ScanDataContext.hasStablePayloadEvidence(): Boolean =
        macChangeCount > 0 &&
            (
                rawData?.isNotEmpty() == true ||
                    manufacturerData?.isNotEmpty() == true ||
                    manufacturerDataById.isNotEmpty()
            )

    companion object {
        private const val TAG = "BleScanHandler"
    }
}

private fun ScanDataContext.toFollowMeAlertEvidence(
    result: FollowMeScoreCalculator.ScoreResult,
    decisionExplanation: String,
    isKnownTracker: Boolean,
): DetectionEvidence =
    DetectionEvidence(
        source = EvidenceSource.FOLLOW_ME_SCORE,
        confidence = result.toFollowMeAlertConfidence(isKnownTracker),
        reasonText =
            "Follow-Me alert decision: $decisionExplanation. " +
                "Score ${result.totalScore}/100, status ${result.status.toFollowMeStatusLabel()}. " +
                "Evidence: ${result.explanation}.",
        timestamp = timestamp,
        rawValue =
            "score=${result.totalScore};duration=${result.durationScore};" +
                "rssi=${result.rssiStabilityScore};type=${result.deviceTypeScore};" +
                "mac=${result.macBehaviorScore};encounters=${result.encounterScore};" +
                "knownTracker=$isKnownTracker;userMoved=$followMeUserMoved;baseline=$followMeBaselineDevice",
        parsedValue =
            if (isKnownTracker && result.status == TrackingStatus.SAFE) {
                "KNOWN_TRACKER"
            } else {
                result.status.name
            },
        isPassive = true,
        provenance = EvidenceProvenance.FOLLOW_ME_ANALYSIS,
    )

private fun FollowMeScoreCalculator.ScoreResult.toFollowMeAlertConfidence(
    isKnownTracker: Boolean,
): DetectionConfidence =
    when {
        status == TrackingStatus.DANGEROUS -> DetectionConfidence.HIGH
        status == TrackingStatus.SUSPICIOUS -> DetectionConfidence.MEDIUM
        isKnownTracker -> DetectionConfidence.LOW
        else -> DetectionConfidence.LOW
    }

private fun TrackingStatus.toFollowMeStatusLabel(): String =
    when (this) {
        TrackingStatus.SAFE -> "safe"
        TrackingStatus.SUSPICIOUS -> "possible follow-me pattern"
        TrackingStatus.DANGEROUS -> "high attention follow-me score"
    }

private fun ScanDataContext.clearFollowMeScoreComponents() {
    followMeDurationScore = 0
    followMeRssiStabilityScore = 0
    followMeDeviceTypeScore = 0
    followMeMacBehaviorScore = 0
    followMeEncounterScore = 0
    followMeUserMoved = null
    followMeBaselineDevice = null
}

private fun DeviceCalibrationLabel.suppressesFollowMeScoring(): Boolean =
    this == DeviceCalibrationLabel.FALSE_POSITIVE || this == DeviceCalibrationLabel.KNOWN_SAFE
