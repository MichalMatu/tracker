package io.blueeye.core.domain.analysis

import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.FollowMeHistorySample
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.IdentityContinuityCandidate
import io.blueeye.core.model.SignalSample
import io.blueeye.core.model.TrackingStatus
import io.blueeye.core.model.analysis.AnalysisContradiction
import io.blueeye.core.model.analysis.AnalysisContradictionType
import io.blueeye.core.model.analysis.AnalysisIdentityCandidate
import io.blueeye.core.model.analysis.AnalysisIdentityDisposition
import io.blueeye.core.model.analysis.AnalysisIdentitySummary
import io.blueeye.core.model.analysis.AnalysisInput
import io.blueeye.core.model.analysis.AnalysisLocationQualitySummary
import io.blueeye.core.model.analysis.AnalysisMovementSummary
import io.blueeye.core.model.analysis.AnalysisQualityFlag
import io.blueeye.core.model.analysis.AnalysisRepresentativeEvidence
import io.blueeye.core.model.analysis.AnalysisSignalSummary
import io.blueeye.core.model.analysis.AnalysisTimeBucket
import io.blueeye.core.model.analysis.AnalysisWindow
import io.blueeye.core.model.analysis.DeterministicAnalysisCandidate
import kotlin.math.abs

/**
 * Pure, deterministic reduction of persisted observation models into a bounded analysis candidate.
 *
 * The reducer owns no clock, storage or Android dependencies. Exact observation coordinates and raw
 * payload values are intentionally omitted from its output.
 */
object DeterministicAnalysisReducer {
    fun reduce(input: AnalysisInput): DeterministicAnalysisCandidate {
        val uniqueSignals = input.signalSamples.distinct().sortedWith(SIGNAL_ORDER)
        val acceptedSignals = uniqueSignals.filter(::isUsableSignal)
        val uniqueFollowMe = input.followMeHistory.distinct().sortedWith(FOLLOW_ME_ORDER)
        val uniqueEvents = input.alertEvidenceEvents.distinct().sortedWith(ALERT_EVENT_ORDER)
        val uniqueCandidates = input.identityCandidates.distinct().sortedWith(IDENTITY_INPUT_ORDER)

        val signalSummary = signalSummary(input.signalSamples.size, uniqueSignals, acceptedSignals)
        val locationSummary = locationSummary(acceptedSignals)
        val movementSummary = movementSummary(input.device.encounterCount, uniqueFollowMe)
        val identityCandidates = identityCandidates(input.device.fingerprint, uniqueCandidates, acceptedSignals)
        val identitySummary =
            AnalysisIdentitySummary(
                sourceCandidateCount = input.identityCandidates.size,
                omittedCandidateCount = (identityCandidates.size - MAX_IDENTITY_CANDIDATES).coerceAtLeast(0),
                candidates = identityCandidates.take(MAX_IDENTITY_CANDIDATES),
            )
        val representativeEvidence = representativeEvidence(input.device.evidence, uniqueEvents)
        val contradictions =
            contradictions(
                input = input,
                followMeHistory = uniqueFollowMe,
                identityCandidates = identityCandidates,
                rawIdentityCandidates = uniqueCandidates,
            )

        val duplicateCount =
            (input.signalSamples.size - uniqueSignals.size) +
                (input.followMeHistory.size - uniqueFollowMe.size) +
                (input.alertEvidenceEvents.size - uniqueEvents.size) +
                (input.identityCandidates.size - uniqueCandidates.size)

        val qualityFlags =
            qualityFlags(
                duplicateCount = duplicateCount,
                signalSummary = signalSummary,
                locationSummary = locationSummary,
                movementSummary = movementSummary,
                identitySummary = identitySummary,
                representativeEvidenceCount = representativeEvidence.totalCount,
            )

        return DeterministicAnalysisCandidate(
            deviceFingerprint = input.device.fingerprint,
            localTrackingStatus = input.device.trackingStatus,
            localFollowingScore = input.device.followingScore,
            window =
                analysisWindow(
                    input = input,
                    acceptedSignals = acceptedSignals,
                    followMeHistory = uniqueFollowMe,
                    identityCandidates = uniqueCandidates,
                ),
            signal = signalSummary,
            locationQuality = locationSummary,
            movement = movementSummary,
            identity = identitySummary,
            timeBuckets = timeBuckets(acceptedSignals),
            representativeEvidence = representativeEvidence.items,
            qualityFlags = qualityFlags,
            contradictions = contradictions,
        )
    }

