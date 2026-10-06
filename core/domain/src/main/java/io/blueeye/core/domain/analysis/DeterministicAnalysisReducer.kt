package io.blueeye.core.domain.analysis

import io.blueeye.core.model.AlertEvidenceEvent
import io.blueeye.core.model.AlertEvidenceEventType
import io.blueeye.core.model.DetectionConfidence
import io.blueeye.core.model.DetectionEvidence
import io.blueeye.core.model.Device
import io.blueeye.core.model.FollowMeHistorySample
import io.blueeye.core.model.IdentityCarryoverVerdict
import io.blueeye.core.model.IdentityContinuityCandidate
import io.blueeye.core.model.SignalSample
import io.blueeye.core.model.TrackingStatus
import io.blueeye.core.model.analysis.AnalysisCandidate
import io.blueeye.core.model.analysis.AnalysisContradiction
import io.blueeye.core.model.analysis.AnalysisContradictionType
import io.blueeye.core.model.analysis.AnalysisEncounterSummary
import io.blueeye.core.model.analysis.AnalysisEvidenceSummary
import io.blueeye.core.model.analysis.AnalysisIdentityCandidateSummary
import io.blueeye.core.model.analysis.AnalysisLocationQualitySummary
import io.blueeye.core.model.analysis.AnalysisMovementSegment
import io.blueeye.core.model.analysis.AnalysisMovementState
import io.blueeye.core.model.analysis.AnalysisMovementSummary
import io.blueeye.core.model.analysis.AnalysisQualityFlag
import io.blueeye.core.model.analysis.AnalysisRssiSummary
import io.blueeye.core.model.analysis.AnalysisSignalSummary
import io.blueeye.core.model.analysis.AnalysisTimeBucket

object DeterministicAnalysisReducer {
    fun reduce(
        device: Device,
        signalSamples: List<SignalSample>,
        followMeHistory: List<FollowMeHistorySample>,
        alertEvidenceEvents: List<AlertEvidenceEvent>,
        identityCandidates: List<IdentityContinuityCandidate>,
    ): AnalysisCandidate {
        val signal = SignalReducer.reduce(device.fingerprint, signalSamples)
        val followMe = FollowMeReducer.reduce(followMeHistory)
        val identities = IdentityReducer.reduce(device.fingerprint, identityCandidates, signalSamples)

        return AnalysisCandidate(
            deviceFingerprint = device.fingerprint,
            trackingStatus = device.trackingStatus,
            followingScore = device.followingScore,
            signal = signal.summary,
            timeBuckets = signal.timeBuckets,
            locationQuality = signal.locationQuality,
            movement = followMe.movement,
            encounters = followMe.encounters,
            identityCandidates = identities.summaries,
            representativeEvidence =
                EvidenceReducer.reduce(
                    fingerprint = device.fingerprint,
                    deviceEvidence = device.evidence,
                    alertEvidenceEvents = alertEvidenceEvents,
                ),
            qualityFlags = qualityFlags(signal, followMe),
            contradictions = identities.contradictions,
        )
    }

    private fun qualityFlags(
        signal: SignalReduction,
        followMe: FollowMeReduction,
    ): List<AnalysisQualityFlag> =
        buildList {
            if (signal.summary.sourceSampleCount == 0) {
                add(AnalysisQualityFlag.NO_SIGNAL_SAMPLES)
            }
            if (signal.summary.duplicateSampleCount > 0) {
                add(AnalysisQualityFlag.DUPLICATE_SIGNAL_SAMPLES_REDUCED)
            }
            if (
                signal.locationQuality.totalSampleCount == 0 ||
                signal.locationQuality.missingSampleCount == signal.locationQuality.totalSampleCount
            ) {
                add(AnalysisQualityFlag.NO_LOCATION_DATA)
            }
            if (
                signal.locationQuality.totalSampleCount > 0 &&
                signal.locationQuality.usableSampleCount == 0
            ) {
                add(AnalysisQualityFlag.NO_USABLE_LOCATION)
            }
            if (signal.locationQuality.rejectedSampleCount > 0) {
                add(AnalysisQualityFlag.REJECTED_LOCATION_SAMPLES)
            }
            if (followMe.encounters.historySampleCount == 0) {
                add(AnalysisQualityFlag.NO_FOLLOW_ME_HISTORY)
            }
        }.sortedBy(AnalysisQualityFlag::name)
}

private data class SignalReduction(
    val summary: AnalysisSignalSummary,
    val timeBuckets: List<AnalysisTimeBucket>,
    val locationQuality: AnalysisLocationQualitySummary,
)

