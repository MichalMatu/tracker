package io.blueeye.core.domain.analysis

import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.DeviceCalibrationLabel
import io.blueeye.core.model.FollowMeHistorySample
import io.blueeye.core.model.IdentityContinuityCandidate
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.SignalSample
import io.blueeye.core.model.TrackingStatus
import io.blueeye.core.model.analysis.AnalysisCandidateV1
import io.blueeye.core.model.analysis.AnalysisContradictionV1
import io.blueeye.core.model.analysis.AnalysisDiagnosticsV1
import io.blueeye.core.model.analysis.AnalysisEvidenceSummaryV1
import io.blueeye.core.model.analysis.AnalysisFollowMeSummaryV1
import io.blueeye.core.model.analysis.AnalysisIdentityRelationV1
import io.blueeye.core.model.analysis.AnalysisLocalAssessmentV1
import io.blueeye.core.model.analysis.AnalysisLocationQualityV1
import io.blueeye.core.model.analysis.AnalysisQualityFlagV1
import io.blueeye.core.model.analysis.AnalysisSignalBucketV1
import io.blueeye.core.model.analysis.AnalysisSignalSummaryV1
import io.blueeye.core.model.analysis.AnalysisWindowV1

data class AnalysisHistoryCompleteness(
    val signalHistoryComplete: Boolean = false,
    val followMeHistoryComplete: Boolean = false,
    val identityHistoryComplete: Boolean = false,
)

object AnalysisReducerRulesV1 {
    const val reducerVersion = 1
    const val signalBucketMs = 30_000L
    const val maxLocationAccuracyMeters = 100f
    const val maxRepresentativeEvidence = 8
}

data class AnalysisReducerInputV1(
    val device: Device,
    val signalSamples: List<SignalSample>,
    val followMeHistory: List<FollowMeHistorySample>,
    val alertEvidenceEvents: List<AlertEvidenceEvent>,
    val identityCandidates: List<IdentityContinuityCandidate>,
    val historyCompleteness: AnalysisHistoryCompleteness = AnalysisHistoryCompleteness(),
)

/**
 * Pure deterministic reduction from already-loaded domain data to AnalysisCandidateV1.
 *
 * The reducer has no clock, database, Android or transport dependency.
 */
object DeterministicAnalysisReducer {
    fun reduce(input: AnalysisReducerInputV1): AnalysisCandidateV1 {
        val device = input.device
        val hasOutOfScopeSignals =
            input.signalSamples.any { sample -> sample.deviceFingerprint != device.fingerprint }
        val signals = scopedSignals(device.fingerprint, input.signalSamples)
        val followMe = input.followMeHistory.distinct().sortedWith(FOLLOW_ME_ORDER)
        val identityRelations = reduceIdentityRelations(device.fingerprint, input.identityCandidates)
        val signalSummary = AnalysisSignalReducer.reduce(signals)
        val followMeSummary = reduceFollowMe(followMe)
        val diagnostics =
            AnalysisDiagnosticsV1(
                representativeEvidence =
                    AnalysisEvidenceReducer.reduce(
                        deviceEvidence = device.evidence,
                        alertEvents =
                            input.alertEvidenceEvents.filter {
                                it.deviceFingerprint == device.fingerprint
                            },
                    ),
                qualityFlags =
                    qualityFlags(
                        signalSummary = signalSummary,
                        completeness = input.historyCompleteness,
                        hasOutOfScopeSignals = hasOutOfScopeSignals,
                    ),
                contradictions =
                    contradictions(
                        device = device,
                        followMe = followMe,
                        identityRelations = identityRelations,
                    ),
            )

        return AnalysisCandidateV1(
            localCandidateKey = device.fingerprint,
            window =
                AnalysisWindowV1(
                    firstSeenAt = device.firstSeenAt,
                    lastSeenAt = device.lastSeenAt,
                ),
            localAssessment =
                AnalysisLocalAssessmentV1(
                    trackingStatus = device.trackingStatus,
                    followingScore = device.followingScore,
                    calibrationLabel = device.calibrationLabel,
                    isWatchlisted = device.isInWatchlist,
                    isTrackingEnabled = device.isTrackingEnabled,
                    isIgnoredForTracking = device.isIgnoredForTracking,
                    isSafeBeacon = device.isSafeBeacon,
                ),
            signal = signalSummary,
            followMe = followMeSummary,
            identityRelations = identityRelations,
            diagnostics = diagnostics,
        )
    }