    private fun signalSummary(
        sourceCount: Int,
        uniqueSignals: List<SignalSample>,
        acceptedSignals: List<SignalSample>,
    ): AnalysisSignalSummary {
        val rssi = acceptedSignals.map(SignalSample::rssi).sorted()
        return AnalysisSignalSummary(
            sourceSampleCount = sourceCount,
            uniqueSampleCount = uniqueSignals.size,
            acceptedSampleCount = acceptedSignals.size,
            duplicateSampleCount = sourceCount - uniqueSignals.size,
            rejectedSampleCount = uniqueSignals.size - acceptedSignals.size,
            minRssi = rssi.firstOrNull(),
            maxRssi = rssi.lastOrNull(),
            averageRssi = rssi.averageIntOrNull(),
            medianRssi = rssi.medianOrNull(),
        )
    }

    private fun locationSummary(signals: List<SignalSample>): AnalysisLocationQualitySummary {
        val withLocation = signals.filter(::hasAnyLocationData)
        val usable = withLocation.filter(::hasUsableLocation)
        val accuracies = usable.map { requireNotNull(it.locationAccuracy) }.sorted()

        return AnalysisLocationQualitySummary(
            sourceLocationCount = withLocation.size,
            usableLocationCount = usable.size,
            rejectedLocationCount = withLocation.size - usable.size,
            bestAccuracyMeters = accuracies.firstOrNull(),
            worstAccuracyMeters = accuracies.lastOrNull(),
            averageAccuracyMeters = accuracies.map(Float::toDouble).averageDoubleOrNull(),
        )
    }

    private fun movementSummary(
        deviceEncounterCount: Int,
        history: List<FollowMeHistorySample>,
    ): AnalysisMovementSummary {
        var encounterSegments = 0
        var movingSegments = 0
        var previousTimestamp: Long? = null
        var inMovingSegment = false

        history.forEach { sample ->
            val previous = previousTimestamp
            val contiguous =
                previous != null &&
                    sample.timestamp >= previous &&
                    sample.timestamp - previous <= FOLLOW_ME_SEGMENT_GAP_MS

            if (previous == null || !contiguous) {
                encounterSegments += 1
                inMovingSegment = false
            }

            if (sample.userMoved == true) {
                if (!inMovingSegment) {
                    movingSegments += 1
                }
                inMovingSegment = true
            } else {
                inMovingSegment = false
            }
            previousTimestamp = sample.timestamp
        }

        val maxHistoryEncounterCount = history.maxOfOrNull(FollowMeHistorySample::encounterCount) ?: 0

        return AnalysisMovementSummary(
            sourceObservationCount = history.size,
            encounterSegmentCount = encounterSegments,
            movingSegmentCount = movingSegments,
            movingObservationCount = history.count { it.userMoved == true },
            stationaryObservationCount = history.count { it.userMoved == false },
            unknownMovementObservationCount = history.count { it.userMoved == null },
            maxEncounterCount = maxOf(deviceEncounterCount, maxHistoryEncounterCount, 0),
        )
    }

    private fun identityCandidates(
        primaryFingerprint: String,
        candidates: List<IdentityContinuityCandidate>,
        signals: List<SignalSample>,
    ): List<AnalysisIdentityCandidate> {
        val timestampsByFingerprint =
            signals
                .groupBy { sample -> sample.deviceFingerprint.ifBlank { primaryFingerprint } }
                .mapValues { (_, samples) -> samples.map(SignalSample::timestamp).sorted() }

        return candidates
            .map { candidate ->
                val coexistence =
                    hasCoexistence(
                        first = timestampsByFingerprint[candidate.deviceFingerprint].orEmpty(),
                        second = timestampsByFingerprint[candidate.candidateFingerprint].orEmpty(),
                    )
                AnalysisIdentityCandidate(
                    candidateFingerprint = candidate.candidateFingerprint,
                    timestamp = candidate.timestamp,
                    reasonCode = candidate.reasonCode,
                    confidence = candidate.confidence.takeIf(Float::isFinite) ?: 0f,
                    verdict = candidate.verdict,
                    disposition =
                        when {
                            coexistence -> AnalysisIdentityDisposition.COEXISTENCE_CONFLICT
                            candidate.verdict == IdentityCarryoverVerdict.FALSE_MATCH ->
                                AnalysisIdentityDisposition.REJECTED_BY_REVIEW
                            candidate.verdict == IdentityCarryoverVerdict.CONFIRMED_SAME_DEVICE ->
                                AnalysisIdentityDisposition.CONFIRMED_CONTINUITY
                            candidate.verdict == IdentityCarryoverVerdict.INCONCLUSIVE ->
                                AnalysisIdentityDisposition.INCONCLUSIVE
                            else -> AnalysisIdentityDisposition.POSSIBLE_CONTINUITY
                        },
                )
            }
            .sortedWith(IDENTITY_OUTPUT_ORDER)
    }