private object SignalReducer {
    fun reduce(
        fingerprint: String,
        allSamples: List<SignalSample>,
    ): SignalReduction {
        val sourceSamples =
            allSamples.filter { sample ->
                sample.deviceFingerprint.isBlank() || sample.deviceFingerprint == fingerprint
            }
        val reducedSamples =
            sourceSamples
                .distinct()
                .sortedWith(SIGNAL_ORDER)
        val locationQuality = locationQuality(reducedSamples)

        return SignalReduction(
            summary = signalSummary(sourceSamples.size, reducedSamples),
            timeBuckets = timeBuckets(reducedSamples),
            locationQuality = locationQuality,
        )
    }

    private fun signalSummary(
        sourceSampleCount: Int,
        samples: List<SignalSample>,
    ): AnalysisSignalSummary =
        AnalysisSignalSummary(
            sourceSampleCount = sourceSampleCount,
            reducedSampleCount = samples.size,
            duplicateSampleCount = sourceSampleCount - samples.size,
            firstObservedAt = samples.firstOrNull()?.timestamp,
            lastObservedAt = samples.lastOrNull()?.timestamp,
            rssi = rssiSummary(samples),
        )

    private fun rssiSummary(samples: List<SignalSample>): AnalysisRssiSummary? {
        if (samples.isEmpty()) return null
        val sortedRssi = samples.map(SignalSample::rssi).sorted()
        val total = sortedRssi.fold(0L) { acc, value -> acc + value }
        val middle = sortedRssi.size / 2
        val median =
            if (sortedRssi.size % 2 == 0) {
                (sortedRssi[middle - 1].toLong() + sortedRssi[middle].toLong()) / 2.0
            } else {
                sortedRssi[middle].toDouble()
            }

        return AnalysisRssiSummary(
            sampleCount = sortedRssi.size,
            minimum = sortedRssi.first(),
            maximum = sortedRssi.last(),
            average = total.toDouble() / sortedRssi.size,
            median = median,
        )
    }

    private fun timeBuckets(samples: List<SignalSample>): List<AnalysisTimeBucket> =
        samples
            .groupBy { sample -> bucketStart(sample.timestamp) }
            .toSortedMap()
            .map { (start, bucketSamples) ->
                val rssiTotal = bucketSamples.fold(0L) { acc, sample -> acc + sample.rssi }
                AnalysisTimeBucket(
                    startTimestamp = start,
                    endTimestampExclusive = start + TIME_BUCKET_MS,
                    sampleCount = bucketSamples.size,
                    minimumRssi = bucketSamples.minOf(SignalSample::rssi),
                    maximumRssi = bucketSamples.maxOf(SignalSample::rssi),
                    averageRssi = rssiTotal.toDouble() / bucketSamples.size,
                    usableLocationSampleCount =
                        bucketSamples.count { sample ->
                            locationState(sample) == LocationState.USABLE
                        },
                )
            }

    private fun locationQuality(samples: List<SignalSample>): AnalysisLocationQualitySummary {
        val usable =
            samples.filter { sample ->
                locationState(sample) == LocationState.USABLE
            }
        val rejected = samples.count { sample -> locationState(sample) == LocationState.REJECTED }
        val missing = samples.count { sample -> locationState(sample) == LocationState.MISSING }
        val accuracies = usable.mapNotNull(SignalSample::locationAccuracy)

        return AnalysisLocationQualitySummary(
            totalSampleCount = samples.size,
            usableSampleCount = usable.size,
            rejectedSampleCount = rejected,
            missingSampleCount = missing,
            bestAccuracyMeters = accuracies.minOrNull(),
            worstUsableAccuracyMeters = accuracies.maxOrNull(),
        )
    }

    private fun locationState(sample: SignalSample): LocationState {
        val latitude = sample.latitude
        val longitude = sample.longitude
        val accuracy = sample.locationAccuracy

        return when {
            latitude == null || longitude == null || accuracy == null -> LocationState.MISSING
            isUsableLatitude(latitude) &&
                isUsableLongitude(longitude) &&
                isUsableAccuracy(accuracy) -> LocationState.USABLE
            else -> LocationState.REJECTED
        }
    }

    private fun isUsableLatitude(latitude: Double): Boolean {
        return latitude.isFinite() && latitude in MIN_LATITUDE..MAX_LATITUDE
    }

    private fun isUsableLongitude(longitude: Double): Boolean {
        return longitude.isFinite() && longitude in MIN_LONGITUDE..MAX_LONGITUDE
    }

