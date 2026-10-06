package io.blueeye.core.domain.analysis

import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.AlertEvidenceEventType
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
        val signals = AnalysisSignalReduction.canonicalSignals(input.signalSamples)
        val followMeHistory = AnalysisContextReduction.canonicalFollowMe(input.followMeHistory)
        val alertEvents = AnalysisContextReduction.canonicalEvents(input.alertEvidenceEvents)
        val identityInput = AnalysisContextReduction.canonicalCandidates(input.identityCandidates)

        val signalSummary =
            AnalysisSignalReduction.signalSummary(
                sourceCount = input.signalSamples.size,
                signals = signals,
            )
        val locationSummary = AnalysisSignalReduction.locationSummary(signals.accepted)
        val movementSummary =
            AnalysisContextReduction.movementSummary(
                deviceEncounterCount = input.device.encounterCount,
                history = followMeHistory,
            )
        val identityCandidates =
            AnalysisContextReduction.identityCandidates(
                primaryFingerprint = input.device.fingerprint,
                candidates = identityInput,
                signals = signals.accepted,
            )
        val identitySummary =
            AnalysisIdentitySummary(
                sourceCandidateCount = input.identityCandidates.size,
                omittedCandidateCount =
                    (identityCandidates.size - AnalysisContextReduction.MAX_IDENTITY_CANDIDATES)
                        .coerceAtLeast(0),
                candidates = identityCandidates.take(AnalysisContextReduction.MAX_IDENTITY_CANDIDATES),
            )
        val representativeEvidence =
            AnalysisContextReduction.representativeEvidence(
                deviceEvidence = input.device.evidence,
                events = alertEvents,
            )
        val contradictions =
            AnalysisContextReduction.contradictions(
                input = input,
                followMeHistory = followMeHistory,
                identityCandidates = identityCandidates,
                rawIdentityCandidates = identityInput,
            )
        val duplicateCount =
            (input.signalSamples.size - signals.unique.size) +
                (input.followMeHistory.size - followMeHistory.size) +
                (input.alertEvidenceEvents.size - alertEvents.size) +
                (input.identityCandidates.size - identityInput.size)

        return DeterministicAnalysisCandidate(
            deviceFingerprint = input.device.fingerprint,
            localTrackingStatus = input.device.trackingStatus,
            localFollowingScore = input.device.followingScore,
            window =
                analysisWindow(
                    input = input,
                    acceptedSignals = signals.accepted,
                    followMeHistory = followMeHistory,
                    identityCandidates = identityInput,
                ),
            signal = signalSummary,
            locationQuality = locationSummary,
            movement = movementSummary,
            identity = identitySummary,
            timeBuckets = AnalysisSignalReduction.timeBuckets(signals.accepted),
            representativeEvidence = representativeEvidence.items,
            qualityFlags =
                qualityFlags(
                    QualityInputs(
                        duplicateCount = duplicateCount,
                        signalSummary = signalSummary,
                        locationSummary = locationSummary,
                        movementSummary = movementSummary,
                        identitySummary = identitySummary,
                        hasIdentityCoexistence =
                            identityCandidates.any {
                                it.disposition == AnalysisIdentityDisposition.COEXISTENCE_CONFLICT
                            },
                        representativeEvidenceCount = representativeEvidence.totalCount,
                    ),
                ),
            contradictions = contradictions,
        )
    }

    private fun qualityFlags(inputs: QualityInputs): List<AnalysisQualityFlag> {
        val flags = mutableSetOf<AnalysisQualityFlag>()
        if (inputs.duplicateCount > 0) flags += AnalysisQualityFlag.DUPLICATES_REDUCED
        if (inputs.signalSummary.rejectedSampleCount > 0) flags += AnalysisQualityFlag.INVALID_SIGNAL_SAMPLES
        if (inputs.signalSummary.acceptedSampleCount == 0) flags += AnalysisQualityFlag.NO_SIGNAL_SAMPLES
        if (inputs.locationSummary.usableLocationCount == 0) flags += AnalysisQualityFlag.NO_USABLE_LOCATION
        if (
            inputs.locationSummary.usableLocationCount > 0 &&
            inputs.locationSummary.usableLocationCount < inputs.signalSummary.acceptedSampleCount
        ) {
            flags += AnalysisQualityFlag.PARTIAL_LOCATION_COVERAGE
        }
        if (inputs.locationSummary.rejectedLocationCount > 0) {
            flags += AnalysisQualityFlag.INVALID_LOCATION_SAMPLES
        }
        if (inputs.movementSummary.sourceObservationCount == 0) {
            flags += AnalysisQualityFlag.NO_FOLLOW_ME_HISTORY
        }
        if (inputs.movementSummary.unknownMovementObservationCount > 0) {
            flags += AnalysisQualityFlag.MOVEMENT_UNKNOWN
        }
        if (inputs.hasIdentityCoexistence) {
            flags += AnalysisQualityFlag.IDENTITY_COEXISTENCE_DETECTED
        }
        if (inputs.identitySummary.omittedCandidateCount > 0) {
            flags += AnalysisQualityFlag.IDENTITY_CANDIDATES_TRUNCATED
        }
        if (inputs.representativeEvidenceCount > AnalysisContextReduction.MAX_REPRESENTATIVE_EVIDENCE) {
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
}

private object AnalysisSignalReduction {
    fun canonicalSignals(source: List<SignalSample>): CanonicalSignals {
        val unique = source.distinct().sortedWith(SIGNAL_ORDER)
        return CanonicalSignals(
            unique = unique,
            accepted = unique.filter(::isUsableSignal),
        )
    }

    fun signalSummary(
        sourceCount: Int,
        signals: CanonicalSignals,
    ): AnalysisSignalSummary {
        val rssi = signals.accepted.map(SignalSample::rssi).sorted()
        return AnalysisSignalSummary(
            sourceSampleCount = sourceCount,
            uniqueSampleCount = signals.unique.size,
            acceptedSampleCount = signals.accepted.size,
            duplicateSampleCount = sourceCount - signals.unique.size,
            rejectedSampleCount = signals.unique.size - signals.accepted.size,
            minRssi = rssi.firstOrNull(),
            maxRssi = rssi.lastOrNull(),
            averageRssi = rssi.averageIntOrNull(),
            medianRssi = rssi.medianOrNull(),
        )
    }

    fun locationSummary(signals: List<SignalSample>): AnalysisLocationQualitySummary {
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

    fun timeBuckets(signals: List<SignalSample>): List<AnalysisTimeBucket> =
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

    private fun isUsableSignal(sample: SignalSample): Boolean = sample.timestamp >= 0L && sample.rssi in MIN_RSSI..MAX_RSSI

    private fun hasAnyLocationData(sample: SignalSample): Boolean =
        sample.latitude != null || sample.longitude != null || sample.locationAccuracy != null

    private fun hasUsableLocation(sample: SignalSample): Boolean {
        val latitude = sample.latitude
        val longitude = sample.longitude
        val accuracy = sample.locationAccuracy
        return latitude != null &&
            longitude != null &&
            accuracy != null &&
            latitude.isFinite() &&
            longitude.isFinite() &&
            accuracy.isFinite() &&
            latitude in MIN_LATITUDE..MAX_LATITUDE &&
            longitude in MIN_LONGITUDE..MAX_LONGITUDE &&
            accuracy > MIN_ACCURACY_METERS &&
            accuracy <= MAX_ACCURACY_METERS
    }

    private fun List<Int>.averageIntOrNull(): Double? = if (isEmpty()) null else sumOf(Int::toLong).toDouble() / size

    private fun List<Double>.averageDoubleOrNull(): Double? = if (isEmpty()) null else sum() / size

    private fun List<Int>.medianOrNull(): Double? {
        if (isEmpty()) return null
        val middle = size / 2
        return if (size % 2 == 1) {
            this[middle].toDouble()
        } else {
            (this[middle - 1].toDouble() + this[middle].toDouble()) / 2.0
        }
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

    private const val TIME_BUCKET_MS = 60_000L
    private const val MIN_RSSI = -127
    private const val MAX_RSSI = 20
    private const val MIN_LATITUDE = -90.0
    private const val MAX_LATITUDE = 90.0
    private const val MIN_LONGITUDE = -180.0
    private const val MAX_LONGITUDE = 180.0
    private const val MIN_ACCURACY_METERS = 0f
    private const val MAX_ACCURACY_METERS = 100f
}

private object AnalysisContextReduction {
    const val MAX_IDENTITY_CANDIDATES = 8
    const val MAX_REPRESENTATIVE_EVIDENCE = 8

    fun canonicalFollowMe(source: List<FollowMeHistorySample>): List<FollowMeHistorySample> = source.distinct().sortedWith(FOLLOW_ME_ORDER)

    fun canonicalEvents(source: List<AlertEvidenceEvent>): List<AlertEvidenceEvent> = source.distinct().sortedWith(ALERT_EVENT_ORDER)

    fun canonicalCandidates(source: List<IdentityContinuityCandidate>): List<IdentityContinuityCandidate> =
        source.distinct().sortedWith(IDENTITY_INPUT_ORDER)

    fun movementSummary(
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
                if (!inMovingSegment) movingSegments += 1
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

    fun identityCandidates(
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

    fun representativeEvidence(
        deviceEvidence: List<DetectionEvidence>,
        events: List<AlertEvidenceEvent>,
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

    fun contradictions(
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
                        "device=${input.device.trackingStatus.name}, " +
                            "latestFollowMe=${latestFollowMe.trackingStatus.name}",
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

        val dangerousWithoutMovement =
            followMeHistory.any {
                it.trackingStatus == TrackingStatus.DANGEROUS &&
                    it.userMoved == false
            }
        if (dangerousWithoutMovement) {
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

    private fun DetectionEvidence.toAnalysisEvidence(alertEventType: AlertEvidenceEventType?): AnalysisRepresentativeEvidence =
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

    private fun confidencePriority(confidence: DetectionConfidence): Int = confidence.ordinal + 1

    private val FOLLOW_ME_ORDER =
        compareBy<FollowMeHistorySample>(
            FollowMeHistorySample::timestamp,
            FollowMeHistorySample::observedMac,
            { it.trackingStatus.name },
            FollowMeHistorySample::score,
            FollowMeHistorySample::rssi,
        )

    private val ALERT_EVENT_ORDER =
        compareBy<AlertEvidenceEvent>(
            AlertEvidenceEvent::timestamp,
            AlertEvidenceEvent::deviceFingerprint,
            AlertEvidenceEvent::observedMac,
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
        compareByDescending<AnalysisIdentityCandidate>(AnalysisIdentityCandidate::confidence)
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

    private const val FOLLOW_ME_SEGMENT_GAP_MS = 30_000L
    private const val IDENTITY_COEXISTENCE_WINDOW_MS = 2_000L
    private const val HIGH_IDENTITY_CONFIDENCE = 0.75f
}

private data class CanonicalSignals(
    val unique: List<SignalSample>,
    val accepted: List<SignalSample>,
)

private data class RepresentativeEvidenceSelection(
    val totalCount: Int,
    val items: List<AnalysisRepresentativeEvidence>,
)

private data class QualityInputs(
    val duplicateCount: Int,
    val signalSummary: AnalysisSignalSummary,
    val locationSummary: AnalysisLocationQualitySummary,
    val movementSummary: AnalysisMovementSummary,
    val identitySummary: AnalysisIdentitySummary,
    val hasIdentityCoexistence: Boolean,
    val representativeEvidenceCount: Int,
)