    private fun hasCoexistence(
        first: List<Long>,
        second: List<Long>,
    ): Boolean {
        var firstIndex = 0
        var secondIndex = 0
        while (firstIndex < first.size && secondIndex < second.size) {
            val firstTimestamp = first[firstIndex]
            val secondTimestamp = second[secondIndex]
            if (abs(firstTimestamp - secondTimestamp) <= IDENTITY_COEXISTENCE_WINDOW_MS) {
                return true
            }
            if (firstTimestamp < secondTimestamp) {
                firstIndex += 1
            } else {
                secondIndex += 1
            }
        }
        return false
    }

    private fun timeBuckets(signals: List<SignalSample>): List<AnalysisTimeBucket> =
        signals
            .groupBy { sample -> Math.floorDiv(sample.timestamp, TIME_BUCKET_MS) * TIME_BUCKET_MS }
            .toSortedMap()
            .map { (bucketStart, samples) ->
                val rssi = samples.map(SignalSample::rssi)
                AnalysisTimeBucket(
                    startTimestamp = bucketStart,
                    endExclusiveTimestamp = bucketStart + TIME_BUCKET_MS,
                    sampleCount = samples.size,
                    minRssi = rssi.min(),
                    maxRssi = rssi.max(),
                    averageRssi = rssi.map(Int::toDouble).average(),
                    usableLocationCount = samples.count(::hasUsableLocation),
                )
            }

    private fun representativeEvidence(
        deviceEvidence: List<DetectionEvidence>,
        events: List<io.blueeye.core.model.AlertEvidenceEvent>,
    ): RepresentativeEvidenceSelection {
        val fromDevice = deviceEvidence.map { evidence -> evidence.toAnalysisEvidence(alertEventType = null) }
        val fromEvents =
            events.map { event ->
                event.evidence.toAnalysisEvidence(alertEventType = event.eventType)
            }
        val canonical =
            (fromDevice + fromEvents)
                .distinct()
                .sortedWith(REPRESENTATIVE_EVIDENCE_ORDER)

        return RepresentativeEvidenceSelection(
            totalCount = canonical.size,
            items = canonical.take(MAX_REPRESENTATIVE_EVIDENCE),
        )
    }

    private fun DetectionEvidence.toAnalysisEvidence(
        alertEventType: io.blueeye.core.model.AlertEvidenceEventType?,
    ): AnalysisRepresentativeEvidence =
        AnalysisRepresentativeEvidence(
            source = source,
            confidence = confidence,
            reasonText = reasonText,
            timestamp = timestamp,
            parsedValue = parsedValue,
            isPassive = isPassive,
            provenance = provenance,
            alertEventType = alertEventType,
        )

    private fun contradictions(
        input: AnalysisInput,
        followMeHistory: List<FollowMeHistorySample>,
        identityCandidates: List<AnalysisIdentityCandidate>,
        rawIdentityCandidates: List<IdentityContinuityCandidate>,
    ): List<AnalysisContradiction> {
        val contradictions = mutableListOf<AnalysisContradiction>()

        identityCandidates
            .filter { it.disposition == AnalysisIdentityDisposition.COEXISTENCE_CONFLICT }
            .forEach { candidate ->
                contradictions +=
                    AnalysisContradiction(
                        type = AnalysisContradictionType.IDENTITY_COEXISTENCE,
                        detail = "candidate=${candidate.candidateFingerprint}",
                    )
            }

        rawIdentityCandidates
            .filter {
                it.verdict == IdentityCarryoverVerdict.FALSE_MATCH &&
                    it.confidence.isFinite() &&
                    it.confidence >= HIGH_IDENTITY_CONFIDENCE
            }
            .forEach { candidate ->
                contradictions +=
                    AnalysisContradiction(
                        type = AnalysisContradictionType.IDENTITY_REVIEW_CONFLICT,
                        detail = "rejected high-confidence candidate=${candidate.candidateFingerprint}",
                    )
            }

        val latestFollowMe = followMeHistory.lastOrNull()
        if (latestFollowMe != null && latestFollowMe.trackingStatus != input.device.trackingStatus) {
            contradictions +=
                AnalysisContradiction(
                    type = AnalysisContradictionType.TRACKING_STATUS_DIVERGENCE,
                    detail =
                        "device=${input.device.trackingStatus.name}, latestFollowMe=${latestFollowMe.trackingStatus.name}",
                )
        }

        val historyStatuses = followMeHistory.map(FollowMeHistorySample::trackingStatus).toSet()
        if (TrackingStatus.SAFE in historyStatuses && TrackingStatus.DANGEROUS in historyStatuses) {
            contradictions +=
                AnalysisContradiction(
                    type = AnalysisContradictionType.TRACKING_STATUS_DIVERGENCE,
                    detail = "follow-me history contains SAFE and DANGEROUS states",
                )
        }

        if (followMeHistory.any { it.trackingStatus == TrackingStatus.DANGEROUS && it.userMoved == false }) {
            contradictions +=
                AnalysisContradiction(
                    type = AnalysisContradictionType.FOLLOW_ME_WITHOUT_MOVEMENT,
                    detail = "dangerous follow-me state observed while movement was explicitly false",
                )
        }

        return contradictions
            .distinct()
            .sortedWith(compareBy<AnalysisContradiction>({ it.type.name }, AnalysisContradiction::detail))
    }