    private fun isUsableAccuracy(accuracy: Float): Boolean =
        accuracy.isFinite() &&
            accuracy > MIN_ACCURACY_METERS &&
            accuracy <= MAX_ACCURACY_METERS

    private fun bucketStart(timestamp: Long): Long = Math.floorDiv(timestamp, TIME_BUCKET_MS) * TIME_BUCKET_MS

    private val SIGNAL_ORDER =
        compareBy<SignalSample>(
            SignalSample::timestamp,
            SignalSample::deviceFingerprint,
            { it.observedMac.orEmpty() },
            SignalSample::rssi,
            { it.rawDataHex.orEmpty() },
        )

    private enum class LocationState {
        USABLE,
        REJECTED,
        MISSING,
    }

    private const val TIME_BUCKET_MS = 60_000L
    private const val MIN_LATITUDE = -90.0
    private const val MAX_LATITUDE = 90.0
    private const val MIN_LONGITUDE = -180.0
    private const val MAX_LONGITUDE = 180.0
    private const val MIN_ACCURACY_METERS = 0f
    private const val MAX_ACCURACY_METERS = 100f
}

private data class FollowMeReduction(
    val movement: AnalysisMovementSummary,
    val encounters: AnalysisEncounterSummary,
)

private object FollowMeReducer {
    fun reduce(history: List<FollowMeHistorySample>): FollowMeReduction {
        val canonical =
            history
                .distinct()
                .sortedWith(FOLLOW_ME_ORDER)

        return FollowMeReduction(
            movement = movementSummary(canonical),
            encounters = encounterSummary(canonical),
        )
    }

    private fun movementSummary(history: List<FollowMeHistorySample>): AnalysisMovementSummary =
        AnalysisMovementSummary(
            sampleCount = history.size,
            movingSampleCount = history.count { it.userMoved == true },
            stationarySampleCount = history.count { it.userMoved == false },
            unknownSampleCount = history.count { it.userMoved == null },
            segments = movementSegments(history),
        )

    private fun movementSegments(history: List<FollowMeHistorySample>): List<AnalysisMovementSegment> {
        if (history.isEmpty()) return emptyList()

        val result = mutableListOf<AnalysisMovementSegment>()
        var segmentStart = history.first().timestamp
        var segmentEnd = segmentStart
        var segmentState = movementState(history.first().userMoved)
        var segmentCount = 1

        history.drop(1).forEach { sample ->
            val state = movementState(sample.userMoved)
            val startsNewSegment =
                state != segmentState ||
                    sample.timestamp - segmentEnd > MAX_CONTIGUOUS_FOLLOW_ME_GAP_MS

            if (startsNewSegment) {
                result +=
                    AnalysisMovementSegment(
                        startTimestamp = segmentStart,
                        endTimestamp = segmentEnd,
                        sampleCount = segmentCount,
                        state = segmentState,
                    )
                segmentStart = sample.timestamp
                segmentCount = 0
                segmentState = state
            }

            segmentEnd = sample.timestamp
            segmentCount += 1
        }

        result +=
            AnalysisMovementSegment(
                startTimestamp = segmentStart,
                endTimestamp = segmentEnd,
                sampleCount = segmentCount,
                state = segmentState,
            )
        return result
    }

    private fun encounterSummary(history: List<FollowMeHistorySample>): AnalysisEncounterSummary =
        AnalysisEncounterSummary(
            historySampleCount = history.size,
            firstObservedAt = history.firstOrNull()?.timestamp,
            lastObservedAt = history.lastOrNull()?.timestamp,
            maxEncounterCount = history.maxOfOrNull(FollowMeHistorySample::encounterCount) ?: 0,
            peakScore =
                history
                    .map(FollowMeHistorySample::score)
                    .filter(Float::isFinite)
                    .maxOrNull(),
            peakTrackingStatus =
                history
                    .map(FollowMeHistorySample::trackingStatus)
                    .maxByOrNull(::trackingStatusPriority),
            distinctObservedMacCount =
                history
                    .map(FollowMeHistorySample::observedMac)
                    .filter(String::isNotBlank)
                    .distinct()
                    .size,
        )

    private fun movementState(value: Boolean?): AnalysisMovementState =
        when (value) {
            true -> AnalysisMovementState.MOVING
            false -> AnalysisMovementState.STATIONARY
            null -> AnalysisMovementState.UNKNOWN
        }

