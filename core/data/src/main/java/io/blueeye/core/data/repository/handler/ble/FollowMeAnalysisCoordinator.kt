package io.blueeye.core.data.repository.handler.ble

import io.blueeye.core.alert.TrackerAlertService
import io.blueeye.core.data.diagnostics.ScoreDiagnosticLogger
import io.blueeye.core.data.tracker.FollowMeScoreCalculator
import io.blueeye.core.data.tracker.alert.AlertDecisionEngine
import io.blueeye.core.data.tracker.session.FollowMeSessionManager
import io.blueeye.core.location.LocationProvider
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.DeviceType
import io.blueeye.core.model.EvidenceProvenance
import io.blueeye.core.model.EvidenceSource
import io.blueeye.core.model.TrackingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.ArrayDeque
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns mutable Follow-Me session/RSSI state and score-side effects.
 *
 * Keeping this state behind one mutex makes explicit tracking resets atomic with score updates
 * without coupling the rest of BLE ingest to the Follow-Me implementation.
 */
@Singleton
internal class FollowMeAnalysisCoordinator @Inject constructor(
    private val classifier: ScanResultClassifier,
    private val followMeScoreCalculator: FollowMeScoreCalculator,
    private val trackerAlertService: TrackerAlertService,
    private val diagnosticLogger: ScoreDiagnosticLogger,
    private val locationProvider: LocationProvider,
    private val sessionManager: FollowMeSessionManager,
    private val alertDecisionEngine: AlertDecisionEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trackingStateMutex = Mutex()

    private val rssiBuffer =
        object : LinkedHashMap<String, ArrayDeque<Int>>(
            RSSI_DEVICE_BUFFER_CAPACITY,
            RSSI_BUFFER_LOAD_FACTOR,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, ArrayDeque<Int>>?,
            ): Boolean = size > RSSI_DEVICE_BUFFER_CAPACITY
        }

    suspend fun analyze(ctx: ScanDataContext) {
        if (ctx.isTactical) return

        val existing = ctx.existingDevice
        val isIgnored = existing?.isIgnoredForTracking == true
        if (shouldSuppressFollowMe(ctx, isIgnored)) {
            markFollowMeSuppressed(ctx)
            return
        }

        val now = System.currentTimeMillis()
        val currentLocation = locationProvider.getFreshCoordinates()
        val computation =
            trackingStateMutex.withLock {
                computeFollowMeLocked(
                    ctx = ctx,
                    now = now,
                    isIgnored = isIgnored,
                    currentLocation = currentLocation,
                )
            }

        logFollowMeScore(ctx, computation)
        dispatchFollowMeAlert(ctx, computation)
    }

    suspend fun reset() {
        trackingStateMutex.withLock {
            sessionManager.resetSession()
            rssiBuffer.clear()
        }
    }

    private fun shouldSuppressFollowMe(
        ctx: ScanDataContext,
        isIgnored: Boolean,
    ): Boolean =
        ctx.existingDevice?.isSafeBeacon == true ||
            isIgnored ||
            ctx.existingDevice?.isTrackingEnabled == false ||
            ctx.existingDevice?.calibrationLabel?.suppressesFollowMeScoring() == true

    private fun markFollowMeSuppressed(ctx: ScanDataContext) {
        ctx.followingScore = 0f
        ctx.trackingStatus = TrackingStatus.SAFE
        ctx.followMeExplanation = "User calibration marks this device safe or disables Follow-Me scoring."
        ctx.clearFollowMeScoreComponents()
    }

    private fun computeFollowMeLocked(
        ctx: ScanDataContext,
        now: Long,
        isIgnored: Boolean,
        currentLocation: Triple<Double?, Double?, Float?>?,
    ): FollowMeComputation {
        val fingerprint = ctx.fingerprint
        sessionManager.updateMovement(
            currentLat = currentLocation?.first,
            currentLon = currentLocation?.second,
            currentAccuracyM = currentLocation?.third,
            now = now,
        )
        val sessionFirstSeen = sessionManager.recordDeviceSighting(fingerprint, now)
        val movementTrackingAvailable = sessionManager.hasMovementReference()
        val userIsMoving = sessionManager.isUserMoving(now)
        val isBaselineDevice = sessionManager.isDeviceZastane(fingerprint)
        val deviceType = classifier.resolveType(ctx)
        val isKnownTracker = deviceType in KNOWN_TRACKER_TYPES
        val metrics =
            FollowMeScoreCalculator.DeviceMetrics(
                deviceType = deviceType,
                firstSeenAt = sessionFirstSeen,
                lastSeenAt = now,
                encounterCount = sessionManager.getMovingEncounterCount(fingerprint),
                rssiSamples = movingRssiSamples(fingerprint, ctx.validRssi, userIsMoving),
                macChangeCount = ctx.macChangeCount,
                isKnownTracker = isKnownTracker,
                hasStablePayload = ctx.hasStablePayloadEvidence(),
                userHasMoved = userIsMoving,
                isBaselineDevice = isBaselineDevice,
                movementTrackingAvailable = movementTrackingAvailable,
                observedWhileMovingDurationMs =
                    sessionManager.getObservedWhileMovingDurationMs(fingerprint),
            )
        val result = followMeScoreCalculator.calculateScore(metrics)
        applyFollowMeResult(ctx, result, userIsMoving, isBaselineDevice)

        val decisionExplanation =
            if (
                alertDecisionEngine.shouldAlert(
                    isIgnored = isIgnored,
                    userHasMoved = userIsMoving,
                    isZastane = isBaselineDevice,
                    trackingStatus = result.status,
                )
            ) {
                alertDecisionEngine.getDecisionExplanation(
                    isIgnored = isIgnored,
                    isKnownTracker = isKnownTracker,
                    userHasMoved = userIsMoving,
                    isZastane = isBaselineDevice,
                    trackingStatus = result.status,
                )
            } else {
                null
            }

        return FollowMeComputation(
            result = result,
            isKnownTracker = isKnownTracker,
            decisionExplanation = decisionExplanation,
        )
    }

    private fun applyFollowMeResult(
        ctx: ScanDataContext,
        result: FollowMeScoreCalculator.ScoreResult,
        userIsMoving: Boolean,
        isBaselineDevice: Boolean,
    ) {
        ctx.followingScore = result.totalScore.toFloat()
        ctx.trackingStatus = result.status
        ctx.followMeExplanation = result.explanation
        ctx.followMeDurationScore = result.durationScore
        ctx.followMeRssiStabilityScore = result.rssiStabilityScore
        ctx.followMeDeviceTypeScore = result.deviceTypeScore
        ctx.followMeMacBehaviorScore = result.macBehaviorScore
        ctx.followMeEncounterScore = result.encounterScore
        ctx.followMeUserMoved = userIsMoving
        ctx.followMeBaselineDevice = isBaselineDevice
    }

    private fun logFollowMeScore(
        ctx: ScanDataContext,
        computation: FollowMeComputation,
    ) {
        scope.launch {
            val location = locationProvider.getFreshCoordinates()
            diagnosticLogger.logScore(
                ctx.mac,
                computation.result,
                location?.first,
                location?.second,
            )
        }
    }

    private fun dispatchFollowMeAlert(
        ctx: ScanDataContext,
        computation: FollowMeComputation,
    ) {
        val decisionExplanation = computation.decisionExplanation ?: return
        ctx.followMeAlertEvidence =
            ctx.toFollowMeAlertEvidence(
                result = computation.result,
                decisionExplanation = decisionExplanation,
                isKnownTracker = computation.isKnownTracker,
            )
        trackerAlertService.onDeviceAnalyzed(
            mac = ctx.mac,
            score = computation.result.totalScore,
            status = computation.result.status,
            evidenceReason = computation.result.explanation,
            isKnownTracker = computation.isKnownTracker,
        )
    }

    private fun movingRssiSamples(
        fingerprint: String,
        rssi: Int,
        userIsMoving: Boolean,
    ): List<Int> {
        val history = rssiBuffer.getOrPut(fingerprint) { ArrayDeque(RSSI_HISTORY_CAPACITY) }
        val continuesMovingWindow = sessionManager.isMovingObservationContinuous(fingerprint)
        if (!userIsMoving || !continuesMovingWindow) history.clear()
        if (userIsMoving) {
            history.addLast(rssi)
            if (history.size > RSSI_HISTORY_CAPACITY) history.removeFirst()
        }
        return history.toList()
    }

    private fun ScanDataContext.hasStablePayloadEvidence(): Boolean =
        macChangeCount > 0 &&
            (
                rawData?.isNotEmpty() == true ||
                    manufacturerData?.isNotEmpty() == true ||
                    manufacturerDataById.isNotEmpty()
            )

    private companion object {
        private const val RSSI_DEVICE_BUFFER_CAPACITY = 100
        private const val RSSI_HISTORY_CAPACITY = 10
        private const val RSSI_BUFFER_LOAD_FACTOR = 0.75f

        private val KNOWN_TRACKER_TYPES =
            setOf(
                DeviceType.AIRTAG,
                DeviceType.TILE,
                DeviceType.SAMSUNG_TAG,
                DeviceType.TRACKER,
                DeviceType.TAG,
            )
    }
}

private data class FollowMeComputation(
    val result: FollowMeScoreCalculator.ScoreResult,
    val isKnownTracker: Boolean,
    val decisionExplanation: String?,
)

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