    private fun qualityFlags(
        duplicateCount: Int,
        signalSummary: AnalysisSignalSummary,
        locationSummary: AnalysisLocationQualitySummary,
        movementSummary: AnalysisMovementSummary,
        identitySummary: AnalysisIdentitySummary,
        representativeEvidenceCount: Int,
    ): List<AnalysisQualityFlag> {
        val flags = mutableSetOf<AnalysisQualityFlag>()
        if (duplicateCount > 0) flags += AnalysisQualityFlag.DUPLICATES_REDUCED
        if (signalSummary.rejectedSampleCount > 0) flags += AnalysisQualityFlag.INVALID_SIGNAL_SAMPLES
        if (signalSummary.acceptedSampleCount == 0) flags += AnalysisQualityFlag.NO_SIGNAL_SAMPLES
        if (locationSummary.usableLocationCount == 0) flags += AnalysisQualityFlag.NO_USABLE_LOCATION
        if (
            locationSummary.usableLocationCount > 0 &&
            locationSummary.usableLocationCount < signalSummary.acceptedSampleCount
        ) {
            flags += AnalysisQualityFlag.PARTIAL_LOCATION_COVERAGE
        }
        if (locationSummary.rejectedLocationCount > 0) flags += AnalysisQualityFlag.INVALID_LOCATION_SAMPLES
        if (movementSummary.sourceObservationCount == 0) flags += AnalysisQualityFlag.NO_FOLLOW_ME_HISTORY
        if (movementSummary.unknownMovementObservationCount > 0) flags += AnalysisQualityFlag.MOVEMENT_UNKNOWN
        if (identitySummary.candidates.any { it.disposition == AnalysisIdentityDisposition.COEXISTENCE_CONFLICT }) {
            flags += AnalysisQualityFlag.IDENTITY_COEXISTENCE_DETECTED
        }
        if (identitySummary.omittedCandidateCount > 0) flags += AnalysisQualityFlag.IDENTITY_CANDIDATES_TRUNCATED
        if (representativeEvidenceCount > MAX_REPRESENTATIVE_EVIDENCE) {
            flags += AnalysisQualityFlag.REPRESENTATIVE_EVIDENCE_TRUNCATED
        }
        return flags.sortedBy(AnalysisQualityFlag::name)
    }

    private fun analysisWindow(
        input: AnalysisInput,
        acceptedSignals: List<SignalSample>,
        followMeHistory: List<FollowMeHistorySample>,
        identityCandidates: List<IdentityContinuityCandidate>,
    ): AnalysisWindow {
        val timestamps =
            buildList {
                add(input.device.firstSeenAt)
                add(input.device.lastSeenAt)
                acceptedSignals.mapTo(this, SignalSample::timestamp)
                followMeHistory.mapTo(this, FollowMeHistorySample::timestamp)
                input.alertEvidenceEvents.mapTo(this) { it.timestamp }
                identityCandidates.mapTo(this, IdentityContinuityCandidate::timestamp)
            }.filter { it >= 0L }

        return AnalysisWindow(
            startTimestamp = timestamps.minOrNull() ?: 0L,
            endTimestamp = timestamps.maxOrNull() ?: 0L,
        )
    }

    private fun isUsableSignal(sample: SignalSample): Boolean =
        sample.timestamp >= 0L && sample.rssi in MIN_RSSI..MAX_RSSI

    private fun hasAnyLocationData(sample: SignalSample): Boolean =
        sample.latitude != null || sample.longitude != null || sample.locationAccuracy != null