    private fun trackingStatusPriority(status: TrackingStatus): Int =
        when (status) {
            TrackingStatus.SAFE -> 0
            TrackingStatus.SUSPICIOUS -> 1
            TrackingStatus.DANGEROUS -> 2
        }

    private val FOLLOW_ME_ORDER =
        compareBy<FollowMeHistorySample>(
            FollowMeHistorySample::timestamp,
            FollowMeHistorySample::observedMac,
            { it.trackingStatus.name },
            FollowMeHistorySample::score,
            FollowMeHistorySample::rssi,
            FollowMeHistorySample::encounterCount,
            { it.userMoved?.toString().orEmpty() },
        )

    private const val MAX_CONTIGUOUS_FOLLOW_ME_GAP_MS = 30_000L
}

private data class IdentityReduction(
    val summaries: List<AnalysisIdentityCandidateSummary>,
    val contradictions: List<AnalysisContradiction>,
)

private object IdentityReducer {
    fun reduce(
        fingerprint: String,
        candidates: List<IdentityContinuityCandidate>,
        signalSamples: List<SignalSample>,
    ): IdentityReduction {
        val summaries =
            candidates
                .asSequence()
                .filter { candidate ->
                    candidate.deviceFingerprint == fingerprint ||
                        candidate.candidateFingerprint == fingerprint
                }
                .distinct()
                .groupBy { candidate -> otherFingerprint(fingerprint, candidate) }
                .toSortedMap()
                .map { (otherFingerprint, relations) ->
                    summarizeRelation(
                        fingerprint = fingerprint,
                        otherFingerprint = otherFingerprint,
                        relations = relations.sortedWith(IDENTITY_ORDER),
                        signalSamples = signalSamples,
                    )
                }

        return IdentityReduction(
            summaries = summaries,
            contradictions =
                summaries
                    .flatMap(::contradictionsFor)
                    .sortedWith(
                        compareBy<AnalysisContradiction>(
                            { it.type.name },
                            { it.relatedFingerprint.orEmpty() },
                            AnalysisContradiction::description,
                        ),
                    ),
        )
    }

    private fun summarizeRelation(
        fingerprint: String,
        otherFingerprint: String,
        relations: List<IdentityContinuityCandidate>,
        signalSamples: List<SignalSample>,
    ): AnalysisIdentityCandidateSummary {
        val latest = relations.last()
        val verdicts =
            relations
                .map(IdentityContinuityCandidate::verdict)
                .distinct()
                .sortedBy(IdentityCarryoverVerdict::name)
        val finiteConfidence =
            relations
                .map(IdentityContinuityCandidate::confidence)
                .filter(Float::isFinite)

        return AnalysisIdentityCandidateSummary(
            candidateFingerprint = otherFingerprint,
            observationCount = relations.size,
            firstObservedAt = relations.first().timestamp,
            lastObservedAt = latest.timestamp,
            maxConfidence = finiteConfidence.maxOrNull() ?: 0f,
            reasonCodes =
                relations
                    .map(IdentityContinuityCandidate::reasonCode)
                    .distinct()
                    .sorted(),
            verdicts = verdicts,
            latestVerdict = latest.verdict,
            hasCoexistenceEvidence =
                hasCoexistenceEvidence(
                    fingerprint = fingerprint,
                    otherFingerprint = otherFingerprint,
                    signalSamples = signalSamples,
                ),
        )
    }

    private fun contradictionsFor(summary: AnalysisIdentityCandidateSummary): List<AnalysisContradiction> =
        buildList {
            if (summary.hasCoexistenceEvidence) {
                add(
                    AnalysisContradiction(
                        type = AnalysisContradictionType.IDENTITY_COEXISTENCE,
                        relatedFingerprint = summary.candidateFingerprint,
                        description = "Both identity candidates were observed within the coexistence guard window.",
                    ),
                )
            }
            if (hasConflictingVerdicts(summary.verdicts)) {
                add(
                    AnalysisContradiction(
                        type = AnalysisContradictionType.IDENTITY_VERDICT_CONFLICT,
                        relatedFingerprint = summary.candidateFingerprint,
                        description = "Identity review contains both confirmed-same-device and false-match verdicts.",
                    ),
                )
            }
        }

    private fun hasConflictingVerdicts(verdicts: List<IdentityCarryoverVerdict>): Boolean =
        IdentityCarryoverVerdict.CONFIRMED_SAME_DEVICE in verdicts &&
            IdentityCarryoverVerdict.FALSE_MATCH in verdicts