    private fun scopedSignals(
        fingerprint: String,
        samples: List<SignalSample>,
    ): List<SignalSample> =
        samples
            .asSequence()
            .filter { sample -> sample.deviceFingerprint == fingerprint }
            .distinct()
            .sortedWith(SIGNAL_ORDER)
            .toList()

    private fun reduceFollowMe(samples: List<FollowMeHistorySample>): AnalysisFollowMeSummaryV1 =
        AnalysisFollowMeSummaryV1(
            observationCount = samples.size,
            maxScore = samples.maxOfOrNull(FollowMeHistorySample::score),
            latestStatus = samples.maxByOrNull(FollowMeHistorySample::timestamp)?.trackingStatus,
            maxEncounterCount = samples.maxOfOrNull(FollowMeHistorySample::encounterCount) ?: 0,
            movedObservationCount = samples.count { it.userMoved == true },
            baselineObservationCount = samples.count { it.baselineDevice == true },
        )

    private fun reduceIdentityRelations(
        fingerprint: String,
        candidates: List<IdentityContinuityCandidate>,
    ): List<AnalysisIdentityRelationV1> =
        candidates
            .asSequence()
            .filter { candidate ->
                candidate.deviceFingerprint == fingerprint ||
                    candidate.candidateFingerprint == fingerprint
            }
            .distinct()
            .sortedWith(IDENTITY_ORDER)
            .map { candidate ->
                AnalysisIdentityRelationV1(
                    relatedLocalCandidateKey =
                        if (candidate.deviceFingerprint == fingerprint) {
                            candidate.candidateFingerprint
                        } else {
                            candidate.deviceFingerprint
                        },
                    timestamp = candidate.timestamp,
                    reasonCode = candidate.reasonCode,
                    confidence = candidate.confidence,
                    verdict = candidate.verdict,
                    featureSummary = candidate.featureSummary,
                )
            }
            .toList()

    private fun qualityFlags(
        signalSummary: AnalysisSignalSummaryV1,
        completeness: AnalysisHistoryCompleteness,
        hasOutOfScopeSignals: Boolean,
    ): List<AnalysisQualityFlagV1> =
        buildSet {
            if (signalSummary.sampleCount == 0) {
                add(AnalysisQualityFlagV1.NO_SIGNAL_SAMPLES)
            }
            if (hasOutOfScopeSignals) {
                add(AnalysisQualityFlagV1.OUT_OF_SCOPE_SIGNAL_SAMPLES_DROPPED)
            }
            if (signalSummary.locationQuality.samplesWithCoordinates == 0) {
                add(AnalysisQualityFlagV1.NO_LOCATION_DATA)
            }
            if (signalSummary.locationQuality.poorLocationSamples > 0) {
                add(AnalysisQualityFlagV1.POOR_LOCATION_QUALITY)
            }
            if (!completeness.signalHistoryComplete) {
                add(AnalysisQualityFlagV1.SIGNAL_HISTORY_INCOMPLETE)
            }
            if (!completeness.followMeHistoryComplete) {
                add(AnalysisQualityFlagV1.FOLLOW_ME_HISTORY_INCOMPLETE)
            }
            if (!completeness.identityHistoryComplete) {
                add(AnalysisQualityFlagV1.IDENTITY_HISTORY_INCOMPLETE)
            }
        }.sortedBy(AnalysisQualityFlagV1::name)