    private fun hasUsableLocation(sample: SignalSample): Boolean {
        val latitude = sample.latitude ?: return false
        val longitude = sample.longitude ?: return false
        val accuracy = sample.locationAccuracy ?: return false
        return latitude.isFinite() &&
            longitude.isFinite() &&
            accuracy.isFinite() &&
            latitude in MIN_LATITUDE..MAX_LATITUDE &&
            longitude in MIN_LONGITUDE..MAX_LONGITUDE &&
            accuracy > MIN_ACCURACY_METERS &&
            accuracy <= MAX_ACCURACY_METERS
    }

    private fun List<Int>.averageIntOrNull(): Double? =
        if (isEmpty()) null else sumOf(Int::toLong).toDouble() / size

    private fun List<Double>.averageDoubleOrNull(): Double? =
        if (isEmpty()) null else sum() / size

    private fun List<Int>.medianOrNull(): Double? {
        if (isEmpty()) return null
        val middle = size / 2
        return if (size % 2 == 1) {
            this[middle].toDouble()
        } else {
            (this[middle - 1].toDouble() + this[middle].toDouble()) / 2.0
        }
    }

    private fun confidencePriority(confidence: DetectionConfidence): Int =
        when (confidence) {
            DetectionConfidence.LOW -> 1
            DetectionConfidence.MEDIUM -> 2
            DetectionConfidence.HIGH -> 3
            DetectionConfidence.CRITICAL -> 4
        }

    private val SIGNAL_ORDER =
        compareBy<SignalSample>(
            SignalSample::timestamp,
            SignalSample::deviceFingerprint,
            { it.observedMac.orEmpty() },
            SignalSample::rssi,
            { it.latitude ?: Double.NEGATIVE_INFINITY },
            { it.longitude ?: Double.NEGATIVE_INFINITY },
            { it.locationAccuracy ?: Float.NEGATIVE_INFINITY },
        )

    private val FOLLOW_ME_ORDER =
        compareBy<FollowMeHistorySample>(
            FollowMeHistorySample::timestamp,
            FollowMeHistorySample::observedMac,
            { it.trackingStatus.name },
            FollowMeHistorySample::score,
            FollowMeHistorySample::rssi,
        )

    private val ALERT_EVENT_ORDER =
        compareBy<io.blueeye.core.model.AlertEvidenceEvent>(
            { it.timestamp },
            { it.deviceFingerprint },
            { it.observedMac },
            { it.eventType.name },
            { it.evidence.source.name },
            { it.evidence.reasonText },
        )

    private val IDENTITY_INPUT_ORDER =
        compareBy<IdentityContinuityCandidate>(
            IdentityContinuityCandidate::timestamp,
            IdentityContinuityCandidate::candidateFingerprint,
            IdentityContinuityCandidate::reasonCode,
            IdentityContinuityCandidate::confidence,
            { it.verdict.name },
        )

    private val IDENTITY_OUTPUT_ORDER =
        compareByDescending<AnalysisIdentityCandidate> { it.confidence }
            .thenByDescending(AnalysisIdentityCandidate::timestamp)
            .thenBy(AnalysisIdentityCandidate::candidateFingerprint)
            .thenBy(AnalysisIdentityCandidate::reasonCode)
            .thenBy { it.verdict.name }

    private val REPRESENTATIVE_EVIDENCE_ORDER =
        compareByDescending<AnalysisRepresentativeEvidence> { confidencePriority(it.confidence) }
            .thenByDescending(AnalysisRepresentativeEvidence::timestamp)
            .thenBy { it.source.name }
            .thenBy { it.provenance.name }
            .thenBy(AnalysisRepresentativeEvidence::reasonText)
            .thenBy { it.parsedValue.orEmpty() }
            .thenBy { it.alertEventType?.name.orEmpty() }

    private data class RepresentativeEvidenceSelection(
        val totalCount: Int,
        val items: List<AnalysisRepresentativeEvidence>,
    )

    private const val TIME_BUCKET_MS = 60_000L
    private const val FOLLOW_ME_SEGMENT_GAP_MS = 30_000L
    private const val IDENTITY_COEXISTENCE_WINDOW_MS = 2_000L
    private const val HIGH_IDENTITY_CONFIDENCE = 0.75f
    private const val MAX_IDENTITY_CANDIDATES = 8
    private const val MAX_REPRESENTATIVE_EVIDENCE = 8
    private const val MIN_RSSI = -127
    private const val MAX_RSSI = 20
    private const val MIN_LATITUDE = -90.0
    private const val MAX_LATITUDE = 90.0
    private const val MIN_LONGITUDE = -180.0
    private const val MAX_LONGITUDE = 180.0
    private const val MIN_ACCURACY_METERS = 0f
    private const val MAX_ACCURACY_METERS = 100f
}