    private fun hasCoexistenceEvidence(
        fingerprint: String,
        otherFingerprint: String,
        signalSamples: List<SignalSample>,
    ): Boolean {
        val primaryTimes =
            signalSamples
                .asSequence()
                .filter { it.deviceFingerprint == fingerprint }
                .map(SignalSample::timestamp)
                .distinct()
                .sorted()
                .toList()
        val candidateTimes =
            signalSamples
                .asSequence()
                .filter { it.deviceFingerprint == otherFingerprint }
                .map(SignalSample::timestamp)
                .distinct()
                .sorted()
                .toList()

        var primaryIndex = 0
        var candidateIndex = 0
        var hasCoexistence = false
        while (
            primaryIndex < primaryTimes.size &&
            candidateIndex < candidateTimes.size &&
            !hasCoexistence
        ) {
            val primary = primaryTimes[primaryIndex]
            val candidate = candidateTimes[candidateIndex]
            val gap = if (primary <= candidate) candidate - primary else primary - candidate
            hasCoexistence = gap < IDENTITY_COEXISTENCE_GUARD_MS

            if (!hasCoexistence) {
                if (primary <= candidate) {
                    primaryIndex += 1
                } else {
                    candidateIndex += 1
                }
            }
        }
        return hasCoexistence
    }

    private fun otherFingerprint(
        fingerprint: String,
        candidate: IdentityContinuityCandidate,
    ): String =
        if (candidate.deviceFingerprint == fingerprint) {
            candidate.candidateFingerprint
        } else {
            candidate.deviceFingerprint
        }

    private val IDENTITY_ORDER =
        compareBy<IdentityContinuityCandidate>(
            IdentityContinuityCandidate::timestamp,
            IdentityContinuityCandidate::id,
            IdentityContinuityCandidate::deviceFingerprint,
            IdentityContinuityCandidate::candidateFingerprint,
            IdentityContinuityCandidate::reasonCode,
            IdentityContinuityCandidate::confidence,
            { it.verdict.name },
        )

    private const val IDENTITY_COEXISTENCE_GUARD_MS = 2_000L
}

private object EvidenceReducer {
    fun reduce(
        fingerprint: String,
        deviceEvidence: List<DetectionEvidence>,
        alertEvidenceEvents: List<AlertEvidenceEvent>,
    ): List<AnalysisEvidenceSummary> {
        val latestEvidence =
            deviceEvidence.map { evidence ->
                evidence.toSummary(alertEventType = null)
            }
        val historicalAlertEvidence =
            alertEvidenceEvents
                .asSequence()
                .filter { event -> event.deviceFingerprint == fingerprint }
                .map { event -> event.evidence.toSummary(event.eventType) }
                .toList()

        return (latestEvidence + historicalAlertEvidence)
            .distinct()
            .sortedWith(EVIDENCE_ORDER)
            .take(MAX_REPRESENTATIVE_EVIDENCE)
    }

    private fun DetectionEvidence.toSummary(alertEventType: AlertEvidenceEventType?): AnalysisEvidenceSummary =
        AnalysisEvidenceSummary(
            source = source,
            confidence = confidence,
            reasonText = reasonText,
            timestamp = timestamp,
            isPassive = isPassive,
            provenance = provenance,
            alertEventType = alertEventType,
        )

    private fun confidencePriority(confidence: DetectionConfidence): Int =
        when (confidence) {
            DetectionConfidence.LOW -> LOW_CONFIDENCE_PRIORITY
            DetectionConfidence.MEDIUM -> MEDIUM_CONFIDENCE_PRIORITY
            DetectionConfidence.HIGH -> HIGH_CONFIDENCE_PRIORITY
            DetectionConfidence.CRITICAL -> CRITICAL_CONFIDENCE_PRIORITY
        }

    private val EVIDENCE_ORDER =
        compareByDescending<AnalysisEvidenceSummary> { summary ->
            confidencePriority(summary.confidence)
        }.thenByDescending(AnalysisEvidenceSummary::timestamp)
            .thenBy { it.source.name }
            .thenBy { it.provenance.name }
            .thenBy(AnalysisEvidenceSummary::reasonText)
            .thenBy { it.alertEventType?.name.orEmpty() }

    private const val LOW_CONFIDENCE_PRIORITY = 0
    private const val MEDIUM_CONFIDENCE_PRIORITY = 1
    private const val HIGH_CONFIDENCE_PRIORITY = 2
    private const val CRITICAL_CONFIDENCE_PRIORITY = 3
    private const val MAX_REPRESENTATIVE_EVIDENCE = 8
}