    private fun contradictions(
        device: Device,
        followMe: List<FollowMeHistorySample>,
        identityRelations: List<AnalysisIdentityRelationV1>,
    ): List<AnalysisContradictionV1> =
        buildSet {
            val calibratedSafe =
                device.isSafeBeacon ||
                    device.isIgnoredForTracking ||
                    device.calibrationLabel == DeviceCalibrationLabel.FALSE_POSITIVE ||
                    device.calibrationLabel == DeviceCalibrationLabel.KNOWN_SAFE
            if (calibratedSafe && device.trackingStatus != TrackingStatus.SAFE) {
                add(AnalysisContradictionV1.CALIBRATED_SAFE_BUT_LOCAL_ATTENTION_ACTIVE)
            }
            if (
                followMe.any {
                    it.baselineDevice == true && it.trackingStatus == TrackingStatus.DANGEROUS
                }
            ) {
                add(AnalysisContradictionV1.BASELINE_OBSERVATION_WITH_DANGEROUS_STATUS)
            }
            if (identityRelations.any { it.verdict == IdentityCarryoverVerdict.FALSE_MATCH }) {
                add(AnalysisContradictionV1.REJECTED_IDENTITY_RELATION_PRESENT)
            }
        }.sortedBy(AnalysisContradictionV1::name)

    private val SIGNAL_ORDER =
        compareBy<SignalSample>(
            SignalSample::timestamp,
            SignalSample::rssi,
            SignalSample::deviceFingerprint,
        ).thenBy { it.observedMac.orEmpty() }
            .thenBy { it.latitude ?: Double.NEGATIVE_INFINITY }
            .thenBy { it.longitude ?: Double.NEGATIVE_INFINITY }

    private val FOLLOW_ME_ORDER =
        compareBy<FollowMeHistorySample>(
            FollowMeHistorySample::timestamp,
            FollowMeHistorySample::score,
            FollowMeHistorySample::rssi,
            FollowMeHistorySample::observedMac,
        )

    private val IDENTITY_ORDER =
        compareBy<IdentityContinuityCandidate>(
            IdentityContinuityCandidate::timestamp,
            IdentityContinuityCandidate::candidateFingerprint,
            IdentityContinuityCandidate::deviceFingerprint,
            IdentityContinuityCandidate::reasonCode,
            IdentityContinuityCandidate::id,
        )
}

private object AnalysisSignalReducer {
    fun reduce(samples: List<SignalSample>): AnalysisSignalSummaryV1 {
        val locationQuality = reduceLocationQuality(samples)
        return AnalysisSignalSummaryV1(
            sampleCount = samples.size,
            minRssi = samples.minOfOrNull(SignalSample::rssi),
            maxRssi = samples.maxOfOrNull(SignalSample::rssi),
            averageRssi = samples.averageRssi(),
            buckets = reduceBuckets(samples),
            locationQuality = locationQuality,
        )
    }

    private fun reduceBuckets(samples: List<SignalSample>): List<AnalysisSignalBucketV1> =
        samples
            .groupBy { sample -> bucketStart(sample.timestamp) }
            .toSortedMap()
            .map { (startedAt, bucketSamples) ->
                AnalysisSignalBucketV1(
                    startedAt = startedAt,
                    sampleCount = bucketSamples.size,
                    minRssi = bucketSamples.minOf(SignalSample::rssi),
                    maxRssi = bucketSamples.maxOf(SignalSample::rssi),
                    averageRssi = bucketSamples.averageRssi() ?: 0.0,
                )
            }

    private fun reduceLocationQuality(samples: List<SignalSample>): AnalysisLocationQualityV1 {
        val withCoordinates =
            samples.filter { sample ->
                sample.latitude != null && sample.longitude != null
            }
        val usable = withCoordinates.filter(::hasUsableLocation)
        val accuracies = usable.mapNotNull(SignalSample::locationAccuracy)

        return AnalysisLocationQualityV1(
            samplesWithCoordinates = withCoordinates.size,
            usableLocationSamples = usable.size,
            poorLocationSamples = withCoordinates.size - usable.size,
            bestAccuracyMeters = accuracies.minOrNull(),
            worstAccuracyMeters = accuracies.maxOrNull(),
        )
    }

    private fun hasUsableLocation(sample: SignalSample): Boolean {
        val latitude = sample.latitude ?: return false
        val longitude = sample.longitude ?: return false
        val accuracy = sample.locationAccuracy ?: return false
        return latitude.isFinite() &&
            longitude.isFinite() &&
            accuracy.isFinite() &&
            latitude in Rules.minLatitude..Rules.maxLatitude &&
            longitude in Rules.minLongitude..Rules.maxLongitude &&
            accuracy > Rules.minAccuracyMeters &&
            accuracy <= AnalysisReducerRulesV1.maxLocationAccuracyMeters
    }

    private fun bucketStart(timestamp: Long): Long =
        Math.floorDiv(timestamp, AnalysisReducerRulesV1.signalBucketMs) * AnalysisReducerRulesV1.signalBucketMs

    private fun List<SignalSample>.averageRssi(): Double? =
        if (isEmpty()) {
            null
        } else {
            sumOf { it.rssi.toLong() }.toDouble() / size
        }

    private object Rules {
        const val minLatitude = -90.0
        const val maxLatitude = 90.0
        const val minLongitude = -180.0
        const val maxLongitude = 180.0
        const val minAccuracyMeters = 0f
    }
}

private object AnalysisEvidenceReducer {
    fun reduce(
        deviceEvidence: List<DetectionEvidence>,
        alertEvents: List<AlertEvidenceEvent>,
    ): List<AnalysisEvidenceSummaryV1> {
        val candidates =
            buildList {
                deviceEvidence.forEach { evidence ->
                    add(EvidenceCandidate(evidence = evidence, eventType = null))
                }
                alertEvents.forEach { event ->
                    add(EvidenceCandidate(evidence = event.evidence, eventType = event.eventType))
                }
            }

        return candidates
            .sortedWith(EVIDENCE_ORDER)
            .distinctBy(EvidenceCandidate::dedupeKey)
            .take(AnalysisReducerRulesV1.maxRepresentativeEvidence)
            .map(EvidenceCandidate::toSummary)
    }

    private fun EvidenceCandidate.toSummary(): AnalysisEvidenceSummaryV1 =
        AnalysisEvidenceSummaryV1(
            source = evidence.source,
            confidence = evidence.confidence,
            provenance = evidence.provenance,
            reasonText = evidence.reasonText,
            timestamp = evidence.timestamp,
            eventType = eventType,
        )

    private fun EvidenceCandidate.dedupeKey(): EvidenceKey =
        EvidenceKey(
            source = evidence.source.name,
            confidence = evidence.confidence.name,
            provenance = evidence.provenance.name,
            reasonText = evidence.reasonText,
            timestamp = evidence.timestamp,
        )

    private val EVIDENCE_ORDER =
        compareByDescending<EvidenceCandidate> { confidenceRank(it.evidence.confidence) }
            .thenByDescending { it.evidence.timestamp }
            .thenByDescending { it.eventType != null }
            .thenBy { it.evidence.source.name }
            .thenBy { it.evidence.provenance.name }
            .thenBy { it.evidence.reasonText }
            .thenBy { it.eventType?.name.orEmpty() }

    private fun confidenceRank(confidence: DetectionConfidence): Int =
        when (confidence) {
            DetectionConfidence.CRITICAL -> 4
            DetectionConfidence.HIGH -> 3
            DetectionConfidence.MEDIUM -> 2
            DetectionConfidence.LOW -> 1
        }
}

private data class EvidenceCandidate(
    val evidence: DetectionEvidence,
    val eventType: AlertEvidenceEventType?,
)

private data class EvidenceKey(
    val source: String,
    val confidence: String,
    val provenance: String,
    val reasonText: String,
    val timestamp: Long,
)
